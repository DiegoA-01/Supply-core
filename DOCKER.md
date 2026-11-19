# Supply-Core con Docker

Esta guía levanta todo el sistema (base de datos, backend y frontend) con un solo comando y explica las decisiones de seguridad.

## Requisitos
- Docker 24 o superior con Docker Compose v2 (`docker compose version`).
- Puerto libre para la aplicación (por defecto 8088; se cambia con `WEB_PORT`).

## Primer arranque

```bash
cp .env.example .env
# editar .env: claves de MySQL, JWT_SECRET y el correo y clave del dueño de la plataforma
docker compose up -d --build
docker compose ps
```

Cuando los tres servicios aparezcan como `healthy`, abrir `http://localhost:8088`.

1. Entrar a `/plataforma/ingreso` con el correo y la clave del dueño (`PLATAFORMA_ADMIN_*` del `.env`).
2. La primera vez la consola pide vincular una app autenticadora (Google Authenticator, Microsoft Authenticator): agregar la clave que muestra y escribir el código de 6 dígitos. La hora del equipo debe estar sincronizada.
3. Crear la primera empresa cliente desde la consola. Su administrador entra por `/ingreso` con el código de la empresa y también configura su segundo factor.

El dueño de la plataforma solo se crea si todavía no existe uno; cambiar `PLATAFORMA_ADMIN_PASSWORD` después no cambia la clave de una cuenta ya creada.

### Datos de demostración (empresa lista para recorrer el flujo)

Con el stack arriba y un `.env` válido, `scripts/sembrar_demo.py` crea por la API pública (Python 3, sin dependencias) una empresa de ejemplo:

```powershell
py scripts/sembrar_demo.py
```

- Empresa `Ferreteria Demo` (plan EMPRESARIAL, activa), con su administrador, sede, centro de costo, bodega, 8 productos con existencias y salidas (para el tablero de rotación) y políticas de aprobación.
- Usuarios por rol: jefe de compras, jefe de bodega, bodeguero, solicitante, aprobador, comprador, tesorería y auditor, más un proveedor ya habilitado.
- Flujo de compra de la demo: el bodeguero (también solicitante) pide lo que falta, la solicitud le llega al jefe de compras (la política de solicitudes la aprueba `ADMIN_COMPRAS`), y él, que también es comprador, arma y abre la RFQ que llega a los proveedores habilitados de la categoría. El jefe de compras también puede pedir; su solicitud la aprueba el dueño de la ferretería, porque nadie aprueba su propia solicitud. El dueño solo administra: no recibe avisos de RFQ, cotizaciones ni proveedores; pide y lee el **informe de compras** que le envía el jefe de compras con su análisis.
- Todas las claves son aleatorias y se escriben en `credenciales-docker.local.txt` (ignorado por git, nunca se sube). Ese archivo también trae la llave de la app autenticadora del dueño del software y del administrador de la empresa, que tienen segundo factor obligatorio.
- El script se niega a correr dos veces. Para empezar de cero: `docker compose down -v`, `docker compose up -d`, borrar `credenciales-docker.local.txt` y repetir. **`down -v` borra toda la base.**
- Es solo para ambientes de prueba: no se usa en producción.

## Cómo está armado

| Servicio | Imagen | Qué hace | Puerto |
|---|---|---|---|
| `web` | nginx sin root | Sirve Angular y reenvía `/api` al backend | `WEB_PORT` (8088) hacia el equipo |
| `api` | Java 21 (JRE) con Spring Boot, perfil `prod` | Lógica de negocio, API | solo red interna |
| `db` | MySQL 8.4 | Datos | solo red interna |

- **Redes.** `db` está en una red interna sin salida a internet: solo el backend le habla. Solo `web` publica un puerto. El backend sale a internet únicamente si usas Gemini o Claude.
- **Volúmenes.** `db-data` guarda la base y `storage` guarda los archivos subidos (cotizaciones, facturas, evidencias). Borrar los contenedores no borra los datos; `docker compose down -v` sí los borra.
- **Orden de arranque.** `api` espera a que MySQL responda y ejecuta las migraciones de Flyway; `web` espera a que `api` esté lista (`/actuator/health/readiness`).
- **Migraciones.** Flyway corre solo al arrancar el backend. Nunca se edita una migración ya ejecutada; se crea `V{n+1}`.

## Seguridad que ya trae
- Las contenedores del backend y del frontend corren **sin root**, con sistema de archivos de solo lectura, sin capacidades de Linux extra y con `no-new-privileges`. El backend solo puede escribir en `/tmp` y en el volumen de archivos.
- **Ningún secreto va en la imagen.** `application-local.yaml`, `.env` y los archivos subidos se excluyen del contexto de build con `.dockerignore`. Las claves llegan por variables de entorno.
- El perfil `prod` **no arranca** si falta `DB_URL`, `DB_USER`, `DB_PASSWORD` o `JWT_SECRET` (o si este último tiene menos de 32 caracteres).
- Swagger y la documentación de la API están apagados (`SWAGGER_ACTIVO=true` los enciende solo para depurar). nginx además responde 404 a `/swagger-ui`, `/v3/api-docs` y `/actuator`.
- Cabeceras de seguridad en nginx (CSP, `X-Frame-Options`, `nosniff`, `Permissions-Policy`) y las propias del backend en `/api`.
- **IP real del cliente.** nginx sobrescribe `X-Forwarded-For` con la IP de quien conecta; el backend solo la acepta si viene de una red privada de confianza. Así el límite de peticiones por IP y la auditoría registran al cliente y no al proxy, y nadie puede falsificarla.

## Operación

```bash
docker compose logs -f api            # registros del backend
docker compose ps                     # estado y salud
docker compose restart api            # reiniciar solo el backend
docker compose up -d --build api      # desplegar una versión nueva del backend
docker compose down                   # apagar (conserva los datos)
```

### Copia de seguridad y restauración de la base
```bash
# copia
docker compose exec -T db sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines supply_core' > respaldo-$(date +%F).sql
# restaurar (sobre una base vacía)
docker compose exec -T db sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" supply_core' < respaldo-2026-11-17.sql
```
Los archivos subidos están en el volumen `storage` (`docker volume inspect supply-core_storage`); respáldalos junto con la base.

### Lectura de cotizaciones con IA
En `.env`: `IA_PROVEEDOR=gemini` y `GEMINI_API_KEY=...`, y `docker compose up -d api`. Con un proveedor de IA los documentos salen del servidor hacia ese proveedor. Para Ollama en el mismo equipo, `OLLAMA_URL=http://host.docker.internal:11434`.

## HTTPS en producción
Este `nginx` habla HTTP. En un servidor real ponlo detrás de quien termine el HTTPS (Caddy, Traefik, un balanceador de la nube) y:
- Activa HSTS en ese proxy (`Strict-Transport-Security`).
- Pon `PUBLIC_URL=https://tu-dominio` en `.env`.
- Si ese proxy está delante de `web`, que le pase a nginx la IP real del cliente; de lo contrario el límite por IP verá la del proxy.

## Para endurecer más (pendiente)
- **Dos usuarios de MySQL.** Hoy la aplicación usa un solo usuario con permisos sobre `supply_core`. `backend/sql/permisos.sql` separa un usuario para migraciones y otro para la aplicación que solo puede leer e insertar en la auditoría. Hay que adaptar sus nombres de host (`'%'` en lugar de `'localhost'`) y volver a correrlo después de cada migración que cree tablas.
- **Escaneo de imágenes y CI.** Construir, probar y escanear (Trivy) las imágenes en cada Pull Request.
- **Un servicio de correo real.** El envío de correos todavía escribe en el registro (`LogEmailClient`).

## Problemas frecuentes
- `Falta JWT_SECRET en .env`: completar el `.env`. Compose se niega a arrancar sin las claves obligatorias.
- `port is already allocated`: otro programa usa el puerto; cambiar `WEB_PORT` en `.env`.
- El backend queda `unhealthy` la primera vez: la migración inicial tarda; `docker compose logs api` muestra el avance.
- El código MFA no funciona: sincronizar la hora del equipo y del celular. Cinco intentos fallidos bloquean la cuenta 15 minutos.

## Integración continua (GitHub Actions)

Cada PR hacia `develop` o `main` (y cada push a esas ramas) corre `.github/workflows/ci.yml`:

- `backend`: Java 21, MySQL 8.4 levantado en el job, `./mvnw verify` (incluidas las pruebas de fuga entre empresas), Javadoc con `doclint=all` y el reporte de cobertura de JaCoCo como artefacto (`cobertura-backend`).
- `frontend`: Node 24, `npm ci`, pruebas con Vitest y build de producción.
- `docker`: construye las imágenes `api` y `web` sin publicarlas y las escanea con Trivy; falla si hay vulnerabilidades CRÍTICAS con arreglo disponible.

Además, `dependency-review.yml` revisa las dependencias de cada PR y `codeql.yml` analiza el código cada lunes. El CI no usa secretos: las claves de MySQL y del JWT se generan dentro del job y se enmascaran en el registro.

**Protección de ramas (la activa el dueño del repositorio, una vez):** en GitHub, *Settings → Branches → Add branch ruleset* (o *Add rule*) para `develop` y otra para `main`:

1. Marcar *Require a pull request before merging* con al menos una aprobación.
2. Marcar *Require status checks to pass* y elegir `backend`, `frontend` y `docker` (aparecen después de la primera ejecución del CI).
3. Marcar *Require branches to be up to date before merging*.
4. Opcional: *Block force pushes* y *Restrict deletions*.

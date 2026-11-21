# Pistas que requieren validación — Supply-Core

Estas 5 pistas tienen la causa raíz identificada en el código, pero su explotabilidad real depende de hechos del **despliegue** que no se ven en el repositorio (configuración del operador, topología de red, límites de memoria del contenedor). No se marcan como confirmadas por eso. Cada una trae un plan de validación: local (reproducible en un sandbox) y de despliegue (lo que el dueño debe comprobar).

---

## V1 — Tokens de recuperación de clave en los logs del backend (perfil prod)

**Fingerprint:** `supply-core:auth/recovery-token-logged-by-log-email-client`

`LogEmailClient` es el **único** bean `EmailClient` y registra el cuerpo del correo a nivel INFO; el correo de recuperación contiene el token de un solo uso en texto plano. El perfil `prod` deja el log raíz en INFO, así que —salvo que se reemplace por un proveedor real— el token se escribe en los logs. Quien pueda leer los logs (pero no el buzón del usuario) podría llamar `/api/auth/restablecer` con el código de empresa y el token en 30 min y tomar la cuenta de cualquier usuario, sin MFA.

- **Código:** `RecuperacionPasswordService.solicitar:116` → `enviar:190` → `LogEmailClient.enviar:25` (`log.info` con el cuerpo). `application-prod.yaml:52` (root INFO).
- **Bloqueadores:** quién puede leer el stdout/almacén de logs en el despliegue real; si se usa un proveedor SMTP real que reemplace `LogEmailClient` (no existe en el código).
- **Validar local:** perfil prod, `POST /api/auth/recuperar` para un usuario ficticio, `docker logs` del api y confirmar que la línea de correo simulado trae el token; luego `restablecer` con ese token.
- **Validar despliegue:** confirmar quién tiene acceso de lectura a los logs del contenedor api (o su agregador) y si son distintos de quienes pueden resetear claves; y si hay SMTP real.

---

## V2 — Expansión no acotada de libros Excel (POI) tumba el JVM compartido

**Fingerprint:** `poi-workbookfactory-unbounded-expansion-shared-jvm`

Las cargas Excel (catálogo de proveedor por el portal, productos, precios) y la conversión IA de archivos de cotización llaman `WorkbookFactory.create` / `XWPFDocument` sobre archivos no confiables validados solo por tamaño **comprimido** (5 MB / 10 MB). Los topes de filas y caracteres se aplican **después** de materializar todo el paquete y el DOM XSSF/XWPF en memoria, sin límites `ZipSecureFile`/`IOUtils`. Con el contenedor a ~750 MB de heap y `-XX:+ExitOnOutOfMemoryError`, un `OutOfMemoryError` termina el único backend multiempresa para todas las empresas hasta reinicio.

- **Código:** `PortalCatalogoController.cargar:52`, `ProductoCargaService:345` (`WorkbookFactory.create`), `LectorArchivos.excelATexto:56`. `Dockerfile:28` (ExitOnOOM), `docker-compose.yml:78` (mem_limit 1g).
- **Bloqueadores:** límites efectivos de POI 5.4.1 y pico de heap para un libro 5 MB manipulado (requiere ejecutar POI en sandbox); si el heap/reinicio desplegado coinciden con el compose.
- **Validar local:** JVM con `-Xmx750m` sin red, `CatalogoProveedorService.leer` sobre un xlsx ~5 MB que expanda a cientos de MB (y uno con millones de celdas); medir pico de heap y si el proceso sale. Luego añadir `ZipSecureFile.setMaxEntrySize` / streaming `XSSFReader` y confirmar que lo rechaza.
- **Validar despliegue:** heap efectivo del contenedor y cuánto tarda el reinicio (caída para todos los tenants).

---

## V3 — Dimensión de página no acotada al renderizar PDF (PDFBox) agota el heap

**Fingerprint:** `pdfbox-render-unbounded-page-dimension`

Con el proveedor Ollama, un PDF de proveedor sin texto se renderiza con `PDFRenderer.renderImageWithDPI(i, 150)` hasta 3 páginas. Las dimensiones salen del `MediaBox` del atacante sin tope, así que un PDF diminuto puede pedir un `BufferedImage` ARGB de varios GB. El `OutOfMemoryError` no se captura (`ProcesadorExtraccion` solo captura `RuntimeException`) y el JVM sale por `-XX:+ExitOnOutOfMemoryError`, tumbando el backend compartido.

- **Código:** `CotizacionService.enviar:162` → `OllamaIAClient.extraer:94` → `LectorArchivos.pdfAImagenes:144` (solo topa el número de páginas, no el tamaño). `ProcesadorExtraccion:64` (solo RuntimeException). `Dockerfile:28`.
- **Bloqueadores:** comportamiento de PDFBox 3.0.5 ante un MediaBox enorme (recortar, lanzar o asignar) hay que observarlo ejecutando PDFBox; solo alcanzable con `app.ia.proveedor=ollama`.
- **Validar local:** `IA_PROVEEDOR=ollama`, JVM `-Xmx750m` sin red, `pdfAImagenes` sobre un PDF de una página sin texto con `MediaBox [0 0 14400 14400]`; registrar OOM vs RuntimeException. Arreglo: calcular ancho/alto a 150 DPI y rechazar páginas sobre un presupuesto de píxeles (p.ej. 25 Mpx) o bajar el DPI.
- **Validar despliegue:** si algún despliegue corre con `IA_PROVEEDOR=ollama` y el heap del contenedor.

---

## V4 — Detrás del proxy TLS documentado, todos comparten una sola cubeta de rate-limit

**Fingerprint:** `frontend/nginx/default.conf:x-forwarded-for-remote_addr:ratelimit-shared-proxy-bucket`

nginx sobrescribe `X-Forwarded-For` con `$remote_addr` y no tiene `real_ip`. El API (`forward-headers-strategy: native`) usa ese valor como `getRemoteAddr()`, y `RateLimitFilter` llavea su ventana de 20 POST/min en él. `DOCKER.md` indica poner un proxy que termina TLS delante del contenedor web; entonces `$remote_addr` es siempre la dirección del proxy frontal, así que **todos** los usuarios de todas las empresas comparten una cubeta. Un cliente sobre 20 POST/min a `/api/auth/login` provoca 429 para todos los tenants en login, refresh (cada 15 min), MFA, recuperación, registro de proveedor, login de plataforma y la ruta del PIN. La IP de auditoría también colapsa a la del proxy.

- **Código:** `default.conf:43` (`proxy_set_header X-Forwarded-For $remote_addr`, sin `real_ip`), `RateLimitFilter.java:86` (llave = `getRemoteAddr()`), `application-prod.yaml:12` (native). `DOCKER.md:85`.
- **Bloqueadores:** si el despliegue real pone un proxy/balanceador delante del contenedor web.
- **Validar local:** en red Docker aislada, poner un reverse proxy mínimo delante de `web`, perfil prod con secretos ficticios; 21 POST a `/api/auth/login` desde el cliente A en un minuto, luego uno desde el cliente B con otra IP; si B recibe 429, la cubeta es compartida. Con `real_ip` configurado, B debería dar 401.
- **Validar despliegue:** si el peer de nginx en producción es un proxy frontal; comparar la columna `ip` de filas recientes de `auditoria` entre usuarios (una sola dirección repetida confirma la llave colapsada).

---

## V5 — El perfil prod acepta los secretos de ejemplo de `.env.example`

**Fingerprint:** `env.example:placeholder-secrets-accepted-by-prod-profile`

El perfil prod falla rápido si faltan `JWT_SECRET` o `PLATAFORMA_ADMIN_PASSWORD`, pero **no** si valen los valores de ejemplo commiteados en `.env.example`: `JwtUtil` solo chequea longitud ≥ 32 y `PlataformaInicializador` solo que la clave tenga 12..72 caracteres. Un `cp .env.example .env` seguido de `docker compose up` sin editar correría con una clave HS256 pública (cualquiera forja un JWT de empresa para cualquier `emp`/usuario/roles) y una clave de dueño de plataforma conocida.

- **Código:** `.env.example:12` (JWT_SECRET), `:17` (PLATAFORMA_ADMIN_PASSWORD); `application-prod.yaml:40`; `JwtUtil.resolverSecreto:343` (solo longitud); `PlataformaInicializador:63` (12..72); `JwtUtil:83` (clave derivada del secreto).
- **Bloqueadores:** si algún `.env` desplegado conserva los valores de ejemplo (hecho del operador, no visible en código); ejecución no permitida en esta corrida.
- **Validar local:** `cp .env.example .env` sin editar + `docker compose up`; confirmar que arranca, que un token forjado con la clave conocida pasa `JwtUtil.validar`, y que el dueño se crea con la clave de ejemplo.
- **Validar despliegue:** comparar `JWT_SECRET` y `PLATAFORMA_ADMIN_PASSWORD` desplegados contra `.env.example` (p.ej. `grep -c` del marcador de ejemplo) sin imprimir los valores. **Si coinciden, rotarlos de inmediato.**

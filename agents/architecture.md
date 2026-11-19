# Arquitectura

## Piezas y puertos
- `backend/`: API REST en Spring Boot, puerto 8080, rutas bajo `/api`. Un paquete por módulo de negocio
  (`compra/`, `inventario/`, `venta/`…), cada uno con `controller/`, `service/`, `repository/`, `entity/`, `dto/`.
- `frontend/`: una sola app Angular con tres zonas: `/app` (empresa), `/portal` (proveedor) y `/plataforma` (dueño del
  software). En desarrollo corre en 4200 con proxy a la API (`proxy.conf.json`).
- `docker-compose.yml`: solo nginx publica puerto (`WEB_PORT`, 8088) y reenvía `/api` al backend; MySQL y el backend
  viven en redes internas. Detalle en `DOCKER.md`.
- Base de datos: MySQL. Fuente de verdad del esquema: `backend/src/main/resources/db/migration/V1__esquema_inicial.sql`
  y las migraciones siguientes. Nunca se edita un `V*` ya ejecutado; se crea el siguiente (ver la reserva en
  `agents/specs/README.md`).

## Cómo habla el frontend con el backend
- El usuario entra con `{empresa, correo, password}` (y MFA si aplica). El backend devuelve un JWT de 15 minutos y un
  refresh token de un solo uso. El JWT vive en memoria y el refresh en `sessionStorage` (solo `SesionService`).
- Los tipos del frontend se generan del Swagger. La UI nunca calcula dinero ni stock: muestra lo que devuelve la API.

## Reglas técnicas que aplican a toda spec
1. **Aislamiento por empresa:** toda tabla de negocio lleva `empresa_id` con `@TenantId`; la empresa sale del JWT (claim
   `emp`), nunca del request. SQL nativo o JDBC lleva `empresa_id` a mano. Cada tabla y endpoint nuevo suma su prueba
   en `AislamientoEmpresasTest` (A pide un dato de B ⇒ 404).
2. **Doble permiso:** `@RequiereModulo` (módulo contratado; apagado = 403 `MODULO_NO_CONTRATADO`) y `@RequiereRol` en
   cada endpoint. Las reglas de "nadie autoriza lo suyo" van en el Service.
3. **Dinero:** lo calcula Java con `BigDecimal` (2 decimales, redondeo `HALF_UP`). Montos `DECIMAL(18,2)`, cantidades
   `DECIMAL(18,4)`. La IA nunca calcula dinero ni elige.
4. **Stock:** solo cambia por `movimiento_inventario` a través de `MovimientoInventarioService`.
5. **Nada se borra:** los registros se anulan o se desactivan.
6. **Auditoría:** acciones sensibles en `auditoria` (`AccionAuditoria`, `EntidadAuditada`).
7. **Avisos:** evento en `EventoNotificacion` y `notificacionService.notificarRol(...)`; las filas de
   `config_notificacion` ausentes cuentan como activas.
8. **Javadoc** en todo método, sin advertencias de doclint.

## Seguridad de ventas y caja
- **Aislamiento:** `@TenantId` en todas las tablas nuevas; pruebas de fuga entre empresas por cada endpoint.
- **Permisos:** `@RequiereModulo` + `@RequiereRol` en cada endpoint; "nadie autoriza lo suyo" en el Service.
- **PIN de supervisor:** BCrypt, 6 dígitos, bloqueo tras 5 fallos (15 min), auditado, nunca viaja en la URL.
- **Autorización de un solo uso:** el supervisor escribe su PIN y el backend devuelve un token corto (2 minutos, una
  sola acción, ligado al turno y a la venta). La acción del cajero lo consume. Así el PIN no viaja en cada petición y no
  se puede reutilizar.
- **Datos de tarjeta:** solo últimos 4 dígitos y número de autorización (fuera del alcance de PCI DSS). **Nunca** se
  guarda el número completo de una tarjeta ni el CVV.
- **Datos personales de clientes:** mínimo necesario, fecha de la autorización de tratamiento de datos (Ley 1581 de
  2012, habeas data), exportación solo para roles con permiso. El consumidor final no pide datos.
- **Rate limit** en las rutas de autorización con PIN (además del que ya existe en el login).
- **Auditoría** de: venta, anulación, devolución, descuento autorizado, cambio de precio, apertura y cierre de turno,
  aprobación de diferencia, retiro, gasto, reimpresión, cambio de cupo de crédito.
- **Concurrencia:** `@Version` en `turno_caja` y `venta`; bloqueo pesimista del stock al vender.
- **Validación:** cantidades > 0, máximo de líneas por venta (ej. 300), máximo de descuento 100 %, montos con 2 decimales.

## Controles contra pérdidas de dinero

| Riesgo | Control | Spec |
|---|---|---|
| Doble cobro por doble clic o red lenta | Clave de idempotencia por venta (única en la base). | 004 |
| Dos cajeros venden el último producto | Bloqueo de la fila de stock y validación dentro de la transacción; nunca stock negativo (`CHECK cantidad >= 0` ya existe). | 004 |
| La pantalla manda un precio falso | El backend ignora los montos del cliente y recalcula con el precio vigente. | 004 |
| Vender por debajo del costo | `precio_minimo` y alerta si el precio queda bajo el costo promedio. | 001, 008 |
| Descuentos "a los amigos" | Límite por rol, autorización con PIN, reporte de descuentos por cajero. | 004, 009 |
| Anular para quedarse el efectivo | Solo en el turno abierto, con autorización de otra persona; reporte de anulaciones por cajero y alerta si pasan un umbral. | 006, 010 |
| Faltantes de caja | Cierre ciego, diferencias guardadas por turno y por cajero, aprobación obligatoria si pasan la tolerancia. | 003 |
| Mucho efectivo en el cajón | Tope de efectivo por caja y aviso de retiro. | 003 |
| Ventas "por fuera del sistema" | Cuadre diario automático: ventas ↔ movimientos de inventario ↔ movimientos de caja. Cualquier diferencia genera alerta. | 009 |
| Reimprimir un tiquete para cobrarlo otra vez | La reimpresión dice "COPIA" y queda auditada. | 005 |
| Borrar o cambiar el historial | Ventas, turnos y movimientos sin borrado ni edición; correcciones solo por anulación o devolución. | 004, 006 |
| Cambios de precio sin control | Historial de precios con quién, cuándo y motivo; solo roles autorizados. | 001 |
| Cajero que ve el esperado y "cuadra" | El esperado no se muestra hasta guardar lo contado. | 003 |

## Estrategia de pruebas

| Nivel | Qué cubre |
|---|---|
| **Unitarias** (JUnit + Mockito) | Cálculo de totales, impuestos (precio con y sin IVA incluido), descuentos, redondeo, cambio, esperado de caja, reglas de autorización |
| **Integración con MySQL real** (`supply_core_test`) | Venta completa en una transacción; idempotencia; rechazo por stock; anulación y devolución reintegran stock y caja |
| **Fuga entre empresas** (`AislamientoEmpresasTest`) | Una prueba por cada tabla y endpoint nuevo |
| **Concurrencia** | Dos ventas simultáneas del último producto: una pasa y la otra recibe `STOCK_INSUFICIENTE`; el stock nunca queda negativo |
| **Propiedades de dinero** | Para muchas ventas aleatorias: total = Σ líneas; Σ pagos − cambio = total; caja esperada = Σ movimientos |
| **Frontend** (Vitest) | Una prueba por pantalla: POS, cobro, tiquete, cierre ciego (no muestra el esperado antes de guardar), autorización |
| **De punta a punta** (Playwright, spec 010) | Abrir turno → vender → anular con PIN → cerrar turno, contra el stack de Docker |

**Meta de cobertura** en los Services de ventas y caja: 90 % de líneas (medida con JaCoCo en el CI).

## Qué deja cada spec al terminar (definición de hecho)
Código, pruebas (incluida la de fuga), Javadoc completo, tipos del frontend regenerados, `DOCKER.md` o la skill
`supply-core` actualizados si cambia algo de operación, la siembra de la demo (`scripts/sembrar_demo.py`) ampliada para
poder probarla, `PROGRESS.md` al día y el CI en verde.

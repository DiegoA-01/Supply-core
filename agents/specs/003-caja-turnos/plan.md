# Plan 003

## Datos (V33, reservada en `agents/specs/README.md`)
**`caja`**: `id, empresa_id, sede_id, bodega_id, nombre, prefijo_consecutivo, tope_efectivo, activa`.
- La bodega debe ser de la sede (D7: una caja, una bodega).

**`turno_caja`**: `id, empresa_id, caja_id, cajero_usuario_id, estado (ABIERTO, CERRADO, APROBADO), abierto_en,
base_apertura, cerrado_en, contado_efectivo, contado_otros (JSON por medio), esperado_efectivo, diferencia,
observacion_cierre, aprobado_por_usuario_id, aprobado_en, version`.
- **Un solo turno abierto por caja y uno por cajero** (índice único parcial vía columna generada: `abierto_caja` y
  `abierto_cajero` valen el id solo si `estado = 'ABIERTO'`).
- `esperado_*` y `diferencia` los calcula Java al cerrar; se guardan para que el histórico no cambie.
- `@Version` para cierres y aprobaciones concurrentes.

**`movimiento_caja`**: libro de la caja. **Cada peso que entra o sale queda aquí.**
`id, empresa_id, turno_id, tipo (BASE, VENTA, DEVOLUCION, ANULACION, RETIRO, GASTO, INGRESO, ABONO_CARTERA),
medio_pago, monto (positivo entra, negativo sale), referencia_tipo, referencia_id, motivo, usuario_id,
autorizado_por_usuario_id, creado_en`. Sin UPDATE ni DELETE.

**PIN y autorizaciones**
- `usuario.pin_supervisor_hash` (BCrypt), `pin_intentos_fallidos`, `pin_bloqueado_hasta`.
- `autorizacion_supervisor`: `id, empresa_id, supervisor_usuario_id, solicitante_usuario_id, accion (ANULAR_VENTA,
  DESCUENTO, PRECIO_BAJO_MINIMO, DEVOLUCION, RETIRO, GASTO), turno_id, referencia_id, token_hash, vence_en, usada_en`.
  Solo se guarda el hash del token.

**Configuración por empresa:** `empresa.tolerancia_diferencia_caja` (por defecto 2000, D6). Se lee y cambia en
`GET/PUT /api/cajas/configuracion` (no en el formulario de la empresa), para que todo lo de caja quede en su módulo.

**Ajustes al construir (2026-11-19):**
- El usuario no tiene sede asignada: el cajero ve todas las cajas activas (no solo las de su sede).
- La autorización la pide la sesión del cajero; el supervisor se identifica con su correo y su PIN en esa pantalla
  (`POST /api/autorizaciones` lo llaman CAJERO, SUPERVISOR_VENTAS y ADMIN_SISTEMA).
- Turno: dentro de la tolerancia queda `CERRADO`; si alguna diferencia por medio de pago la pasa, queda `CERRADO` con
  `requiere_aprobacion` y pasa a `APROBADO` cuando un supervisor (que no sea el cajero) la explica.
- Con el turno abierto, el cajero no ve cuánto efectivo debería haber: solo la marca `debeRetirar` cuando pasa el tope.
- El gasto lleva un número de soporte en texto; la foto del recibo se agrega en la spec 005 con "Mi turno".

## Flujos
**Abrir turno**
1. El cajero elige su caja (solo las activas de su sede).
2. Cuenta la base en efectivo y la escribe.
3. Java valida: no tiene otro turno abierto y la caja tampoco.
4. Se crea el turno y un `movimiento_caja` tipo `BASE`. Se audita `TURNO_ABRIR`.

**Retiros y gastos menores**
- **Retiro a bóveda:** cuando el efectivo pasa el `tope_efectivo` de la caja, la respuesta del turno trae la marca de
  aviso y la pantalla avisa que hay que retirar.
- **Gasto menor:** con motivo y número de soporte (la foto del recibo llega en la spec 005).
- Los dos con autorización de supervisor. Quedan en `movimiento_caja` en negativo.

**Cerrar turno (cierre ciego)**
1. El cajero cuenta el efectivo **sin ver cuánto debería tener** y escribe lo contado. Escribe también los totales de
   los vouchers de tarjeta y las transferencias.
2. Java calcula el esperado: `base + ventas en efectivo + abonos − devoluciones en efectivo − retiros − gastos`.
3. Guarda la diferencia por medio de pago. Turno `CERRADO`.
4. Si la diferencia pasa la tolerancia, queda pendiente de aprobación de un supervisor (nunca el mismo cajero), que
   escribe la explicación. Turno `APROBADO`.
5. El reporte del cierre (tiquete Z) se genera, se imprime y queda guardado. Audita `TURNO_CERRAR` y `TURNO_APROBAR`.

**Autorización con PIN**
1. El supervisor escribe correo y PIN en la pantalla del cajero; va en el cuerpo de un POST, nunca en la URL.
2. El backend valida (bloqueo tras 5 fallos, 15 min; rate limit propio), comprueba que supervisor y solicitante sean
   distintos y devuelve un token de un solo uso (2 minutos, una acción, ligado al turno y a la referencia).
3. La acción del cajero lo envía y el Service lo consume dentro de su transacción.

## API (`@RequiereModulo(CAJA)`)

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET/POST/PUT /api/cajas` | ADMIN_SISTEMA | Cajas |
| `POST /api/turnos` | CAJERO, SUPERVISOR_VENTAS | Abrir turno |
| `GET /api/turnos/actual` | CAJERO, SUPERVISOR_VENTAS | Turno abierto del usuario (sin esperado mientras está abierto) |
| `POST /api/turnos/{id}/movimientos` | CAJERO + autorización | Retiro, gasto o ingreso |
| `POST /api/turnos/{id}/cerrar` | CAJERO | Cierre ciego; responde el esperado y la diferencia solo después de guardar |
| `POST /api/turnos/{id}/aprobar` | SUPERVISOR_VENTAS, ADMIN_SISTEMA | Aprobar la diferencia |
| `GET /api/turnos?estado=` | SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Turnos abiertos, cierres y pendientes |
| `GET /api/turnos/{id}/cierre` | los mismos y el cajero dueño del turno | Tiquete Z |
| `POST /api/autorizaciones` | CAJERO, SUPERVISOR_VENTAS, ADMIN_SISTEMA (sesión del cajero) | Valida correo y PIN del supervisor y devuelve el permiso de un solo uso |
| `PUT /api/autorizaciones/mi-pin` | SUPERVISOR_VENTAS, ADMIN_SISTEMA | Fijar o cambiar su PIN (pide la contraseña) |
| `GET/PUT /api/cajas/configuracion` | ADMIN_SISTEMA (leer también SUPERVISOR_VENTAS) | Tolerancia de diferencia de caja |

## Frontend
- **Cajas y turnos** (`/app/caja/turnos`, supervisor y dueño): turnos abiertos, cierres, diferencias por aprobar.
- Configuración de cajas en `/app/configuracion`.
- Diálogo reutilizable de autorización con PIN (lo usan 004, 005 y 006).

## Pruebas
- Unitarias: cálculo del esperado y la diferencia por medio; tolerancia; "nadie aprueba lo suyo"; validación y bloqueo
  del PIN; token vencido, usado o de otra acción ⇒ rechazo.
- Integración: dos aperturas simultáneas de la misma caja ⇒ una sola pasa; caja esperada = Σ movimientos.
- Fuga: cajas, turnos, movimientos y autorizaciones de A invisibles para B.
- Frontend: el cierre ciego no muestra el esperado antes de guardar; el diálogo de PIN no guarda el PIN.

# Plan de trabajo: terminar el frontend

Fecha: 2026-11-17. Base: `develop` en `19e0d74`, Angular 22, tipos generados del Swagger.

## Cómo se armó este plan
Se cruzaron las 176 rutas de la API (`frontend/src/app/core/api/esquema.ts`) contra las rutas que el frontend realmente llama. Lo que aparece abajo como "sin uso" no tiene ninguna llamada en el frontend; lo marcado "verificar" aparece con la ruta base usada pero no con todos sus verbos o subrutas (puede estar hecho con otro verbo o faltar). Cada fase termina con su prueba y con la actualización de `references/frontend.md`.

## Reglas para todas las fases
- La interfaz nunca calcula dinero ni stock; solo muestra lo que devuelve la API.
- Cada pantalla respeta el doble permiso: módulo activo (`/api/empresa/modulos`) y rol del usuario. Si no tiene permiso, no se muestra el menú ni la ruta.
- Cada pantalla trae: estado de carga, vacío, error, validación del formulario, accesibilidad por teclado y vista de teléfono.
- Después de cualquier cambio de DTO en el backend: `npm run api:tipos`.
- Cada pantalla suma pruebas de componente (hoy hay 15) y, si toca datos de otra empresa, la prueba de fuga ya existe en el backend.

## Fase 0. Preparación (media jornada) — hecha en parte el 2026-11-17: tipos regenerados; falta devolver el proxy a 8080 y las piezas compartidas, que se crean cuando la segunda pantalla las pida
1. Devolver `frontend/proxy.conf.json` a `localhost:8080` cuando se libere el puerto (hoy está en 8081 por conflicto con WSL).
2. Regenerar `esquema.ts` y confirmar que compila y que pasan las 15 pruebas.
3. Crear piezas compartidas para no repetir: tabla con paginación y filtros, formulario de catálogo con activar/desactivar, diálogo de confirmación con motivo, selector de archivo con vista previa, botón de exportar (descarga y envío por correo).
4. Revisar el menú lateral: una entrada por pantalla, oculta según módulo y rol.

## Fase 1. Lectura de cotizaciones con IA y exportaciones — hecha el 2026-11-17 (el kardex pasa a la fase 3, donde se elige el producto)
Rutas sin uso: `/api/cotizaciones/{id}/extraccion`, `.../extraccion/campos/{campo}`, `.../extraccion/lineas`, `.../extraccion/reintentar`.
1. Pantalla de revisión de la extracción: campos leídos, líneas, correcciones manuales, botón reintentar.
2. Mostrar la alerta `TOTAL_NO_CUADRA` y el total impreso frente al calculado; la cotización en `REQUIERE_REVISION` queda marcada.
3. Mostrar el estado de la lectura (pendiente, leyendo, lista, falló) y qué proveedor de IA la hizo.
4. Exportaciones sin uso: kardex (descarga y correo), solicitudes (descarga y correo), stock por correo, orden por correo. Botón en cada pantalla de origen.
5. Factura: botón "recruzar" (`/api/facturas/{id}/recruzar`) y ver el resultado del cruce de tres vías.

## Fase 2. Cuenta y seguridad (SaaS 3 en la interfaz) — hecha el 2026-11-18, salvo el punto 7 (editar el código de ingreso): el backend no tiene esa ruta, hay que crearla primero (PATCH de plataforma con validación, duplicado, auditoría y aviso de que los usuarios deberán usar el código nuevo)
Rutas sin uso: `/api/auth/recuperar`, `/restablecer`, `/mfa`, `/mfa/configurar`, `/mfa/activar`, `/me`, `PATCH /api/usuarios/{id}/mfa/reiniciar`.
1. "Olvidé mi contraseña": pedir el correo, mensaje igual exista o no la cuenta, pantalla de restablecer con el token de la dirección.
2. Segundo paso del ingreso de empresa cuando el usuario tiene MFA (desafío y código).
3. En el perfil: configurar MFA (clave y código QR), activar con un código, estado actual.
4. En usuarios: botón "reiniciar MFA" (solo ADMIN_SISTEMA) y activar o desactivar usuario con confirmación.
5. Primer ingreso del ADMIN_SISTEMA obligado a configurar MFA cuando la empresa lo exige.
6. Usar `/api/auth/me` al recargar la página para recuperar la sesión.
7. Consola del dueño: editar el código de ingreso de una empresa (hoy solo se fija al crear). Requiere confirmar con el backend la ruta de edición.
8. Configuración de notificaciones por usuario (`/api/config-notificaciones`).

## Fase 3. Inventario — hecha el 2026-11-18 (reorden y carga masiva ya estaban completas; el kardex se exporta aquí)
Rutas sin uso: `/api/inventario/stock`, `/api/inventario/movimientos`.
1. Stock por bodega con filtros y alertas de stock bajo.
2. Movimientos de inventario con filtros y detalle.
3. Ajustes y traslados entre bodegas (el stock solo cambia por `movimiento_inventario`).
4. Kardex por producto y exportación (se une con la fase 1).
5. Reorden sugerida y su borrador (ya existe la llamada; verificar la pantalla completa).
6. Carga masiva por Excel de productos (ya existe la llamada; verificar plantilla, errores por fila y confirmación).

## Rotación de productos (pedido el 2026-11-18, hecho): reporte nuevo en el backend y tablero en el frontend; sin filtro por bodega ni Excel todavía

## Fase 4. Caja y pagos — hecha el 2026-11-18 (los despachos son del almacenista, no del cajero; no hay exportación del estado de cuenta en el backend)
Rutas sin uso: `/api/despachos` (lista, detalle, confirmar, anular), `/api/pagos` (lista, detalle, anular, comprobante).
1. Despachos de caja para CAJERO: lista, crear, confirmar, anular con motivo.
2. Pagos: lista, registrar (ya existe dentro de la orden), anular con motivo, descargar el comprobante.
3. Estado de cuenta del proveedor (existe; verificar la exportación).

## Fase 5. Decisiones y órdenes — hecha el 2026-11-18 (con un endpoint nuevo en el backend: candidatos de delegación)
Rutas sin uso: `/api/adjudicaciones/pendientes`, `/adjudicaciones/{id}`, `/adjudicaciones/{id}/decision`, `/api/ordenes/directa`, `/api/politicas-aprobacion/aplicable`. Verificar: `/api/ordenes/{id}/cancelar`, `/version`, `/recepciones/cerrar`, `/api/rfqs/{id}/cancelar`.
1. Bandeja de adjudicaciones pendientes con decisión (la justificación es obligatoria si se aparta de la recomendación de la IA).
2. Orden directa (sin RFQ) con la política aplicable mostrada antes de enviar.
3. Cancelar orden y nueva versión de una orden emitida (nunca se edita; se crea otra versión con motivo).
4. Cerrar recepciones de una orden y resolver excesos.
5. Cancelar RFQ.
6. Delegaciones: otorgadas, recibidas, crear y revocar.

## Fase 6. Catálogos de configuración — hecha el 2026-11-18 (catálogos simples con una pantalla genérica; el índice está en /app/configuracion) (la más grande)
Cada catálogo lleva lista, crear, editar, activar y desactivar. Hoy se leen en selectores, pero no se administran.
1. Sedes (`/api/sedes`: sin uso).
2. Centros de costo, bodegas y categorías (verificar edición, activar, desactivar).
3. Unidades de medida y conversión (`/api/unidades-medida/convertir`).
4. Productos: crear, editar, activar, desactivar.
5. Políticas de aprobación (umbrales, SLA).
6. Pesos de evaluación (`/api/config-pesos`): mostrar que cada análisis guarda el snapshot usado.
7. Presupuestos por centro de costo.
8. Contratos y precios pactados, ofertas, vigencia y anular (`/api/contratos`: sin uso).
9. Proveedores: editar, suspender, rechazar, habilitar, ver evaluaciones (`/api/proveedores/{id}/evaluaciones`).

## Fase 7. Portal del proveedor — hecha el 2026-11-18 (con un endpoint nuevo en el backend: categorías del proveedor)
Sin uso: `/api/portal/proveedor/evaluaciones`. Verificar: registro público y sus categorías, aceptar, rechazar, preparar, despachar y recepciones de una orden del portal.
1. Registro público del proveedor (`/api/portal/registro/{codigo}` y categorías), con el código de empresa decodificado de la dirección.
2. Perfil del proveedor: datos, categorías, documentos.
3. Evaluaciones recibidas.
4. Órdenes del portal: aceptar, rechazar con motivo, preparar, despachar, ver recepciones.
5. Facturas y pagos del proveedor sobre sus órdenes (verificar).

## Fase 8. Auditoría de la empresa — hecha el 2026-11-18 (rama `feature/fase-8-auditoria`)
Rutas sin uso: `/api/auditoria`, `/api/auditoria/{id}`.
1. Reemplazar el marcador de `/app/auditoria` por la lista con filtros (usuario, acción, fecha) y el detalle del cambio.
2. Solo ADMIN_SISTEMA y AUDITOR.

## Fase 9. Datos que la API aún no trae (pequeños cambios de backend)
1. Nombre del solicitante en la bandeja (hoy solo ADMIN_SISTEMA y AUDITOR leen `/api/usuarios`): agregarlo al DTO de la solicitud.
2. Usuarios por empresa en la consola del dueño (solo el conteo, sin datos de negocio).
3. Variación frente al mes anterior en los indicadores del inicio.
4. Regenerar tipos y cambiar las pantallas.

## Fase 10. Cierre de calidad
1. Pruebas de componente para todas las pantallas nuevas y pruebas de las rutas protegidas.
2. Prueba de punta a punta (Playwright o equivalente) con dos empresas a la vez: A no ve nada de B.
3. Accesibilidad (teclado, contraste, etiquetas) y vista de teléfono en todas las pantallas.
4. `ng build` de producción sin advertencias; revisar el tamaño de los paquetes.
5. Revisar que ningún secreto, token ni dato personal queda en el código ni en el almacenamiento (el JWT solo en memoria).
6. Actualizar `references/frontend.md`, `PROGRESS.md` y `estado.md`; regenerar el documento de exposición.

## Orden y dependencias
Fase 0 primero. Las fases 1 y 2 no dependen de nada y desbloquean la demostración de seguridad y de IA. Las fases 3 a 8 son independientes entre sí (se pueden repartir entre dos personas). La fase 9 se hace cuando se llegue a la bandeja y a la consola. La fase 10 va al final, pero las pruebas de cada pantalla se escriben dentro de su fase.

## Cómo verificar que no se omitió nada
Al terminar cada fase, repetir el cruce de rutas (API contra uso del frontend) y que la lista de rutas sin uso solo contenga rutas que se decidió dejar fuera a propósito, anotadas con su motivo en `references/frontend.md`.

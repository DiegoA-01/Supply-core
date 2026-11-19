# Tasks 001

Hechas en `feature/mejoras-negocio` (V29), mezclada a `develop` por el PR #27 con los trabajos `backend`, `frontend`,
`docker` y Dependency Review en verde:

- [x] T1: Módulo `VENTAS` en los planes Profesional y Empresarial
- [x] T2: Tarifas de impuesto por empresa + pantalla y endpoints
- [x] T3: Datos de venta del producto (código de barras único por empresa, tarifa, vendible, precio con IVA incluido)
- [x] T4: Listas de precios con "General" obligatoria
- [x] T5: Precios con historial, precio mínimo y auditoría
- [x] T6: Margen por producto y global calculado en Java
- [x] T7: Delegación de precios al jefe de compras (D8)
- [x] T8: Pantalla `/app/precios` y prueba de fuga de precios

Pendientes (rama `feature/ventas-catalogo`):

- [x] T9: Confirmar que `feature/mejoras-negocio` quedó en `develop` y que sus pruebas pasan; reservar V31 en el README
- [x] T10: V31 con los módulos `CAJA` y `CARTERA` en sus planes + constantes + prueba de los planes
- [x] T11: V31 con los roles `SUPERVISOR_VENTAS` y `VENDEDOR` + constantes + asignación en la pantalla de usuarios
- [x] T12: V31 con el único precio vigente por producto, lista y unidad + prueba de concurrencia del primer precio
- [x] T13: Precio por unidad (D9) en el request, la consulta y la pantalla + pruebas
- [x] T14: Carga masiva de precios por Excel (todo o nada) + pruebas + botón en la pantalla
- [x] T15: `GET /api/ventas/productos?q=` para cajero, vendedor y supervisor + prueba de fuga
- [x] T16: Regenerar tipos del frontend y ampliar `scripts/sembrar_demo.py` con precios y un supervisor de ventas
- [x] T17: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas de la construcción (2026-11-19):
- V31: módulos `CAJA` y `CARTERA`, roles `SUPERVISOR_VENTAS` y `VENDEDOR`, columna generada `vigente_unico` con la
  regla única `uq_precio_vigente`. Antes de crear la regla, la migración cierra duplicados si los hubiera.
- Dos personas fijando a la vez el primer precio: una gana y la otra recibe 409 `PRECIO_CONCURRENTE` (choque con la regla
  única o bloqueo mutuo de MySQL); la prueba de concurrencia lo comprueba con dos hilos.
- La búsqueda escapa `%` y `_` (se buscan como texto) y no devuelve costo, margen ni precio mínimo.
- Los tipos del frontend se agregaron a mano en `esquema.ts` (el backend local no arrancó desde el panel para
  regenerarlos); conviene correr `npm run api:tipos` la próxima vez que el backend esté arriba.
- `scripts/sembrar_demo.py` suma cajero, vendedor y supervisor de ventas y los precios de la lista General (con uno por
  docena). No se ejecutó: la demo actual ya está sembrada y el script no se repite sobre ella.
- Pruebas: 786 del backend (1 omitida) y 154 del frontend en verde; doclint sin advertencias; build de producción limpio.

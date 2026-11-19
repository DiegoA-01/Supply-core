# Tasks 001

Hechas en `feature/mejoras-negocio` (V29). Se confirman al mezclar esa rama, cuando el CI o `./mvnw test` pasen en
`develop`:

- [x] T1: Módulo `VENTAS` en los planes Profesional y Empresarial
- [x] T2: Tarifas de impuesto por empresa + pantalla y endpoints
- [x] T3: Datos de venta del producto (código de barras único por empresa, tarifa, vendible, precio con IVA incluido)
- [x] T4: Listas de precios con "General" obligatoria
- [x] T5: Precios con historial, precio mínimo y auditoría
- [x] T6: Margen por producto y global calculado en Java
- [x] T7: Delegación de precios al jefe de compras (D8)
- [x] T8: Pantalla `/app/precios` y prueba de fuga de precios

Pendientes (rama `feature/ventas-catalogo`):

- [ ] T9: Confirmar que `feature/mejoras-negocio` quedó en `develop` y que sus pruebas pasan; reservar V31 en el README
- [ ] T10: V31 con los módulos `CAJA` y `CARTERA` en sus planes + constantes + prueba de los planes
- [ ] T11: V31 con los roles `SUPERVISOR_VENTAS` y `VENDEDOR` + constantes + asignación en la pantalla de usuarios
- [ ] T12: V31 con el único precio vigente por producto, lista y unidad + prueba de concurrencia del primer precio
- [ ] T13: Precio por unidad (D9) en el request, la consulta y la pantalla + pruebas
- [ ] T14: Carga masiva de precios por Excel (todo o nada) + pruebas + botón en la pantalla
- [ ] T15: `GET /api/ventas/productos?q=` para cajero, vendedor y supervisor + prueba de fuga
- [ ] T16: Regenerar tipos del frontend y ampliar `scripts/sembrar_demo.py` con precios y un supervisor de ventas
- [ ] T17: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

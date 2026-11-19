# Spec: módulo de Ventas y Caja (índice)

**Estado:** propuesta para revisar. No hay código todavía.
**Fecha:** 2026-11-18.
**Ramas:** cada fase se construye después en su propia rama, sacada de `develop` (las ramas de cada fase están en la
sección 15 de cada parte).

La spec se dividió en **tres partes** para construirla por bloques. Cada parte se puede revisar, aprobar y construir por
separado, en orden. Todo el contenido de la spec original está en alguna de las tres (se comprobó línea por línea); las
secciones comunes viven en la parte 1 y las otras dos las siguen sin repetirlas.

| Parte | Archivo | Qué construye | Fases | Depende de |
|---|---|---|---|---|
| **1. Base, CI, catálogo de venta y clientes** | [SPEC_VENTAS_1_BASE_Y_CATALOGO.md](SPEC_VENTAS_1_BASE_Y_CATALOGO.md) | CI con GitHub Actions, reglas, roles, módulos, impuestos, código de barras, precios con historial, listas de precios y clientes | F0, F1, F2 | — |
| **2. Caja y venta en mostrador** | [SPEC_VENTAS_2_CAJA_Y_VENTA.md](SPEC_VENTAS_2_CAJA_Y_VENTA.md) | Cajas, turnos, libro de caja, PIN de supervisor, venta en una transacción, POS, tiquete, anulación y devolución | F3, F4, F5, F6 | Parte 1 |
| **3. Cartera, costos, reportes y endurecimiento** | [SPEC_VENTAS_3_CARTERA_COSTOS_REPORTES.md](SPEC_VENTAS_3_CARTERA_COSTOS_REPORTES.md) | Crédito y abonos, costo promedio y margen, reportes y tablero, cuadre diario automático, alertas y pruebas de punta a punta | F7, F8, F9, F10 | Partes 1 y 2 |

Al terminar la parte 2 el negocio ya puede **vender y cuadrar caja**. La parte 3 suma crédito, márgenes y control fino.

> **Antes de construir:** hay que actualizar `CLAUDE.md` (regla 7 y la descripción del proyecto) para que Ventas y Caja
> entren al alcance. Hoy el proyecto se describe como compras e inventario.

## Dónde quedó cada sección de la spec original

| Sección original | Parte 1 | Parte 2 | Parte 3 |
|---|---|---|---|
| 1. Objetivo y fuera de alcance | ✔ | | |
| 2. Reglas de oro | ✔ (común) | | |
| 3. Glosario | ✔ (común) | | |
| 4. Roles y permisos | ✔ (común) | | |
| 5. Módulos y planes | ✔ | | |
| 6.1 Catálogo de venta | ✔ | | costo promedio |
| 6.2 Clientes | ✔ | | |
| 6.3 Cajas y turnos | | ✔ | |
| 6.4 Ventas | | ✔ | |
| 6.5 Cartera | | | ✔ |
| 6.6 Inventario | | ✔ | costo unitario |
| 7.1 a 7.7 Flujos de caja y venta | | ✔ | |
| 7.8 Crédito y cartera | | | ✔ |
| 8. Controles contra pérdidas | | ✔ | cuadre diario y costo |
| 9. Seguridad | ✔ (común) | | |
| 10. API | precios, impuestos, clientes, búsqueda | cajas, turnos, ventas, devoluciones, autorizaciones | cartera y reportes |
| 11. Pantallas | precios, clientes | POS, tiquete, mi turno, ventas, cajas | cartera, tablero |
| 12. Reportes e historial | | | ✔ |
| 13. Pruebas | estrategia completa (común) | las de venta y caja | punta a punta |
| 14. Integración continua | ✔ (F0) | | trabajo `e2e` (F10) |
| 15. Fases | F0, F1, F2 | F3 a F6 | F7 a F10 y futuro |
| 16. Decisiones | D2, D5, D8 | D1, D3, D6, D7 | D4 |
| 17. Riesgos | | POS rápido, sin internet, bloqueos | DIAN |

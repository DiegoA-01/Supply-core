# 001 - Catálogo de venta: módulos, roles, impuestos y precios

**Estado:** Hecha (2026-11-19, PR #28). Gran parte quedó hecha en la rama `feature/mejoras-negocio` (migración V29), ya mezclada a
`develop` por el PR #27 con el CI en verde; lo que falta va en `feature/ventas-catalogo`. · **Depende de:** 000

## Objetivo
Dejar listo todo lo que el punto de venta necesita saber antes de cobrar: qué módulos contrató la empresa, quién puede
vender o autorizar, qué impuesto lleva cada producto, cómo se reconoce con el lector y a qué precio se vende en cada
lista, con el historial de cada cambio.

## Historias
- Como dueño del software, quiero ofrecer Ventas, Caja y Cartera como módulos del plan, para cobrar según lo que cada
  empresa usa.
- Como dueño del negocio, quiero asignar los roles de vendedor y supervisor de ventas, para separar quién vende de quién
  autoriza.
- Como dueño del negocio, quiero configurar los impuestos de venta (IVA 19 %, IVA 5 %, exento, excluido) y asignarlos a
  cada producto.
- Como dueño del negocio, quiero registrar el código de barras de cada producto, para venderlo con el lector.
- Como dueño del negocio, quiero marcar qué productos se venden y cuáles solo se consumen internamente.
- Como dueño del negocio, quiero fijar el precio de venta de cada producto en cada lista ("General", "Mayorista",
  "Taller aliado"…) con un precio mínimo, para que nadie venda por debajo sin autorización.
- Como dueño del negocio, quiero poder delegar los precios al jefe de compras.
- Como dueño del negocio, quiero ver qué precio tenía un producto cada día, quién lo cambió y por qué.
- Como dueño del negocio, quiero ver el margen de cada producto y el global, para saber si mis precios dejan ganancia.
- Como dueño del negocio, quiero un precio por caja y otro por unidad del mismo producto (decisión D9).
- Como dueño del negocio, quiero cambiar muchos precios a la vez con un Excel.
- Como cajero o vendedor, quiero buscar un producto por código, código de barras o nombre y ver su precio vigente y su
  stock.

## Criterios de aceptación
- [x] El módulo Ventas existe y viene en los planes Profesional y Empresarial. *(V29)*
- [x] Los módulos Caja (Profesional y Empresarial) y Cartera (solo Empresarial) existen.
- [x] Los roles `VENDEDOR` y `SUPERVISOR_VENTAS` existen y el administrador los puede asignar.
- [x] Cada empresa tiene IVA 19 %, IVA 5 %, exento y excluido, y puede crear o desactivar tarifas. *(V29)*
- [x] El código de barras es único dentro de la empresa; dos empresas pueden repetirlo. *(V29)*
- [x] Cada empresa tiene una lista "General" obligatoria y puede crear más. *(V29)*
- [x] Cambiar un precio cierra la vigencia del anterior; el historial muestra fecha, persona y motivo. *(V29)*
- [x] El precio mínimo nunca es mayor que el precio. *(V29)*
- [x] El precio de mostrador incluye IVA (D2) y el margen lo calcula el sistema. *(V29)*
- [x] Solo el dueño fija precios, salvo que los delegue al jefe de compras (D8). *(V29)*
- [x] Nunca hay dos precios vigentes para el mismo producto, lista y unidad, ni siquiera si dos personas guardan a la vez.
- [x] Un producto puede tener precio por caja y por unidad (D9).
- [x] El cambio masivo de precios por Excel funciona y es todo o nada.
- [x] La búsqueda para vender encuentra por código, código de barras o nombre y trae precio vigente y stock.
- [x] Una empresa nunca ve ni cambia impuestos, listas o precios de otra.
- [x] Todas las pruebas pasan, incluidas las de fuga entre empresas.

## Fuera de alcance
- Clientes (spec 002), caja (003) y la venta (004).
- Costo promedio (008): el margen de esta spec usa el último costo de compra.

## Decisiones
D2, D5, D8 y D9 **tomadas** (ver `agents/specs/README.md`).

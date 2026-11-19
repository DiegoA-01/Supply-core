# 008 - Costo promedio y margen real

**Estado:** Propuesta · **Rama:** `feature/inventario-costo-promedio` · **Depende de:** 004

## Objetivo
Saber cuánto gana el negocio de verdad en cada venta, con un costo que refleje lo que se pagó por toda la mercancía en
existencia y no solo la última compra.

## Historias
- Como dueño, quiero que cada vez que entra mercancía comprada se actualice el costo promedio del producto.
- Como dueño, quiero que cada venta guarde el costo del momento, para que su margen no cambie después.
- Como dueño, quiero ver el margen por producto y por categoría.
- Como dueño, quiero una alerta si un producto se vende por debajo de su costo promedio.

## Criterios de aceptación
- [ ] Cada recepción actualiza el costo promedio ponderado: (existencia × costo anterior + cantidad recibida × costo de
  la compra) ÷ (existencia + cantidad recibida).
- [ ] Cada movimiento de inventario guarda el costo unitario con el que se movió.
- [ ] La línea de venta guarda el costo promedio del momento.
- [ ] El margen por producto coincide con un cálculo a mano.
- [ ] Vender por debajo del costo promedio genera alerta (y el precio mínimo sigue exigiendo autorización).
- [ ] La pantalla de precios muestra el margen con el costo promedio en lugar del último costo.
- [ ] Todas las pruebas pasan.

## Fuera de alcance
- Métodos de costeo distintos al promedio ponderado (PEPS, UEPS).
- Recalcular el costo de movimientos pasados.

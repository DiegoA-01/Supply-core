# 008 - Costo promedio y margen real

**Estado:** Hecha (V36) · **Rama:** `feature/ventas-pos` (con 004 a 008) · **Depende de:** 004

## Objetivo
Saber cuánto gana el negocio de verdad en cada venta, con un costo que refleje lo que se pagó por toda la mercancía en
existencia y no solo la última compra.

## Historias
- Como dueño, quiero que cada vez que entra mercancía comprada se actualice el costo promedio del producto.
- Como dueño, quiero que cada venta guarde el costo del momento, para que su margen no cambie después.
- Como dueño, quiero ver el margen por producto y por categoría.
- Como dueño, quiero una alerta si un producto se vende por debajo de su costo promedio.

## Criterios de aceptación
- [x] Cada recepción actualiza el costo promedio ponderado: (existencia × costo anterior + cantidad recibida × costo de
  la compra) ÷ (existencia + cantidad recibida).
- [x] Cada movimiento de inventario guarda el costo unitario con el que se movió.
- [x] La línea de venta guarda el costo promedio del momento.
- [x] El margen por producto coincide con un cálculo a mano.
- [x] Vender por debajo del costo promedio genera alerta (y el precio mínimo sigue exigiendo autorización).
- [x] La pantalla de precios muestra el margen con el costo promedio en lugar del último costo.
- [x] Todas las pruebas pasan.

## Fuera de alcance
- Métodos de costeo distintos al promedio ponderado (PEPS, UEPS).
- Recalcular el costo de movimientos pasados.

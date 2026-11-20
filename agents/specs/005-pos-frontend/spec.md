# 005 - Punto de venta, tiquete y mi turno

**Estado:** Hecha (falta probar en Docker) · **Rama:** `feature/ventas-pos` (con 004 a 008) · **Depende de:** 004

## Objetivo
Que el cajero venda rápido con el lector y el teclado, cobre con pagos mixtos, imprima el tiquete y maneje su turno
desde una sola pantalla.

## Historias
- Como cajero, quiero escanear un producto y que se agregue al carrito sin tocar el mouse.
- Como cajero, quiero atajos de teclado: F2 buscar, F4 cliente, F9 cobrar, Esc cancelar.
- Como cajero, quiero cambiar cantidades en el carrito y ver los totales que calcula el sistema.
- Como cajero, quiero cobrar con varios medios de pago y ver el cambio que calcula el sistema.
- Como cajero, quiero buscar o crear el cliente desde el POS.
- Como cajero, quiero cargar un pedido de venta que armó un vendedor y cobrarlo.
- Como cajero, quiero pedir la autorización del supervisor en la misma pantalla.
- Como cajero, quiero imprimir el tiquete en la impresora térmica de 80 mm.
- Como cajero, quiero abrir mi turno, registrar retiros y gastos y hacer el cierre ciego desde "Mi turno".
- Como dueño, quiero que una reimpresión diga "COPIA" y quede registrada.
- Como supervisor o dueño, quiero ver el historial de ventas con filtros y su detalle.

## Criterios de aceptación
- [x] El campo del lector tiene el foco siempre; Enter agrega el producto.
- [x] Los atajos F2, F4, F9 y Esc funcionan.
- [x] La pantalla nunca calcula dinero: totales y cambio vienen del servidor.
- [x] Se puede cobrar con pagos mixtos y el cambio aparece antes de confirmar.
- [x] Aparece el aviso cuando la caja pasa su tope de efectivo.
- [x] El tiquete sale en 80 mm con consecutivo, NIT, impuestos discriminados y "COPIA" en las reimpresiones.
- [x] La reimpresión queda en la auditoría.
- [x] "Mi turno" permite abrir, retirar, registrar gastos y cerrar a ciegas; no muestra el esperado antes de guardar.
- [~] Una venta completa se puede hacer desde la pantalla en el stack de Docker (probada en la demo local; falta Docker).
- [x] Hay pruebas de cada pantalla y pasan.

## Fuera de alcance
- Vender sin internet.
- Anular y devolver desde el historial (spec 006).

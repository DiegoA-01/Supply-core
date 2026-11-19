# 003 - Cajas, turnos y cierre ciego

**Estado:** Hecha (2026-11-19, PR #30) · **Rama:** `feature/caja-turnos` · **Depende de:** 001

## Objetivo
Saber en todo momento cuánto dinero debe haber en cada caja, quién la tuvo y si al cerrar faltó o sobró, sin que el
cajero pueda "acomodar" el conteo.

## Historias
- Como dueño del negocio, quiero crear cajas (por ejemplo "Caja 1 mostrador"), cada una de una sede y de una bodega, con
  un tope de efectivo.
- Como cajero, quiero abrir mi turno en una caja de mi sede contando la base en efectivo.
- Como cajero, quiero que el sistema me avise cuando tengo demasiado efectivo y deba hacer un retiro a la bóveda.
- Como cajero, quiero registrar un gasto menor con su motivo y, si quiero, la foto del recibo.
- Como supervisor, quiero autorizar retiros, gastos y otras acciones del cajero en su misma pantalla con mi PIN, sin que
  él cierre su sesión.
- Como cajero, quiero cerrar mi turno contando el efectivo **sin ver** cuánto debería tener, y escribir los totales de
  los vouchers de tarjeta y de las transferencias.
- Como supervisor o dueño, quiero aprobar con una explicación las diferencias de caja que pasan la tolerancia, y ver los
  turnos abiertos, los cierres y las diferencias por aprobar.
- Como dueño, quiero el reporte del cierre (tiquete Z) impreso y guardado.

## Criterios de aceptación
- [x] Una caja descuenta de una sola bodega (D7).
- [x] Una caja no puede tener dos turnos abiertos, ni un cajero dos turnos abiertos a la vez.
- [x] Al abrir el turno la base queda registrada como el primer movimiento de la caja.
- [x] Todo retiro, gasto o ingreso queda registrado con motivo, quién lo hizo y quién lo autorizó.
- [x] Al pasar el tope de efectivo de la caja aparece el aviso de retiro.
- [x] El esperado se calcula como: base + ventas en efectivo + abonos − devoluciones en efectivo − retiros − gastos.
- [x] El cajero no ve el esperado hasta guardar lo contado.
- [x] La diferencia queda guardada por medio de pago y no cambia después.
- [x] Si la diferencia pasa la tolerancia ($2.000 por defecto, configurable por empresa, D6) el turno queda pendiente de
  aprobación; nadie aprueba la diferencia de su propio turno.
- [x] El PIN del supervisor es de 6 dígitos, se bloquea 15 minutos tras 5 intentos fallidos y nunca aparece en una
  dirección web.
- [x] Una autorización vale para una sola acción, de un solo turno, durante 2 minutos.
- [x] Abrir, cerrar y aprobar turnos, retiros, gastos y autorizaciones quedan en la auditoría.
- [x] Una empresa nunca ve cajas, turnos ni movimientos de otra.
- [x] Todas las pruebas pasan, incluidas las de fuga entre empresas.

## Fuera de alcance
- Vender (spec 004): aquí el libro de caja ya acepta movimientos de venta, pero no hay POS.
- La pantalla "Mi turno" del cajero (spec 005).
- Abonos de cartera (spec 007).

## Decisiones
D6 y D7 **tomadas** (ver `agents/specs/README.md`).

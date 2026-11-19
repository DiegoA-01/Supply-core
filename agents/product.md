# Producto

## Problema
Los negocios pequeños y medianos (ferretería, repuestos de moto o de carro, eléctricos, droguería…) compran, guardan y
venden con hojas de cálculo, cuadernos y programas que no se hablan entre sí. No saben a tiempo qué les falta, pagan de
más a los proveedores y no pueden demostrar cuánto dinero entró o salió de cada caja.

Supply-Core es una plataforma web **SaaS multiempresa**: el dueño del software activa a cada empresa cliente y le
enciende los módulos de su plan. Cada empresa ve solo sus datos.

## Usuarios
- **Dueño del software (plataforma):** activa empresas, planes y módulos. Nunca ve datos de negocio.
- **Administrador de la empresa (`ADMIN_SISTEMA`, el dueño del negocio):** configura su empresa y sus usuarios.
- **Usuarios con rol:** jefe de compras (`ADMIN_COMPRAS`), comprador, solicitante, aprobador, almacenista (bodeguero),
  administrativo (tesorería), auditor, cajero y, con ventas, `VENDEDOR` y `SUPERVISOR_VENTAS`.
- **Proveedor:** entra a su portal a cotizar y a ver sus órdenes. Nunca ve faltantes, stock ni urgencias de la empresa.

## Dentro del alcance
- **Compras (hecho):** necesidad → solicitud → aprobación → RFQ → cotizaciones (formulario o archivo leído con IA) →
  análisis y ranking → adjudicación → orden de compra → anticipo → despacho del proveedor → recepción con calidad →
  factura cruzada con lo recibido → pago final registrado → evaluación del proveedor.
- **Inventario (hecho):** bodegas, kardex, ajustes, traslados, rotación, reorden y plan de reabastecimiento.
- **Compras avanzadas (hecho):** presupuesto por centro de costo, contratos con precios pactados, delegación y SLA de
  aprobación, carga masiva por Excel, informe de compras para el dueño.
- **Ventas y caja (en construcción, specs 001 a 010):** que el negocio también **venda**:
  - cobrar en un punto de venta (POS) rápido, con lector de código de barras;
  - saber en todo momento **cuánto entró, cuánto salió y cuánto debe haber en cada caja**;
  - descontar el stock en el mismo instante de la venta, sin pasos manuales;
  - ver qué vendió, a quién, quién lo vendió, con qué margen y por qué medio de pago;
  - no perder dinero por errores, descuidos o fraude interno, y poder demostrarlo con un historial que no se borra.

## Fuera del alcance

| Tema | Por qué |
|---|---|
| Factura electrónica DIAN | Fase propia, después de ventas (ya anotada como fase futura). El modelo de venta queda listo para emitirla. |
| Pasarela de pagos o datáfono integrado | El cobro con tarjeta se **registra** (últimos 4 dígitos y número de autorización). No se procesan tarjetas. La pasarela solo cobra la suscripción SaaS. |
| Pagar a proveedores por pasarela | El pago a proveedores solo se registra. |
| Multimoneda | Fase futura. Todo en la moneda base de la empresa (COP). |
| Tienda en línea o marketplace | Fuera del alcance del proyecto. |
| Vender sin internet | Limitación documentada; un modo sin conexión sería una spec aparte. |

## Glosario de ventas

| Término | Significado |
|---|---|
| **Caja** | Punto de venta físico (por ejemplo "Caja 1 mostrador"). Pertenece a una sede y descuenta de una bodega. |
| **Turno** | Periodo en que un cajero trabaja una caja: se abre con una base en efectivo y se cierra con un arqueo. |
| **Arqueo** | Conteo físico del dinero al cerrar el turno. |
| **Cierre ciego** | El cajero cuenta y escribe lo que tiene **sin ver** cuánto debería tener. Después el sistema compara. |
| **Diferencia** | Contado menos esperado. Sobrante (positivo) o faltante (negativo). Siempre queda registrada. |
| **Anulación** | Deja sin efecto una venta del turno abierto (error de digitación, cliente desistió). Devuelve stock y dinero. |
| **Devolución** | El cliente regresa productos de una venta ya cerrada. Tiene su propio consecutivo y reintegra según el estado del producto. |
| **Medio de pago** | Efectivo, tarjeta débito, tarjeta crédito, transferencia, billetera digital (Nequi, Daviplata), crédito del cliente. |
| **Cartera** | Lo que los clientes deben por ventas a crédito. |

## Reglas del negocio para ventas
Ninguna spec de ventas se da por terminada si rompe alguna.

1. **El sistema calcula todo el dinero.** Subtotales, descuentos, impuestos, total, cambio y diferencias de caja; la
   pantalla solo muestra lo que calcula el servidor.
2. **El stock solo cambia por movimientos de inventario.** Una venta descuenta y una devolución reintegra, en la misma
   operación que la venta: si un producto no alcanza, no se vende nada.
3. **Una venta confirmada no se edita ni se borra.** Para corregirla se **anula** (mismo turno, con motivo y
   autorización) o se hace una **devolución** (cualquier día, con su propio consecutivo). El consecutivo anulado no se
   reutiliza: no hay huecos en la numeración.
4. **Foto fija en cada línea.** La venta guarda el precio, el impuesto, el descuento y el costo **del momento de la
   venta**. Si mañana cambia el precio, las ventas de ayer no cambian.
5. **Todo peso que entra o sale de una caja tiene un registro.** Ventas, devoluciones en efectivo, retiros, gastos menores
   y base de apertura. La caja se cuadra con esos registros, no con lo que alguien recuerde.
6. **Cada empresa ve solo lo suyo.**
7. **Doble permiso:** el módulo debe estar contratado por la empresa y el usuario debe tener el rol.
8. **Las acciones sensibles quedan en la auditoría:** anular, devolver, dar descuento por encima del límite, cambiar
   precios, abrir y cerrar turno, registrar diferencias de caja.
9. **Nadie autoriza lo suyo.** El cajero no aprueba su propia anulación, su propio descuento especial ni su propia
   diferencia de caja (igual que en las solicitudes).

## Roles y permisos de ventas
Se usa el catálogo global de roles. Se agregan **dos roles** nuevos: `SUPERVISOR_VENTAS` y `VENDEDOR`. `CAJERO` ya existe
y por fin tiene pantallas. El dueño puede asignar los dos roles nuevos a la misma persona en negocios pequeños.

| Acción | CAJERO | VENDEDOR | SUPERVISOR_VENTAS | ADMIN_SISTEMA (dueño) | AUDITOR |
|---|---|---|---|---|---|
| Abrir y cerrar **su** turno | ✔ | | ✔ | | |
| Vender y cobrar en su turno | ✔ | | ✔ | | |
| Armar una cotización o pedido de venta (sin cobrar) | ✔ | ✔ | ✔ | | |
| Descuento hasta el límite de su rol | ✔ | ✔ | ✔ | | |
| Autorizar descuento por encima del límite | | | ✔ | ✔ | |
| Anular una venta del turno | pide | | autoriza | autoriza | |
| Devolución de cliente | pide | | autoriza | autoriza | |
| Retiro de efectivo o gasto menor | pide | | autoriza | autoriza | |
| Aprobar la diferencia de un cierre | | | ✔ (no la suya) | ✔ | |
| Precios de venta y listas | | | | ✔ (o `ADMIN_COMPRAS` si el dueño lo delega) | lee |
| Clientes | crea y edita | crea y edita | ✔ | ✔ | lee |
| Reportes de ventas y caja | su turno | sus ventas | todos | todos | todos |

**Autorización en el mostrador.** Cuando el cajero necesita una autorización (anular, descuento alto), el supervisor la da
**en la misma pantalla** con su correo y un **PIN de supervisor de 6 dígitos**. Queda auditado quién pidió y quién
autorizó. No hace falta cerrar la sesión del cajero.

## Módulos y planes

| Módulo | Qué incluye |
|---|---|
| `VENTAS` | POS, clientes, precios de venta, listas de precios, impuestos, devoluciones |
| `CAJA` | Cajas, turnos, movimientos de caja, arqueo y cierre ciego |
| `CARTERA` | Ventas a crédito, cupos, abonos y edades de cartera |

- **Plan Básico:** no los trae (es solo compras e inventario).
- **Plan Profesional:** `VENTAS` + `CAJA`.
- **Plan Empresarial:** todo, incluido `CARTERA`.

Cada módulo se activa o se apaga desde la consola del dueño del software, igual que los actuales. Con el módulo apagado
la empresa no puede usarlo; con la suscripción morosa, solo puede consultar.

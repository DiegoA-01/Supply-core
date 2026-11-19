# 002 - Clientes

**Estado:** Propuesta · **Rama:** `feature/ventas-clientes` · **Depende de:** 001

## Objetivo
Saber a quién se le vende cuando hace falta (crédito, devoluciones, listas especiales), sin frenar la venta rápida al
consumidor final y cuidando los datos personales.

## Historias
- Como cajero o vendedor, quiero buscar un cliente por su documento y, si no existe, crearlo rápido desde el POS.
- Como cajero, quiero vender sin pedir datos cuando el cliente no los quiere dar (consumidor final).
- Como dueño del negocio, quiero asignar a un cliente una lista de precios (por ejemplo "Mayorista").
- Como dueño del negocio, quiero guardar el cupo de crédito y el plazo de un cliente, para usarlos cuando se active la
  cartera.
- Como cliente, quiero que mis datos solo se guarden si autorizo su tratamiento.
- Como auditor, quiero consultar los clientes sin poder cambiarlos.

## Criterios de aceptación
- [ ] Cada empresa tiene siempre un cliente "Consumidor final" que no se puede borrar ni desactivar.
- [ ] No se pueden crear dos clientes con el mismo tipo y número de documento en la misma empresa; en otra empresa sí.
- [ ] Los tipos de documento admitidos son CC, NIT, CE y pasaporte, y el número se valida según el tipo.
- [ ] Para guardar un cliente con datos de contacto se registra la fecha en que autorizó el tratamiento de datos.
- [ ] Un cliente puede tener una lista de precios; si no tiene, se usa la General.
- [ ] Cajero, vendedor y supervisor crean y editan clientes; el dueño también; el auditor solo los lee.
- [ ] Exportar clientes solo lo pueden hacer los roles con permiso.
- [ ] Los clientes no se borran: se desactivan.
- [ ] Una empresa nunca ve los clientes de otra.
- [ ] Todas las pruebas pasan, incluidas las de fuga entre empresas.

## Fuera de alcance
- Vender a crédito y cobrar abonos (spec 007): aquí solo se guardan el cupo y el plazo.
- Pantalla de cartera.

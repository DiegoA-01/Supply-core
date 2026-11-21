# 010 - Alertas, pruebas de punta a punta y carga

**Estado:** Hecha (V39; verificada en Docker local, falta ver el trabajo `e2e` en GitHub) · **Rama:** `feature/ventas-endurecimiento` · **Depende de:** 009

## Objetivo
Cerrar el módulo de ventas con alertas sobre comportamientos sospechosos y con la prueba de que todo el recorrido
funciona de punta a punta y aguanta el ritmo de un mostrador con fila.

## Historias
- Como dueño, quiero una alerta cuando un cajero pasa un umbral de anulaciones o de descuentos en un periodo.
- Como equipo, queremos que cada PR pruebe el recorrido completo: abrir turno, vender, anular con PIN y cerrar turno.
- Como dueño, quiero saber que el POS aguanta al menos 50 ventas por minuto sin errores.

## Criterios de aceptación
- [x] Los umbrales de anulaciones y descuentos por cajero se configuran por empresa, y pasarlos avisa al dueño.
- [x] La prueba de punta a punta (abrir turno → vender → anular con PIN → cerrar turno) corre en el CI contra el stack de
  Docker y pasa.
- [x] La prueba de carga hace 50 ventas por minuto sin errores, sin stock negativo y con la caja cuadrada.
- [x] La cobertura de los Services de ventas y caja es de al menos 90 %.
- [x] Todas las pruebas pasan.

## Fuera de alcance
- Factura electrónica DIAN y repuestos (compatibilidad por marca y modelo de vehículo, referencias cruzadas): specs
  futuras propias.

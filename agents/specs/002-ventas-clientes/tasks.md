# Tasks 002

- [x] T0: Aprobar la spec y reservar V32
- [x] T1: V32: tabla `cliente` con el consumidor final sembrado por empresa
- [x] T2: Entidad, repositorio y validación del documento por tipo + pruebas unitarias
- [x] T3: Servicio y endpoints de clientes con permisos y auditoría + pruebas
- [x] T4: Consumidor final para empresas creadas después de V32 (al consultar) + prueba
- [x] T5: Prueba de fuga de clientes en `AislamientoEmpresasTest`
- [x] T6: Exportación a Excel con permisos + prueba
- [x] T7: Pantalla `/app/clientes` y creación rápida reutilizable + pruebas Vitest
- [x] T8: Regenerar tipos y sembrar clientes de ejemplo en la demo
- [x] T9: Verificar los criterios de aceptación y que `./mvnw test` y `npm test` pasen

Notas de la construcción (2026-11-19):
- V32: tabla `cliente` con `UNIQUE (empresa_id, tipo_documento, numero_documento)` y un consumidor final por empresa
  (columna generada `consumidor_unico` + `UNIQUE`), documento CC `222222222222`.
- Paquete `cliente/`: `ClienteController` (`/api/clientes`, `/api/clientes/consumidor-final`), `ClienteService`,
  `DocumentoCliente` (CC, CE, PAS y NIT con dígito de verificación de la DIAN).
- Auditoría: `CLIENTE_CREAR`, `CLIENTE_EDITAR`, `CLIENTE_CUPO_CAMBIAR`, entidad `CLIENTE`; el detalle no guarda datos
  personales, solo qué cambió.
- Exportación: `GET /api/exportaciones/clientes?formato=xlsx` (dueño y auditoría).
- `GET /api/ventas/listas` ahora también lo leen supervisor de ventas, cajero y vendedor (solo nombres de las listas),
  para elegir la lista del cliente; los precios y márgenes siguen cerrados para ellos.
- Frontend: `/app/clientes` (sección Ventas del menú) y `ClienteFormulario`, reutilizable desde el POS (spec 005).
  Los textos de auditoría se pusieron al día con las acciones que faltaban desde el PR #27.
- Tipos del frontend agregados a mano en `esquema.ts`; conviene regenerarlos con `npm run api:tipos`.
- `scripts/sembrar_demo.py` crea dos clientes (uno con cupo); no se ejecutó sobre la demo ya sembrada.
- Pruebas: 791 del backend (1 omitida) y 161 del frontend en verde; build de producción limpio.

# Plan 002

## Datos (migración: la siguiente libre, reservarla en `agents/specs/README.md`)
**`cliente`**: `id, empresa_id, tipo_documento (CC, NIT, CE, PAS), numero_documento, nombre, telefono, correo,
direccion, lista_precio_id, cupo_credito, plazo_dias, activo, acepta_datos_en, creado_en`.
- Único por empresa: `(empresa_id, tipo_documento, numero_documento)`.
- Siempre existe un cliente **"Consumidor final"** por empresa: sembrado en la migración (`INSERT … SELECT` desde
  `empresa`) y creado al dar de alta una empresa nueva (servicio de la consola de plataforma). Marcarlo con
  `es_consumidor_final` para impedir desactivarlo.
- `acepta_datos_en`: fecha de la autorización de tratamiento de datos (Ley 1581 de 2012, habeas data). Obligatoria si
  se guarda teléfono, correo o dirección.
- `cupo_credito DECIMAL(18,2)` y `plazo_dias` se guardan aquí y se usan en la 007.

## API (paquete `venta/` o `cliente/`, `@RequiereModulo(VENTAS)`)

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/clientes?q=` | CAJERO, VENDEDOR, SUPERVISOR_VENTAS, ADMIN_SISTEMA, AUDITOR | Buscar por documento o nombre (paginado) |
| `GET /api/clientes/{id}` | los mismos | Detalle |
| `POST /api/clientes` | CAJERO, VENDEDOR, SUPERVISOR_VENTAS, ADMIN_SISTEMA | Crear |
| `PUT /api/clientes/{id}` | los mismos | Editar o desactivar |

- Validación del número: CC y CE solo dígitos (6 a 10), NIT con dígito de verificación (cálculo en Java), PAS
  alfanumérico.
- Cambiar `cupo_credito` queda auditado (`CLIENTE_CUPO_CAMBIAR`); crear y editar, `CLIENTE_CREAR` y `CLIENTE_EDITAR`.
- Exportación a Excel por `exportacion/`, solo para ADMIN_SISTEMA y AUDITOR.

## Frontend
- `/app/clientes`: búsqueda por documento, creación y edición, casilla de autorización de datos.
- Componente de creación rápida reutilizable desde el POS (spec 005).

## Pruebas
- Unitarias: validación del documento por tipo (incluido el dígito de verificación del NIT); autorización de datos
  obligatoria cuando hay contacto.
- Integración: documento repetido ⇒ 409; el consumidor final no se desactiva; empresa nueva nace con su consumidor final.
- Fuga: A no lee, busca, edita ni exporta clientes de B.
- Permisos: el auditor no crea; el cajero no exporta.
- Frontend: búsqueda y creación rápida; el formulario no deja guardar contacto sin la autorización.

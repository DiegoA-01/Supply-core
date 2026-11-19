# Plan 002

## Datos (V32, reservada en `agents/specs/README.md`)
**`cliente`**: `id, empresa_id, tipo_documento (CC, NIT, CE, PAS), numero_documento, nombre, telefono, correo,
direccion, lista_precio_id, cupo_credito, plazo_dias, activo, acepta_datos_en, creado_en`.
- Único por empresa: `(empresa_id, tipo_documento, numero_documento)`.
- Siempre existe un cliente **"Consumidor final"** por empresa (documento CC `222222222222`, el que usa la DIAN):
  sembrado en V32 (`INSERT … SELECT` desde `empresa`) y, para las empresas que se creen después, creado la primera vez
  que se consultan los clientes (`asegurarConsumidorFinal`, igual que la lista General de precios). No se crea desde la
  consola de plataforma porque esa consola nunca toca tablas de negocio. Lo marca `es_consumidor_final` (uno por
  empresa, garantizado con una columna generada y un `UNIQUE`) y no se edita ni se desactiva.
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

- Validación del número (Java, `DocumentoCliente`): CC solo dígitos (4 a 10), CE solo dígitos (4 a 12), NIT de 6 a 10
  dígitos más el dígito de verificación de la DIAN (se acepta `900123456-7` o `9001234567` y se guarda con guion), PAS
  letras y números (5 a 20, en mayúsculas). Se quitan puntos y espacios antes de validar.
- El cupo de crédito, el plazo y la lista de precios solo los cambian ADMIN_SISTEMA y SUPERVISOR_VENTAS (los demás que
  lo intenten reciben 403); el cajero y el vendedor sí crean clientes, sin cupo.
- Cambiar `cupo_credito` queda auditado (`CLIENTE_CUPO_CAMBIAR`); crear y editar, `CLIENTE_CREAR` y `CLIENTE_EDITAR`.
- Exportación a Excel: `GET /api/exportaciones/clientes` en `exportacion/` (reutiliza `ClienteService`), solo para
  ADMIN_SISTEMA y AUDITOR, y queda auditada como las demás exportaciones.

## Frontend
- `/app/clientes`: búsqueda por documento, creación y edición, casilla de autorización de datos.
- Componente de creación rápida reutilizable desde el POS (spec 005).

## Pruebas
- Unitarias: validación del documento por tipo (incluido el dígito de verificación del NIT); autorización de datos
  obligatoria cuando hay contacto.
- Integración: documento repetido ⇒ 409; el consumidor final no se edita ni se desactiva; una empresa sin consumidor
  final lo obtiene en su primera consulta.
- Fuga: A no lee, busca, edita ni exporta clientes de B.
- Permisos: el auditor no crea; el cajero no exporta.
- Frontend: búsqueda y creación rápida; el formulario no deja guardar contacto sin la autorización.

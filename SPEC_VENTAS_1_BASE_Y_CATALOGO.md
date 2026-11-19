# Spec de Ventas y Caja · Parte 1: base, CI, catálogo de venta y clientes (F0 a F2)

**Estado:** propuesta para revisar. No hay código todavía.
**Parte de:** [SPEC_VENTAS.md](SPEC_VENTAS.md) (índice). Va primero: deja el CI, las reglas, los roles, los módulos, los precios y los clientes que usan las partes 2 y 3.
**Fecha:** 2026-11-18.

---

> Las secciones 1 a 5, la seguridad y la estrategia de pruebas son **comunes a las tres partes**: las partes 2 y 3
> las siguen sin repetirlas.

## 1. Objetivo

Que Supply-Core también **venda**, no solo compre y guarde. El negocio (ferretería, repuestos de moto o de carro,
eléctricos, droguería…) debe poder:

- cobrar en un punto de venta (POS) rápido, con lector de código de barras;
- saber en todo momento **cuánto entró, cuánto salió y cuánto debe haber en cada caja**;
- descontar el stock en el mismo instante de la venta, sin pasos manuales;
- ver qué vendió, a quién, quién lo vendió, con qué margen y por qué medio de pago;
- no perder dinero por errores, descuidos o fraude interno, y poder demostrarlo con un historial que no se borra.

### Fuera de esta spec

| Tema | Por qué |
|---|---|
| Factura electrónica DIAN | Fase propia, después de este módulo (ya está anotada como fase futura). El modelo de venta queda listo para emitirla. |
| Pasarela de pagos o datáfono integrado | El cobro con tarjeta se **registra** (últimos 4 dígitos y número de autorización). No se procesan tarjetas. |
| Multimoneda | Sigue fuera del alcance actual. Todo en la moneda base de la empresa (COP). |
| Tienda en línea o marketplace | Fuera del alcance del proyecto. |

> **Antes de construir:** hay que actualizar `CLAUDE.md` (regla 7 y la descripción del proyecto) para que Ventas y Caja
> entren al alcance. Hoy el proyecto se describe como compras e inventario.

---

## 2. Reglas de oro de las ventas

Son la extensión de las reglas que ya tiene el proyecto. Ninguna fase se da por terminada si rompe alguna.

1. **Java calcula todo el dinero.** Subtotales, descuentos, impuestos, total, cambio y diferencias de caja se calculan en
   el backend con `BigDecimal` (2 decimales, redondeo `HALF_UP`). La pantalla solo muestra lo que devuelve el servidor.
2. **El stock solo cambia por `movimiento_inventario`.** Una venta genera `SALIDA_VENTA`; una devolución de cliente,
   `DEVOLUCION_CLIENTE`. Todo en la misma transacción que la venta: si un producto no alcanza, no se vende nada.
3. **Una venta confirmada es inmutable.** No se edita ni se borra. Para corregirla se **anula** (mismo turno, con motivo y
   autorización) o se hace una **devolución** (cualquier día, con su propio consecutivo). El consecutivo anulado no se
   reutiliza: no hay huecos en la numeración.
4. **Foto fija en cada línea.** La venta guarda el precio, el impuesto, el descuento y el costo **del momento de la
   venta**. Si mañana cambia el precio, las ventas de ayer no cambian.
5. **Todo peso que entra o sale de una caja tiene un registro.** Ventas, devoluciones en efectivo, retiros, gastos menores
   y base de apertura. La caja se cuadra con esos registros, no con lo que alguien recuerde.
6. **Aislamiento por empresa.** Toda tabla nueva lleva `empresa_id` (de `@TenantId`, nunca del request) y su prueba de
   fuga entre empresas en `AislamientoEmpresasTest`.
7. **Doble permiso.** Módulo activo para la empresa (`@RequiereModulo`) y rol del usuario (`@RequiereRol`).
8. **Acciones sensibles → `auditoria`.** Anular, devolver, dar descuento por encima del límite, cambiar precios, abrir y
   cerrar turno, registrar diferencias de caja.
9. **Nadie autoriza lo suyo.** El cajero no aprueba su propia anulación, su propio descuento especial ni su propia
   diferencia de caja (igual que en las solicitudes).

---

## 3. Glosario

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

---

## 4. Roles y permisos

Se usa el catálogo global de roles. Se agregan **dos roles** nuevos: `SUPERVISOR_VENTAS` y `VENDEDOR`.
`CAJERO` ya existe y por fin tiene pantallas.

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
**en la misma pantalla** con su correo y un **PIN de supervisor de 6 dígitos** (se guarda solo el hash con BCrypt, con
bloqueo tras 5 intentos fallidos). Queda auditado quién pidió y quién autorizó. No hace falta cerrar la sesión del cajero.

---

## 5. Módulos y planes (SaaS)

| Módulo nuevo | Qué incluye |
|---|---|
| `VENTAS` | POS, clientes, precios de venta, listas de precios, impuestos, devoluciones |
| `CAJA` | Cajas, turnos, movimientos de caja, arqueo y cierre ciego |
| `CARTERA` | Ventas a crédito, cupos, abonos y edades de cartera |

- **Plan Básico:** no los trae (es solo compras e inventario).
- **Plan Profesional:** `VENTAS` + `CAJA`.
- **Plan Empresarial:** todo, incluido `CARTERA`.

Cada módulo se activa o se apaga desde la consola del dueño del software, igual que los actuales.
Con el módulo apagado la API responde `403 MODULO_NO_CONTRATADO`; con la suscripción morosa, solo lectura.

---

## 6. Modelo de datos (parte 1)


Todas las tablas llevan `empresa_id` con `@TenantId`. Los montos son `DECIMAL(18,2)`; las cantidades, `DECIMAL(18,4)`.
Las migraciones empiezan en **V26**. Nada se borra: los registros se anulan o se desactivan.

### 6.1 Catálogo de venta

**`tarifa_impuesto`**: IVA 19 %, IVA 5 %, exento, excluido (configurable por empresa).
`id, empresa_id, codigo, nombre, porcentaje, activo`.

**Cambios en `producto`:**
- `codigo_barras VARCHAR(50) NULL` (único por empresa cuando existe) para el lector.
- `tarifa_impuesto_id` (impuesto de venta).
- `vendible BOOLEAN` (hay productos que solo se consumen internamente).
- `precio_incluye_impuesto BOOLEAN` (en mostrador el precio suele incluir IVA; ver decisión D2).
- `costo_promedio DECIMAL(18,4)` (fase F8: lo actualiza la recepción).

**`lista_precio`**: "General" (obligatoria), "Mayorista", "Taller aliado"…
`id, empresa_id, nombre, es_general, activo`.

**`precio_producto`** (historial de precios, nunca se sobrescribe):
`id, empresa_id, lista_precio_id, producto_id, unidad_medida_id, precio, precio_minimo, vigente_desde, vigente_hasta,
creado_por_usuario_id, motivo`.
- Cambiar un precio **cierra** la vigencia del anterior y crea uno nuevo. Así se sabe qué precio tenía cada día.
- `precio_minimo` es el piso: vender por debajo exige autorización de supervisor.
- Permite precio por caja y por unidad del mismo producto (usa la conversión de unidades que ya existe).

### 6.2 Clientes

**`cliente`**: `id, empresa_id, tipo_documento (CC, NIT, CE, PAS), numero_documento, nombre, telefono, correo,
direccion, lista_precio_id, cupo_credito, plazo_dias, activo, acepta_datos_en, creado_en`.
- Único por empresa: `(tipo_documento, numero_documento)`.
- Existe siempre un cliente **"Consumidor final"** por empresa (ventas rápidas sin datos).
- `acepta_datos_en`: fecha de la autorización de tratamiento de datos (Ley 1581 de 2012, habeas data).

---

## 9. Seguridad

- **Aislamiento:** `@TenantId` en todas las tablas nuevas; pruebas de fuga entre empresas por cada endpoint (A pide un
  dato de B ⇒ 404).
- **Permisos:** `@RequiereModulo` + `@RequiereRol` en cada endpoint; las reglas de "nadie autoriza lo suyo" en el Service.
- **PIN de supervisor:** BCrypt, 6 dígitos, bloqueo tras 5 fallos (15 min), auditado, nunca viaja en la URL.
- **Datos de tarjeta:** solo últimos 4 dígitos y número de autorización (fuera del alcance de PCI DSS).
- **Datos personales de clientes:** mínimo necesario, fecha de la autorización de tratamiento de datos, exportación
  solo para roles con permiso. El consumidor final no pide datos.
- **Rate limit** en las rutas de autorización con PIN (además del que ya existe en el login).
- **Auditoría** de: venta, anulación, devolución, descuento autorizado, cambio de precio, apertura y cierre de turno,
  aprobación de diferencia, retiro, gasto, reimpresión, cambio de cupo de crédito.
- **Concurrencia:** `@Version` en `turno_caja` y `venta`; bloqueo pesimista del stock al vender.
- **Validación:** cantidades > 0, máximo de líneas por venta (ej. 300), máximo de descuento 100 %, montos con 2 decimales.

---

## 10. API de esta parte


| Método y ruta | Rol | Qué hace |
|---|---|---|
| `GET /api/ventas/productos?q=` | CAJERO, VENDEDOR, SUPERVISOR | Busca por código, código de barras o nombre; trae precio vigente y stock de la bodega de la caja |
| `GET/POST/PUT /api/precios` | ADMIN_SISTEMA | Precios por lista con historial |
| `GET/POST/PUT /api/listas-precio` | ADMIN_SISTEMA | Listas de precios |
| `GET/POST/PUT /api/impuestos` | ADMIN_SISTEMA | Tarifas de impuesto |
| `GET/POST/PUT /api/clientes` | CAJERO, VENDEDOR, SUPERVISOR | Clientes |

**Autorización de un solo uso:** el supervisor escribe su PIN y el backend devuelve un token corto (2 minutos, una sola
acción, ligado al turno y a la venta). La acción del cajero lo consume. Así el PIN no viaja en cada petición y no se
puede reutilizar.

---

## 11. Pantallas de esta parte


| Pantalla | Para quién | Lo principal |
|---|---|---|
| **Clientes** | Cajero, vendedor | Búsqueda por documento, creación rápida desde el POS |
| **Precios y listas** | Dueño | Precio por lista, historial, cambio masivo por Excel (reutiliza la carga masiva) |

Reglas de siempre: la UI nunca calcula dinero ni stock; tipos generados del Swagger; pruebas Vitest por pantalla.

---

## 13. Pruebas

| Nivel | Qué cubre |
|---|---|
| **Unitarias** (JUnit + Mockito) | Cálculo de totales, impuestos (precio con y sin IVA incluido), descuentos, redondeo, cambio, esperado de caja, reglas de autorización |
| **Integración con MySQL real** (`supply_core_test`) | Venta completa en una transacción; idempotencia; rechazo por stock; anulación y devolución reintegran stock y caja |
| **Fuga entre empresas** (`AislamientoEmpresasTest`) | Una prueba por cada tabla y endpoint nuevo |
| **Concurrencia** | Dos ventas simultáneas del último producto: una pasa y la otra recibe `STOCK_INSUFICIENTE`; el stock nunca queda negativo |
| **Propiedades de dinero** | Para muchas ventas aleatorias: total = Σ líneas; Σ pagos − cambio = total; caja esperada = Σ movimientos |
| **Frontend** (Vitest) | POS, cobro, tiquete, cierre ciego (no muestra el esperado antes de guardar), autorización |
| **De punta a punta** (Playwright, fase F10) | Abrir turno → vender → anular con PIN → cerrar turno, contra el stack de Docker |

**Meta de cobertura** en los Services de ventas y caja: 90 % de líneas (medida con JaCoCo en CI).

---

## 14. Integración continua (GitHub Actions)

Se construye **primero** (fase F0), para que todo lo demás nazca probado.

**`.github/workflows/ci.yml`**: corre en cada PR hacia `develop` y `main`, y en cada push a esas ramas.

| Trabajo | Pasos |
|---|---|
| `backend` | Java 21 (Temurin) con caché de Maven → servicio MySQL 8 con `supply_core_test` → `./mvnw verify` (pruebas, incluida la de fuga entre empresas) → Javadoc con `-Ddoclint=all` → reporte JaCoCo como artefacto |
| `frontend` | Node 22 con caché de npm → `npm ci` → `npm test -- --watch=false` → `npm run build` (producción) |
| `docker` | Construye las imágenes `api` y `web` (sin publicarlas) → escaneo con Trivy (falla con vulnerabilidades CRÍTICAS) |
| `e2e` (F10) | `docker compose up` con un `.env` de prueba generado en el job → siembra la demo → Playwright |

**Seguridad del CI:**
- Sin secretos reales: las claves de prueba se generan dentro del job. Gemini y Claude no se usan en CI (`IA_PROVEEDOR=ninguno`).
- `permissions: contents: read` por defecto.
- Acciones fijadas por versión.
- **Dependency Review** en cada PR para bloquear dependencias con vulnerabilidades conocidas.
- **CodeQL** semanal para Java y TypeScript.

**Protección de ramas** (la configura el dueño del repositorio en GitHub): `develop` y `main` exigen que pasen
`backend`, `frontend` y `docker` antes de mezclar, y al menos una revisión.

---

## 15. Fases de esta parte


| Fase | Rama | Entrega | Listo cuando |
|---|---|---|---|
| **F0** | `feature/ci-github-actions` | CI de backend, frontend y Docker; Dependency Review; CodeQL | Un PR de prueba muestra los 3 trabajos en verde |
| **F1** | `feature/ventas-catalogo` | Módulos `VENTAS`, `CAJA` y `CARTERA` en planes; roles nuevos; impuestos; código de barras; listas y precios con historial; pantalla de precios | Precio vigente por lista y por unidad; historial; pruebas de fuga |
| **F2** | `feature/ventas-clientes` | Clientes, consumidor final, autorización de datos | CRUD con validación de documento y fuga |

Cada fase deja: código, pruebas (incluida la de fuga), Javadoc completo, tipos del frontend regenerados, `DOCKER.md` o la
skill actualizados si cambia algo de operación, y la siembra de la demo ampliada para poder probarla.

---

## 16. Decisiones de esta parte


| # | Pregunta | Recomendación |
|---|---|---|
| D2 | ¿El precio de mostrador incluye IVA? | **Sí**, como es costumbre en Colombia; Java separa base e impuesto. |
| D5 | ¿Roles nuevos `SUPERVISOR_VENTAS` y `VENDEDOR`? | **Sí**; el dueño puede asignar los dos a la misma persona en negocios pequeños. |
| D8 | ¿Quién fija precios: solo el dueño o también el jefe de compras? | El dueño, con opción de delegarlo a `ADMIN_COMPRAS`. |

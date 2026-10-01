# Supply-Core — Guía del Backend (explicada bien fácil)

Este documento es la guía de referencia del backend de Supply-Core. Está escrito para que cualquiera del equipo, incluso si nunca ha tocado este proyecto, pueda leerlo de arriba a abajo y entender **cómo está organizado el código, por qué está organizado así, y qué le toca a cada módulo**.

No hace falta leer otro documento antes que este. Si algo no está claro, es un bug de este README — avisa para corregirlo.

---

## 1. ¿Qué estamos construyendo, en una frase?

Una aplicación que ayuda a una empresa a comprar cosas de forma ordenada: desde "se nos está acabando el papel" hasta "ya llegó el papel y quedó guardado en la bodega correcta", pasando por pedir cotizaciones a varios proveedores y que una IA ayude a comparar cuál cotización conviene más.

La regla de oro que **nunca** se rompe en este proyecto:

> **La IA explica. El backend en Java calcula y valida. La persona decide.**
> La IA jamás calcula plata ni elige un proveedor por su cuenta. Solo lee documentos, ordena información y explica en palabras por qué una oferta se ve mejor que otra.

---

## 2. La idea de las "cajitas" (cómo se organiza TODO el código)

Imagina que cada módulo del negocio (inventario, proveedores, usuarios, compras...) es una **caja grande**, y adentro de cada caja grande siempre metemos las mismas 5 cajitas pequeñas, en el mismo orden. No importa qué caja grande sea — todas se ven igual por dentro.

```
inventario/                    ← la caja grande (el módulo)
├── controller/                ← cajita 1: la puerta
│   └── ProductoController.java
├── service/                    ← cajita 2: el cerebro
│   └── ProductoService.java
├── repository/                 ← cajita 3: el mensajero
│   └── ProductoRepository.java
├── entity/                     ← cajita 4: el espejo de la tabla
│   └── Producto.java
└── dto/                        ← cajita 5: el disfraz
    └── ProductoDTO.java
```

Y así, exactamente igual, para **cada** módulo: `proveedor/`, `usuario/`, `compra/rfq/`, etc. Nunca hay una carpeta `controller` gigante en la raíz del proyecto con los controllers de todo mezclados — cada módulo tiene los suyos, aislados.

### ¿Qué hace cada cajita, en palabras simples?

| Cajita | Su trabajo | Analogía |
|---|---|---|
| **Controller** | Es la puerta de entrada. Aquí llega la petición desde Angular ("quiero ver los productos"). No piensa, no calcula, solo recibe y le pasa el trabajo al Service. | El mesero de un restaurante: toma el pedido, pero no cocina. |
| **Service** | Es el cerebro. Aquí viven las reglas de negocio: "¿el stock puede quedar en negativo? No, entonces rechaza esta operación". Todo el "pensar" pasa aquí. | El chef: decide cómo se hace la comida, con qué reglas y qué pasa si falta un ingrediente. |
| **Repository** | Es el mensajero a la base de datos. Solo sabe guardar, buscar, actualizar, borrar. No piensa, solo va y trae. | El mesero yendo a la bodega del restaurante a traer un ingrediente — no opina si se debe usar. |
| **Entity** | Es el espejo de una tabla de MySQL. Una clase Java que representa exactamente cómo se ve la tabla `producto` en la base de datos. | El plano exacto de la caja tal como está en la bodega. |
| **DTO** (Data Transfer Object) | Es el "disfraz para salir a la calle". Es lo que le devolvemos a Angular — **nunca** se manda la Entity directa, porque puede tener campos internos que no se deben mostrar (como `hash_password`). | La comida ya emplatada que sale a la mesa, sin mostrar cómo quedó la cocina por dentro. |

### El camino que sigue una petición

```
Angular  →  Controller  →  Service  →  Repository  →  Base de datos (MySQL)
                                ↓
                              DTO  (lo único que sale hacia Angular)
```

**Regla práctica:** un Controller nunca debe usar un Repository directamente, y un Repository nunca debe tener reglas de negocio ("si tal, entonces cual"). Si te encuentras escribiendo un `if` de negocio dentro de un Controller o un Repository, ese código está en la cajita equivocada — muévelo al Service.

---

## 3. Los módulos del proyecto (las "cajas grandes")

Cada módulo vive en su propio paquete Java, con la estructura de 5 cajitas de arriba. Esta es la lista completa, en el orden en que normalmente se construyen (ver Roadmap más abajo):

```
src/main/java/com/proyecto/supply_core/
├── security/          → login, JWT, roles y permisos
├── usuario/            → usuarios internos y sus roles
├── empresa/             → empresa, sedes, centros de costo, políticas de aprobación
├── catalogo/            → categorías de producto, unidades de medida
├── inventario/          → productos, bodegas, stock, movimientos
├── proveedor/           → proveedores y su evaluación de desempeño
├── compra/
│   ├── solicitud/       → solicitudes de compra y su aprobación
│   ├── rfq/             → solicitud de cotización a proveedores
│   ├── cotizacion/      → lo que responde cada proveedor
│   ├── analisis/        → criterios, pesos y el resultado de la IA
│   ├── adjudicacion/    → la decisión final de a quién se le compra
│   └── orden/           → la orden de compra formal
├── recepcion/           → lo que llega físicamente a la bodega
├── pago/                → registro del pago de una orden
├── documento/           → archivos subidos (PDF, Excel, imágenes, comprobantes)
├── notificacion/        → avisos a los usuarios
├── auditoria/           → quién hizo qué y cuándo
└── ia/                  → la conexión con el servicio de inteligencia artificial
```

**Por qué `compra` tiene subcarpetas en vez de ser un módulo plano:** el ciclo de compra tiene 6 etapas bien diferenciadas (solicitud → rfq → cotización → análisis → adjudicación → orden), cada una con su propio estado y sus propias reglas. Meterlas todas en un solo módulo `compra/` sería como meter cocina, caja registradora y bodega del restaurante en un solo cuarto — funciona, pero se vuelve un desorden rápido.

---

## 4. Qué tabla de la base de datos le pertenece a cada módulo

Ya tenemos el script de base de datos (`base-de-datos.sql` / `modelo-datos-abastecimiento.sql`, en la raíz del repo, un nivel arriba de este backend). Aquí está el mapeo exacto de **qué tabla se convierte en Entity dentro de qué módulo**, para que nadie tenga dudas de dónde va cada clase:

| Módulo (carpeta) | Tablas de la base de datos que le pertenecen |
|---|---|
| `security` + `usuario` | `usuario`, `usuario_rol`, `rol`, `permiso`, `rol_permiso` |
| `empresa` | `empresa`, `sede`, `centro_costo`, `politica_aprobacion`, `config_notificacion` |
| `catalogo` | `categoria_producto`, `unidad_medida` |
| `inventario` | `producto`, `bodega`, `stock_producto_bodega`, `movimiento_inventario` |
| `proveedor` | `proveedor`, `evaluacion_proveedor` |
| `compra.solicitud` | `solicitud_compra`, `solicitud_compra_linea`, `aprobacion_solicitud` |
| `compra.rfq` | `rfq`, `rfq_linea`, `rfq_proveedor` |
| `compra.cotizacion` | `cotizacion`, `cotizacion_linea` |
| `compra.analisis` | `criterio_evaluacion`, `config_pesos_evaluacion`, `config_pesos_evaluacion_detalle`, `analisis_cotizacion`, `analisis_cotizacion_resultado`, `analisis_cotizacion_detalle` |
| `compra.adjudicacion` | `adjudicacion` |
| `compra.orden` | `orden_compra`, `orden_compra_linea` |
| `recepcion` | `recepcion`, `recepcion_linea` |
| `pago` | `pago` |
| `documento` | `documento` (tabla polimórfica: `entidad_tipo` + `entidad_id` dicen a qué registro pertenece cada archivo) |
| `notificacion` | `notificacion` |
| `auditoria` | `auditoria` |
| `ia` | **Ninguna tabla propia.** Este módulo es un `service` que llama a la API externa de IA y llena las tablas de `compra.analisis`. No tiene Entity/Repository propios — solo Controller (opcional) y Service. |

**Nota sobre `documento`:** como su llave es polimórfica (`entidad_tipo` + `entidad_id`, por ejemplo `'COTIZACION'` + `id=45`), no se relaciona con una FK real de JPA hacia `cotizacion` o `pago`. Se maneja por código: se guarda el tipo y el id como texto/número, y quien necesite los documentos de un registro los busca por esos dos campos.

---

## 5. La base de datos: cómo está armada y cómo funciona

El script completo vive en la raíz del repo (`base-de-datos.sql` / `modelo-datos-abastecimiento.sql`). Motor **MySQL 8.0.16+** (se necesita esa versión mínima para que las restricciones `CHECK` se validen de verdad y no se ignoren en silencio), charset `utf8mb4` con `utf8mb4_unicode_ci` (para que tildes, `ñ` y símbolos no den problemas). Son **27 tablas**, todas `InnoDB` (el motor que sí soporta llaves foráneas y transacciones — obligatorio aquí porque el negocio depende de que varias tablas se actualicen juntas o ninguna se actualice).

Piensa la base de datos como una fila de fichas de dominó: cada bloque se apoya en el anterior, y ninguno se puede crear si el de atrás no existe.

```
EMPRESA ─┬─ SEDE
         ├─ USUARIO ── USUARIO_ROL ── ROL ── ROL_PERMISO ── PERMISO
         ├─ BODEGA ── STOCK_PRODUCTO_BODEGA ── PRODUCTO ── CATEGORIA_PRODUCTO / UNIDAD_MEDIDA
         ├─ CENTRO_COSTO
         ├─ POLITICA_APROBACION
         └─ CONFIG_PESOS_EVALUACION ── CONFIG_PESOS_EVALUACION_DETALLE ── CRITERIO_EVALUACION

PROVEEDOR (independiente, se conecta más adelante)

SOLICITUD_COMPRA ── SOLICITUD_COMPRA_LINEA
       │
       └── APROBACION_SOLICITUD
       │
       ▼
      RFQ ── RFQ_LINEA
       │  └── RFQ_PROVEEDOR ── PROVEEDOR
       ▼
   COTIZACION ── COTIZACION_LINEA
       │
       ▼
ANALISIS_COTIZACION ── ANALISIS_COTIZACION_RESULTADO
       │              └── ANALISIS_COTIZACION_DETALLE
       ▼
  ADJUDICACION
       ▼
  ORDEN_COMPRA ── ORDEN_COMPRA_LINEA
       │
       ├── PAGO
       ├── RECEPCION ── RECEPCION_LINEA  →  (dispara) MOVIMIENTO_INVENTARIO
       └── EVALUACION_PROVEEDOR ── PROVEEDOR.score_actual

Transversales (le "cuelgan" archivos/avisos/huella a cualquier tabla de arriba):
DOCUMENTO (entidad_tipo + entidad_id)  |  NOTIFICACION  |  AUDITORIA (entidad + entidad_id)
```

### 5.1 Los 10 bloques del script, en orden de creación

| Bloque | Tablas | Qué resuelve |
|---|---|---|
| **Núcleo y seguridad** | `empresa`, `sede`, `rol`, `permiso`, `rol_permiso`, `usuario`, `usuario_rol` | Quién existe y qué puede hacer. Todo lo demás cuelga de `empresa`, directa o indirectamente. |
| **Configuración y maestros** | `centro_costo`, `categoria_producto`, `unidad_medida`, `politica_aprobacion`, `criterio_evaluacion`, `config_pesos_evaluacion`, `config_pesos_evaluacion_detalle` | Catálogos que casi todos los demás módulos consultan. |
| **Productos e inventario** | `producto`, `bodega`, `stock_producto_bodega`, `movimiento_inventario` | Qué se compra/almacena y cuánto hay, por bodega. |
| **Proveedores** | `proveedor` | Quién nos vende. |
| **Solicitudes de compra** | `solicitud_compra`, `solicitud_compra_linea`, `aprobacion_solicitud` | El punto de partida del ciclo. |
| **RFQ y cotizaciones** | `rfq`, `rfq_linea`, `rfq_proveedor`, `cotizacion`, `cotizacion_linea` | Pedir y recibir ofertas de varios proveedores. |
| **Inteligencia y adjudicación** | `analisis_cotizacion`, `analisis_cotizacion_resultado`, `analisis_cotizacion_detalle`, `adjudicacion` | El ranking explicado por IA + la decisión humana final. |
| **Órdenes, pago y recepción** | `orden_compra`, `orden_compra_linea`, `pago`, `recepcion`, `recepcion_linea` | Formalizar la compra, pagarla y recibirla físicamente. |
| **Evaluación de proveedores** | `evaluacion_proveedor` | Qué tan bien cumplió el proveedor en una orden puntual. |
| **Auditoría, documentos y notificaciones** | `documento`, `config_notificacion`, `notificacion`, `auditoria` | Módulos transversales: archivos, avisos y bitácora. |

### 5.2 Cómo leer cada tabla (diccionario rápido)

**Núcleo y seguridad**

- `empresa` — la raíz de todo. `nit` único, `moneda_base` por defecto `COP`.
- `sede` — 1 empresa → N sedes. Tiene `latitud`/`longitud` para geolocalización (Google Maps).
- `rol` / `permiso` / `rol_permiso` — catálogo de roles y permisos, unidos por una tabla puente N:M. `rol_permiso` no tiene columna `id` propia: su llave primaria es el par `(rol_id, permiso_id)`.
- `usuario` / `usuario_rol` — usuarios internos (no proveedores). `usuario_rol` es la razón por la que un usuario puede ser Comprador **y** Aprobador a la vez: es una tabla puente N:M igual que `rol_permiso`.

**Configuración y maestros**

- `centro_costo` — a qué área se le imputa una compra.
- `categoria_producto` / `unidad_medida` — catálogos simples. `unidad_medida.factor_conversion_base` es la pieza clave que le permite al motor de comparación convertir "caja x24" y "docena" a una misma unidad antes de comparar precios.
- `politica_aprobacion` — rangos de monto (`monto_desde`/`monto_hasta`) que definen si una solicitud necesita aprobación adicional.
- `criterio_evaluacion` — catálogo de criterios de la IA (precio, calidad, entrega, garantía, historial, riesgo, condiciones). Está en tabla, no en columnas fijas, para poder agregar un criterio nuevo sin alterar el esquema.
- `config_pesos_evaluacion` + `config_pesos_evaluacion_detalle` — la matriz de pesos, versionada (`vigente_desde`/`vigente_hasta`). El detalle dice cuánto pesa (%) cada criterio en esa versión. La suma de los pesos de una misma versión debe dar 100 — **eso se valida en el Service, la tabla no lo impide por sí sola**.

**Productos e inventario**

- `producto` — catálogo, con `stock_minimo`/`stock_maximo` (para alertas de reabastecimiento) y `costo_referencia`.
- `bodega` — ubicaciones físicas de una empresa.
- `stock_producto_bodega` — la **única fuente de verdad** del stock: `(producto_id, bodega_id)` es único, y `cantidad` es el saldo actual. Nunca se edita a mano.
- `movimiento_inventario` — el historial completo. El `ENUM tipo` tiene 7 valores (`ENTRADA_COMPRA`, `SALIDA_CONSUMO`, `AJUSTE_POSITIVO`, `AJUSTE_NEGATIVO`, `DEVOLUCION_PROVEEDOR`, `RECEPCION_PARCIAL`, `TRASLADO`). Un traslado usa `bodega_origen_id` **y** `bodega_destino_id`; una entrada o salida normal solo usa uno de los dos. `stock_resultante` congela el saldo justo después de ese movimiento — así nunca hay que "recalcular hacia atrás" para auditar.

**Proveedores**

- `proveedor` — tiene su propio `hash_password` porque entra a su propio portal, separado del login interno de `usuario`. `score_actual` es un campo *rollup*: un resumen recalculado cada vez que se cierra una `evaluacion_proveedor` nueva, para no tener que promediar todo el historial cada vez que se necesita mostrar el score.

**Solicitudes de compra**

- `solicitud_compra` — cabecera con `estado` (`ENUM` de 8 valores: `BORRADOR → PENDIENTE_APROBACION → APROBADA/RECHAZADA → EN_RFQ → ADJUDICADA → CANCELADA/CERRADA`).
- `solicitud_compra_linea` — el detalle producto por producto. Tiene un `CHECK (cantidad > 0)` real a nivel de base de datos — MySQL rechaza el insert si alguien intenta colar una cantidad cero o negativa, incluso si el Service tuviera un bug.
- `aprobacion_solicitud` — el rastro de auditoría de la decisión puntual (`APROBADA`/`RECHAZADA`/`DEVUELTA`), con qué política se aplicó.

**RFQ y cotizaciones**

- `rfq` — se crea a partir de una `solicitud_compra` ya aprobada. `estado`: `ABIERTA → CERRADA/ADJUDICADA/CANCELADA`.
- `rfq_linea` — qué se pide cotizar.
- `rfq_proveedor` — tabla puente N:M: qué proveedores fueron invitados a esa RFQ.
- `cotizacion` — la respuesta de un proveedor. `documento_original_id` guarda el archivo tal cual llegó (Excel/PDF/imagen), **sin importar** lo que la IA extraiga después — así siempre se puede volver al original. `estado`: `BORRADOR → ENVIADA → EN_ANALISIS → SELECCIONADA/NO_SELECCIONADA/RECHAZADA`.
- `cotizacion_linea` — el detalle de la oferta. `confianza_extraccion` (0.000–1.000) es el dato que entrega la IA sobre qué tan segura está de haber leído bien ese campo — es la columna que dispara la validación manual obligatoria cuando la confianza es baja.

**Inteligencia y adjudicación**

- `analisis_cotizacion` — cabecera de un análisis para una RFQ. Guarda `config_pesos_evaluacion_id`: el *snapshot* exacto de pesos que usó (ver sección 8).
- `analisis_cotizacion_resultado` — score final y `posicion_ranking` por cotización. Es lo que se pinta directo en pantalla.
- `analisis_cotizacion_detalle` — el desglose por criterio (`peso_aplicado`, `puntaje` 0-100, `score_ponderado`). Es lo que permite explicar "por qué" quedó primera una oferta, criterio por criterio.
- `adjudicacion` — la decisión final: qué `cotizacion_seleccionada_id`, qué `usuario_id` decidió, y `sigue_recomendacion` (booleano) + `justificacion` (obligatoria a nivel de aplicación cuando `sigue_recomendacion = 0`).

**Órdenes, pago y recepción**

- `orden_compra` — `version` se incrementa si hay que modificar la orden después de emitida, en vez de sobrescribirla (nunca se pierde qué se acordó originalmente). `estado` tiene 8 valores, desde `EMITIDA` hasta `CERRADA`/`CANCELADA`.
- `orden_compra_linea` — producto, cantidad, precio pactado, subtotal.
- `pago` — `estado` (`PENDIENTE`/`REGISTRADO`) + `comprobante_documento_id`. No es una pasarela de pagos, solo un registro de que ya se pagó por otro medio.
- `recepcion` — encabezado de lo que llegó físicamente a una `bodega`.
- `recepcion_linea` — cantidad recibida vs. lo pedido en `orden_compra_linea`, con `cantidad_faltante` y `cantidad_rechazada`. **Esta es la tabla que dispara la creación de un `movimiento_inventario`** — es el único camino por el que una compra aumenta el stock.

**Evaluación de proveedores**

- `evaluacion_proveedor` — un registro por cada orden cerrada con un proveedor, con 7 indicadores (`puntualidad`, `cumplimiento_cantidad`, `incidencias`, `calidad`, `precio`, `respuesta`, `score`). Todos son `NULL`-ables a propósito: si no hay datos suficientes para calcular un indicador, se deja sin calcular en vez de inventarse un valor por defecto.

**Auditoría, documentos y notificaciones**

- `documento` — **llave polimórfica real**: `entidad_tipo` (texto: `'COTIZACION'`, `'ORDEN_COMPRA'`, `'RECEPCION'`, `'PAGO'`, `'PROVEEDOR'`, etc.) + `entidad_id`. Así una sola tabla sirve para los archivos de cualquier módulo, sin necesitar una tabla de documentos por cada uno. Tiene un índice compuesto (`idx_documento_entidad`) para que buscar "todos los archivos de la cotización 45" sea rápido.
- `config_notificacion` / `notificacion` — plantilla de qué evento dispara qué canal, y las notificaciones concretas que un usuario recibió.
- `auditoria` — la bitácora general, con la misma idea de llave polimórfica (`entidad` + `entidad_id`) que `documento`.

### 5.3 Dos decisiones de diseño que vale la pena entender

**Las FKs "diferidas" hacia `documento` (al final del script).**
`cotizacion` necesita apuntar a `documento` (el archivo original), pero `documento` también podría necesitar existir antes de que `cotizacion` exista. Para no crear una dependencia circular al momento de crear las tablas, el script crea primero `cotizacion.documento_original_id` y `pago.comprobante_documento_id` como columnas simples (sin FK), crea `documento` después, y al final agrega las llaves foráneas con `ALTER TABLE ... ADD CONSTRAINT`. En JPA esto no importa — se mapea con un `@ManyToOne` normal hacia `Documento`, Hibernate no necesita saber en qué orden se crearon las tablas.

**Tablas puente N:M sin columna `id` propia.**
`rol_permiso`, `usuario_rol` y `rfq_proveedor` usan como llave primaria el **par** de columnas (ej. `PRIMARY KEY (rol_id, permiso_id)`), no un `id` autoincremental adicional. En JPA esto se mapea con `@ManyToMany` + `@JoinTable` (Hibernate genera y administra la tabla puente solo), **excepto** si la tabla puente necesita una columna extra como `rfq_proveedor.invitado_en` — en ese caso conviene modelarla como su propia Entity con `@ManyToOne` a cada lado, en vez de un `@ManyToMany` puro.

### 5.4 Cómo se traduce esto a JPA (Entities)

- Nombre de tabla en `snake_case` (`solicitud_compra`) → nombre de clase en `PascalCase` (`SolicitudCompra`). Spring Boot con Hibernate usa por defecto la estrategia `SpringPhysicalNamingStrategy`, que ya hace esta conversión sola — **no hace falta** poner `@Table(name = "solicitud_compra")` en cada Entity si la clase se llama `SolicitudCompra`, aunque se puede dejar explícito por claridad.
- Toda columna `*_id` que sea una FK se convierte en una relación `@ManyToOne` hacia la Entity correspondiente (nunca se deja como un `Long` suelto sin relación, salvo que sea una FK de módulo cruzado que preferimos no acoplar por JPA — ver la nota de `documento` abajo).
- Los `ENUM` de MySQL (`estado`, `tipo`, `decision`, etc.) se mapean con un `enum` de Java + `@Enumerated(EnumType.STRING)` — **nunca** `EnumType.ORDINAL`, porque si algún día se reordena o se agrega un valor al enum de Java, `ORDINAL` rompería silenciosamente los datos ya guardados.
- Los campos `activo TINYINT(1)` se mapean como `boolean`.
- Las columnas `DECIMAL(18,4)` de cantidades y `DECIMAL(18,2)` de dinero se mapean como `BigDecimal` — **nunca** `double`/`float`, porque estos pierden precisión y aquí se está calculando plata.
- `documento.entidad_tipo` + `documento.entidad_id` (y lo mismo para `auditoria`) **no se mapean como una relación JPA normal**, porque no apuntan a una sola tabla fija. Se guardan como un `String` + un `Long` planos en la Entity, y el `Service` de `documento`/`auditoria` es quien sabe reconstruir a mano a qué registro corresponden, según el valor de `entidad_tipo`.

---

## 6. El flujo completo del negocio, paso a paso

Esto es lo que el sistema debe poder demostrar de punta a punta. Cada paso corresponde a una pantalla o proceso interno concreto:

1. **Se detecta una necesidad** — el stock cae por debajo del mínimo, o alguien registra manualmente que necesita algo.
2. **Se crea la solicitud de compra** — producto, cantidad, fecha requerida, centro de costo, justificación. *(módulo `compra.solicitud`)*
3. **Se aprueba o se rechaza** — según la política de montos de la empresa. Todo queda auditado. *(`compra.solicitud` + `auditoria`)*
4. **Se crea la RFQ** — el comprador define qué se pide, la fecha límite y qué proveedores se invitan. *(`compra.rfq`)*
5. **El proveedor recibe la invitación** desde su propio portal.
6. **El proveedor cotiza** — con un formulario o subiendo un archivo (Excel, PDF, imagen). El archivo original **siempre** se conserva. *(`compra.cotizacion` + `documento`)*
7. **La IA procesa la cotización** — extrae producto, cantidad, precio, plazos, garantía, y devuelve un nivel de confianza por cada dato. *(módulo `ia`)*
8. **El backend valida y calcula** — los cálculos de dinero y las reglas de negocio corren en Java, **nunca** en el modelo de IA.
9. **Se normaliza y compara** — todo se convierte a una unidad comparable (costo por unidad) y se calcula el costo total. *(`compra.analisis`)*
10. **La IA explica el ranking** — resume ventajas, desventajas y anomalías de cada oferta.
11. **El comprador decide** — puede seguir la recomendación o elegir otra oferta, pero si se aparta debe dejar un motivo obligatorio. *(`compra.adjudicacion`)*
12. **Se aprueba la adjudicación** — si el monto lo exige, un aprobador valida.
13. **Se genera la orden de compra** — queda vinculada a la solicitud, la RFQ, la cotización y el proveedor elegido. Una vez emitida es **inmutable**: cualquier cambio crea una nueva versión, nunca se sobrescribe. *(`compra.orden`)*
14. **Se registra el pago** — método, valor, fecha, referencia, comprobante. No hay pasarela de pagos, solo un registro. *(`pago`)*
15. **El proveedor confirma y despacha** — actualiza el estado de la orden.
16. **El almacén recibe** — cantidad esperada, recibida, diferencias, evidencia fotográfica. *(`recepcion`)*
17. **El inventario se actualiza** — la recepción es el **único** evento que aumenta el stock por una compra. Una orden emitida, por sí sola, jamás mueve inventario. *(`inventario`)*
18. **Se evalúa al proveedor** — puntualidad, cumplimiento, calidad, incidencias. *(`proveedor` → `evaluacion_proveedor`)*
19. **El proceso se cierra** — todo queda consultable para futuras comparaciones y para los dashboards.

### La regla que jamás se negocia

> El stock **nunca** se edita a mano desde una pantalla. El saldo en `stock_producto_bodega` siempre es consecuencia de una fila nueva en `movimiento_inventario` (entrada, salida, ajuste, traslado o recepción). La recepción es el único evento que incrementa el stock por una compra.

Esto significa, en código: **nunca** hagas un `UPDATE` directo sobre `stock_producto_bodega` desde un Service. Siempre: 1) inserta el `movimiento_inventario`, 2) el Service recalcula y guarda el nuevo saldo, en la misma transacción.

---

## 7. Cómo funciona la IA aquí adentro (sin magia, sin misterio)

| La IA SÍ hace | La IA NUNCA hace |
|---|---|
| Leer un PDF/Excel/imagen y sacar de ahí producto, cantidad, precio, plazos, garantía | Calcular precios, subtotales, impuestos o el score final (eso es una fórmula en Java) |
| Explicar en palabras por qué una oferta quedó primera, segunda o tercera | Decidir quién gana — la decisión final siempre la firma una persona |
| Avisar si un precio se ve raro comparado con el histórico | Bloquear automáticamente una oferta por verse rara — solo la marca como alerta |

Flujo técnico real:

1. El proveedor sube el documento → se guarda en `documento` y se crea la `cotizacion` en estado `ENVIADA`.
2. Se dispara un proceso **asíncrono** (para no congelar la pantalla mientras la IA trabaja).
3. El módulo `ia` le manda el documento al proveedor de IA, pidiéndole una respuesta en un formato fijo (JSON con estructura definida — *structured output*), no texto libre.
4. Esa respuesta se valida contra el formato esperado. Cada dato trae un nivel de confianza (`confianza_extraccion` en `cotizacion_linea`).
5. Si un dato crítico (cantidad, precio, unidad) tiene confianza baja, el sistema **obliga** al comprador a confirmarlo antes de seguir.
6. El backend (Java, no la IA) calcula costo unitario, costo total y el score ponderado por criterio, usando la matriz de pesos vigente (`config_pesos_evaluacion`).
7. El resultado se guarda en `analisis_cotizacion` + `analisis_cotizacion_resultado` + `analisis_cotizacion_detalle`, junto con la explicación en texto de la IA, y se publica el ranking.

**Por qué el módulo `ia` está encapsulado detrás de una interfaz propia** (`IAClient`): si mañana cambiamos de proveedor de IA, solo se reescribe la implementación de esa interfaz. El resto del sistema — Service de análisis, Controllers, base de datos — no se entera del cambio.

---

## 8. Snapshot de pesos: por qué el histórico nunca cambia

`config_pesos_evaluacion` guarda **versiones** de la matriz de pesos (cuánto pesa precio, calidad, entrega, etc.), con `vigente_desde` / `vigente_hasta`. Cada `analisis_cotizacion` guarda **a qué versión exacta** de esa matriz hizo referencia.

Esto quiere decir: si el próximo mes cambiamos los pesos (por ejemplo, le damos más importancia a la garantía), los análisis de meses anteriores **no se alteran retroactivamente**. Es como tomarle una foto a la báscula el día que se pesó algo — aunque la báscula se recalibre después, la foto vieja sigue mostrando lo que marcaba ese día.

---

## 9. Seguridad — lo no negociable

- Contraseñas **siempre** con hash fuerte (BCrypt), nunca en texto plano. El campo se llama `hash_password` a propósito, para que quede claro que ahí nunca va la contraseña real.
- Autorización por rol y permiso. Un usuario puede tener varios roles (`usuario_rol` es una tabla N:M), y cada rol agrupa varios permisos (`rol_permiso`).
- Segregación de funciones: quien solicita no puede ser quien aprueba y registra el pago a la vez, cuando la política lo exija — esta regla se valida en el `Service`, no en la base de datos.
- Toda acción sensible (aprobar, adjudicar, emitir orden, registrar pago) queda en `auditoria`: usuario, entidad afectada, acción y fecha.
- Las llaves de la API de IA y de Google Maps viven **solo** en variables de entorno del backend. Nunca en el frontend, nunca commiteadas al repositorio.

---

## 10. Stack tecnológico

### Lo que ya está instalado en este proyecto (`pom.xml`)

| Pieza | Para qué |
|---|---|
| Java 21 + Spring Boot 4.1.1 | El lenguaje y el framework base |
| Spring Data JPA | Para que el `repository` hable con MySQL sin escribir SQL a mano |
| Spring Web MVC | Para los `controller` (endpoints REST) |
| Spring Validation | Para validar los DTOs que llegan del frontend (`@NotNull`, `@Positive`, etc.) |
| Flyway (+ `flyway-mysql`) | Migraciones versionadas de la base de datos — cada módulo va a tener sus propios scripts `V1__...sql`, `V2__...sql` |
| MySQL Connector/J | El driver para conectarse a MySQL |
| Lombok | Para no escribir a mano getters/setters/constructores en cada Entity y DTO |

### Lo que todavía falta agregar (según el proyecto avance de fase)

- **Spring Security + JWT** — para el login y los roles (Fase 1).
- **springdoc-openapi** — para generar el Swagger/OpenAPI de la API automáticamente (recomendado en la sección 9 del documento funcional).
- **Cliente de Object Storage** (S3-compatible o similar) — para guardar los archivos de `documento` fuera de la base de datos.
- **Cola de trabajos asíncronos** (puede ser algo simple como `@Async` de Spring al inicio, o una cola real como RabbitMQ más adelante) — para que el procesamiento de la IA no bloquee la pantalla del usuario.
- **Cliente HTTP hacia la API de IA elegida** — encapsulado detrás de la interfaz `IAClient`.
- **Cliente de Google Maps** — para geocodificar la dirección de los proveedores, encapsulado detrás de `MapsClient`.

---

## 11. Las capas dentro del backend (resumen visual)

```
Controller  →  Service  →  Reglas de dominio  →  Repository
      |
   DTOs / Mappers
      |
Persistencia / MySQL

Integraciones externas SIEMPRE detrás de una interfaz propia:
IAClient   |   MapsClient   |   EmailClient
```

---

## 12. Roadmap — el orden en que se construye

No es opcional saltarse el orden: cada fase depende de que la anterior ya exista. Construir RFQ antes que inventario y proveedores, por ejemplo, obliga a inventar datos falsos para probar y genera retrabajo.

| Fase | Qué se construye |
|---|---|
| 0 | Reglas de negocio, casos de uso y modelo de datos (ya hecho — este README + el script SQL) |
| 1 | Migraciones de base de datos (Flyway) + autenticación/autorización (`security`, `usuario`) |
| 2 | Productos e inventario (`catalogo`, `inventario`) |
| 3 | Proveedores y su portal (`proveedor`) |
| 4 | Solicitudes de compra y RFQ (`compra.solicitud`, `compra.rfq`) |
| 5 | Cotizaciones — formulario y carga de archivos (`compra.cotizacion`, `documento`) |
| 6 | IA y motor de comparación (`ia`, `compra.analisis`) |
| 7 | Adjudicación, orden de compra, pago y recepción (`compra.adjudicacion`, `compra.orden`, `pago`, `recepcion`) |
| 8 | Dashboards y auditoría (`auditoria`, reportes) |
| 9 | Pruebas end-to-end, seguridad, documentación y despliegue |

---

## 13. Qué NO entra en esta versión (para no perder tiempo construyéndolo)

| Tema | Por qué no |
|---|---|
| Módulo de ventas | Fuera del alcance actual; el modelo de datos queda listo para agregarlo después sin rediseñar todo |
| Multiempresa real (SaaS) | Es software a la medida para una sola empresa; el código queda modular, pero no se opera multiempresa ahora |
| Marketplace abierto de proveedores | Requiere reputación, moderación y modelo comercial que no aporta al proyecto formativo |
| Pasarela de pagos propia | Solo se registra el pago que la empresa ya hizo por otro medio |
| Facturación electrónica / DIAN | Dominio tributario aparte, fuera del alcance académico |
| Microservicios | Se construye un monolito modular; separar en microservicios sería una evolución posterior |
| Modelos de IA propios / agentes autónomos | Se usa una API de un modelo ya entrenado; no se entrena nada propio en esta versión |

---

## 14. Checklist antes de programar un módulo nuevo

Antes de crear las 5 cajitas de un módulo nuevo, confirma:

- [ ] Sabes qué tabla(s) de la base de datos le pertenecen (ver la tabla de la sección 4).
- [ ] Sabes qué caso de uso del documento funcional cubre este módulo.
- [ ] Sabes qué estados tiene la entidad principal y cómo se transiciona entre ellos (ej. `solicitud_compra.estado`).
- [ ] Sabes qué rol puede hacer qué acción sobre este módulo.
- [ ] Si el módulo toca dinero o decisiones (adjudicación, orden, pago), sabes que la fórmula/cálculo va en el `Service`, nunca en la IA ni en el frontend.
- [ ] Si el módulo toca inventario, sabes que el stock se mueve solo a través de `movimiento_inventario`, nunca con un `UPDATE` directo.

---

*Este README se actualiza a medida que el equipo avanza de fase. Si algo aquí ya no coincide con el código o la base de datos, gana el código — y hay que corregir este documento, no al revés.*

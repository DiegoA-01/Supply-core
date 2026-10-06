# Convenciones del backend

## Estructura por módulo (monolito modular)
`com.proyecto.supply_core.<modulo>/` con `controller/ service/ repository/ entity/ dto/` (+ `mapper/` si hace falta). Nunca carpetas técnicas globales. Compartido en `common/` (excepciones, respuesta de error, auditoría helper).
Flujo: Controller → Service → Repository. Controller sin lógica; Repository sin reglas; todo `if` de negocio en Service. Integraciones externas detrás de interfaz propia: `IAClient`, `MapsClient`, `EmailClient`, `StorageClient`.

## JPA
- Clase PascalCase ↔ tabla snake_case (naming por defecto de Spring); `@Table` explícito opcional.
- FK → `@ManyToOne(fetch = LAZY)`. Tablas puente sin columnas extra → `@ManyToMany`; con extras (`rfq_proveedor.invitado_en`) → Entity propia.
- Enums → `@Enumerated(EnumType.STRING)`, nunca ORDINAL; nombres idénticos a los del ENUM SQL.
- Dinero/cantidades → `BigDecimal` (nunca double); comparar con `compareTo`, redondeo `RoundingMode.HALF_UP`.
- `TINYINT(1)` → `boolean`. `JSON` → `String` (o `@JdbcTypeCode(SqlTypes.JSON)`).
- Polimórficos (`documento`, `auditoria`) → `String entidadTipo` + `Long entidadId`, sin relación JPA.
- Columnas con DEFAULT de BD (`creado_en`) → `@Column(insertable=false, updatable=false)` o asignar en `@PrePersist`.
- `ddl-auto: none`: si falta una columna, se crea migración, no se toca Hibernate.

## API
- Validaciones en el request (decisión del usuario): Bean Validation con mensajes en español (`message = "es obligatorio"`) + anotaciones propias en `<modulo>/validation/` (`@CorreoUnico`, `@RolesExistentes`, `@ProveedorConsistente` a nivel de clase con `addPropertyNode`). Los validadores reciben repositorios por constructor (Spring los crea). Todo sale como 400 `VALIDACION` con `detalles: ["campo: mensaje"]`. En el Service quedan solo reglas que dependen del estado o del id de la URL (unicidad al editar, último admin, transiciones).
- Prefijo `/api/<recurso>` en plural, kebab-case. DTOs de entrada `XxxRequest` con Bean Validation; salida `XxxResponse`. Nunca exponer Entity ni `hash_password`.
- Errores: `@RestControllerAdvice` con cuerpo `{codigo, mensaje, detalles[], timestamp}`; 400 validación, 401 sin token, 403 sin rol, 404, 409 regla de negocio/estado inválido.
- Transiciones de estado: método de Service `@Transactional` que valida estado origen, cambia, audita, notifica.
- Consecutivos (`SOL-000001`, `RFQ-…`, `OC-…`) generados en Service.

## Calidad obligatoria (pedido del usuario)
- **Javadoc en toda clase y todo método** (incluidos privados, constructores y componentes de records con `@param`). Verificar: `./mvnw -q org.apache.maven.plugins:maven-javadoc-plugin:3.11.2:javadoc -Ddoclint=all -Dshow=private` → 0 warnings.
- Listados con `@Query` + `Pageable`: validar el `sort` con `Ordenamiento.validar(pageable, CAMPOS_ORDENABLES)` (lista blanca; nunca campos sensibles).
- Parámetros de ruta con `@Positive`; errores de parámetros → 400 vía `HandlerMethodValidationException`.
- Contraseñas: `ReglasPassword` (8–72, letra + número; 72 = límite BCrypt).
- Excepciones disponibles: `NegocioException` 409, `NoEncontradoException` 404, `NoAutenticadoException` 401, `AccesoDenegadoException` 403, `ParametroInvalidoException` 400.
- Secretos: nunca en `application.yaml`; van en `application-local.yaml` (gitignored, importado con `spring.config.import`) o variables de entorno.

## Auditoría
`AuditoriaService.exito(accion, entidad, entidadId, detalle)` (une la transacción; usuario de la sesión) · `exitoDe(usuarioId, ...)` (sin sesión, p. ej. login) · `fallo(usuarioId, ...)` (REQUIRES_NEW, no lanza). Acciones/entidades en los enums de `auditoria/enums`. El detalle nunca lleva contraseñas.

## Buenas prácticas Spring Boot (skill java-springboot, aplicar siempre)
- **Estructura:** Maven + starters; paquetes por dominio (`usuario/`, `proveedor/`...), nunca por capa global.
- **Inyección:** solo por constructor, dependencias `private final`; estereotipos correctos (`@Service`, `@Repository`, `@RestController`, `@Component`).
- **Configuración:** YAML; propiedades propias con `@ConfigurationProperties` (record + `@Validated`), no `@Value` suelto (pendiente migrar `WebConfig` y `DataInitializer`). Secretos solo en `application-local.yaml`/variables de entorno.
- **Web:** API REST con DTOs (nunca entidades); Bean Validation en los requests; errores solo por `GlobalExceptionHandler`.
- **Mensajes de validación SIEMPRE explícitos en español** (`message = "..."`), también en `@PathVariable @Positive`. Los mensajes por defecto cambian según el idioma de la JVM. Verificar: `./mvnw test -DargLine="-Duser.language=en -Duser.country=US"` debe pasar igual.
- **Servicios:** toda la lógica de negocio aquí, sin estado; `@Transactional` por método (`readOnly = true` en lecturas).
- **Datos:** Spring Data JPA; `@Query` o Specifications para filtros; consultas siempre parametrizadas. Proyecciones DTO cuando un listado crezca.
- **Logging:** SLF4J `private static final Logger log = LoggerFactory.getLogger(X.class);` con mensajes parametrizados (`log.info("Usuario {} creado", id)`), nunca concatenación ni datos sensibles.
- **Pruebas** (todas locales, sin Docker):
  - Service: JUnit 5 + Mockito (reglas, estados, auditoría llamada o no).
  - Controller: `@WebMvcTest` extendiendo `WebTestBase` (`src/test/.../common/web/`): roles 401/403, validaciones 400 con "campo: mensaje", 404/405/409/415. Services y repositorios con `@MockitoBean`; token real con `token(roles...)`.
  - `@SpringBootTest` solo para el arranque completo (usa el MySQL local).
  - `@DataJpaTest` + Testcontainers: pendiente hasta tener Docker.
- **Permisos:** `@RequiereRol(...)` (alguno de esos roles) y `@UsuarioInterno` (cualquiera menos PROVEEDOR); se combinan. Patrón de catálogos: clase con `@UsuarioInterno`, escrituras con `@RequiereRol`.
- **Seguridad:** sin Spring Security por decisión del proyecto (JWT propio con jjwt, `@RequiereRol`); BCrypt costo 12.

## Documentos (archivos)
- Subir/leer siempre con `DocumentoService.subir(EntidadDocumento, entidadId, MultipartFile)` / `listar` / `descargar`. Archivos en `StorageClient` (local: `app.storage.ruta`, gitignored), metadata en `documento`. Sin borrado.
- Validación automática: no vacío, ≤ 10 MB, extensión pdf/xlsx/xls/png/jpg/jpeg/webp y firma real del contenido (`TipoArchivo`). MIME guardado = el detectado.
- Cada módulo que admita documentos crea un `@Component` que implemente `ReglaAccesoDocumentos` para su `EntidadDocumento` (ejemplo: `proveedor/service/ProveedorReglaDocumentos`). Sin regla → 409 ENTIDAD_SIN_DOCUMENTOS.

## Módulo nuevo — checklist
1. ¿Las tablas existen en V1? Si no, `V{n+1}__...sql` (ver `flyway.md`).
2. Entities + Repository + DTOs + Service (reglas de `reglas-negocio.md`) + Controller con `@RequiereRol`.
3. Auditoría en acciones sensibles. 4. Prueba del Service (reglas y estados). 5. Actualizar `estado.md`.


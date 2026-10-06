# Autenticación — JWT propio con jjwt (sin Spring Security)

Decisión del usuario (2026-10-01): firma/verificación con **jjwt 0.13.0** (`jjwt-api` compile; `jjwt-impl` y `jjwt-jackson` runtime; jjwt-jackson usa Jackson 2, convive con Jackson 3 de Boot). Filtro, roles y login son propios. BCrypt con `spring-security-crypto` (sin filtros ni autoconfiguración).

## Diseño (paquete `security/`)
- `JwtUtil` (API estable: `generar`, `validar`, `getExpiracionSegundos`): `Keys.hmacShaKeyFor` + `Jwts.SIG.HS256`, `requireIssuer("supply-core")`, reloj inyectable (constructor package-private con `java.time.Clock` para tests). `ExpiredJwtException` → 401 "La sesión expiró…"; cualquier otra `JwtException` → 401 "Token inválido". Rechaza `alg:none` y otros emisores.
- Claims: `sub` (usuario.id), `correo`, `roles` (códigos), `prov` (proveedor_id o null), `iat`, `exp` (8 h).
- Secreto: `JWT_SECRET` (variable de entorno, ≥ 32 bytes). Nunca en el repo.
- `AuthFilter extends OncePerRequestFilter`: lee `Authorization: Bearer`, valida, guarda el usuario en un `RequestContext`/atributo de request; rutas públicas: `/api/auth/login`, Swagger. Token inválido → 401 JSON.
- `@RequiereRol({"COMPRADOR", ...})` + `HandlerInterceptor` que verifica roles → 403.
- `PROVEEDOR` solo ve datos de su `proveedor_id` (validar en Service, no solo en el controller).

## Login (UC-01)
`POST /api/auth/login {correo, password}` → buscar usuario activo, `BCrypt.matches`; error genérico "Credenciales inválidas"; inactivo → "Cuenta inactiva, contacte al administrador". Éxito: actualizar `ultimo_acceso`, auditar `LOGIN` (EXITO/FALLO con ip), devolver `{token, expiraEn, usuario:{id,nombre,roles}}`.

## Admin inicial
`DataInitializer` (`CommandLineRunner`): si no existe usuario con rol ADMIN_SISTEMA, crearlo con `ADMIN_EMAIL`/`ADMIN_PASSWORD` de entorno, hasheado con BCrypt.

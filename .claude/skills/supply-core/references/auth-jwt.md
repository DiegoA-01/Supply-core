# Autenticación — JWT propio con jjwt (sin Spring Security)

Decisión del usuario (2026-10-01): firma/verificación con **jjwt 0.13.0** (`jjwt-api` compile; `jjwt-impl` y `jjwt-jackson` runtime; jjwt-jackson usa Jackson 2, convive con Jackson 3 de Boot). Filtro, roles y login son propios. BCrypt con `spring-security-crypto` (sin filtros ni autoconfiguración).

## Diseño (paquete `security/`)
- `JwtUtil` (API estable: `generar`, `validar`, `getExpiracionSegundos`): `Keys.hmacShaKeyFor` + `Jwts.SIG.HS256`, `requireIssuer("supply-core")`, reloj inyectable (constructor package-private con `java.time.Clock` para tests). `ExpiredJwtException` → 401 "La sesión expiró…"; cualquier otra `JwtException` → 401 "Token inválido". Rechaza `alg:none` y otros emisores.
- Claims: `sub` (usuario.id), `correo`, `roles` (códigos), `prov` (proveedor_id o null), `emp` (empresa del usuario, obligatorio desde V17), `iat`, `exp` (15 min desde V16).
- Secreto: `JWT_SECRET` (variable de entorno, ≥ 32 bytes). Nunca en el repo.
- `AuthFilter extends OncePerRequestFilter`: lee `Authorization: Bearer`, valida, guarda el usuario en un `RequestContext`/atributo de request; rutas públicas: `/api/auth/login`, Swagger. Token inválido → 401 JSON.
- `@RequiereRol({"COMPRADOR", ...})` + `HandlerInterceptor` que verifica roles → 403.
- `PROVEEDOR` solo ve datos de su `proveedor_id` (validar en Service, no solo en el controller).

## Sesión renovable (V16, 2026-10-23)
- JWT de 15 min (`app.jwt.expiracion-minutos`). `LoginResponse` trae también `refreshToken` (opaco, 256 bits, 7 días, hash SHA-256 en `refresh_token`).
- `POST /api/auth/refresh {refreshToken}` (público): canjea el token por JWT + refresh nuevos; cada uno sirve una vez. Reuso de uno ya usado/revocado ⇒ se revoca toda la familia y se audita `REFRESH_REUTILIZADO`. Revalida usuario activo y proveedor habilitado.
- `POST /api/auth/logout {refreshToken}` y `POST /api/auth/logout-todo` (con JWT) ⇒ 204, audita `LOGOUT`.
- Bloqueo: `intentos_fallidos` / `bloqueado_hasta` en `usuario`; 5 fallos ⇒ 15 min; bloqueada responde el mismo "Credenciales inválidas". `login` y `refrescar` usan `noRollbackFor = NoAutenticadoException` para que el contador y la revocación queden guardados.
- `RateLimitFilter`: 20 POST/min por IP a login, refresh y registro de proveedores ⇒ 429 `DEMASIADAS_PETICIONES` (config `app.sesion.*`).
- Empresa: el `AuthFilter` fija `EmpresaContexto` con el claim `emp`; en rutas públicas trabaja sin empresa y el login/registro la resuelven por código. Ver `saas.md`.
- Pendiente: revocar sesiones al cambiar/restablecer contraseña y al desactivar usuario (hoy el corte llega al vencer el JWT, máx. 15 min); MFA.

## Login (UC-01)
`POST /api/auth/login {empresa, correo, password}` (`empresa` = código público; el correo es único por empresa y la búsqueda del usuario ocurre dentro de ella) → buscar usuario activo, `BCrypt.matches`; error genérico "Credenciales inválidas"; inactivo → "Cuenta inactiva, contacte al administrador". Éxito: actualizar `ultimo_acceso`, auditar `LOGIN` (EXITO/FALLO con ip), devolver `{token, expiraEn, usuario:{id,nombre,roles}}`.

## Admin inicial
`DataInitializer` (`CommandLineRunner`): si no existe usuario con rol ADMIN_SISTEMA, crearlo con `ADMIN_EMAIL`/`ADMIN_PASSWORD` de entorno, hasheado con BCrypt.

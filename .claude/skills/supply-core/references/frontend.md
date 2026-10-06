# Frontend (Angular)

Estado: `frontend/` vacío. Cuando se cree, registrar aquí versión, comandos y estructura real.

## Lineamientos acordados
- Angular con componentes standalone y formularios reactivos.
- Estructura por funcionalidad: `core/` (auth service, interceptor del token, guards), `shared/`, `features/<modulo>/`.
- Interceptor agrega `Authorization: Bearer`; 401 ⇒ logout y login.
- Guard por rol con los códigos de `rol`; menú generado según roles.
- La UI nunca calcula dinero ni stock: muestra lo que devuelve el backend.
- Portal del proveedor: rutas separadas para rol PROVEEDOR.
- URL del backend en `environments`; ninguna clave (IA, Maps) en el frontend.

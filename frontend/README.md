# Supply-Core · Frontend

Angular 22 (componentes standalone, señales, formularios reactivos) con Angular Material 22 para diálogos, menús y avisos. El aspecto sigue el mockup de 10 vistas (naranja `#d93a0b`, barra lateral oscura).

## Comandos

```bash
npm install          # primera vez
npm start            # http://localhost:4200 (pasa /api al backend en :8080 con proxy.conf.json)
npm test             # pruebas unitarias (Vitest)
npm run build        # compilación de producción en dist/
npm run api:tipos    # regenera src/app/core/api/esquema.ts desde el Swagger del backend (debe estar corriendo)
```

Para probar con datos reales, levanta el backend con el perfil `demo` (dos empresas, todos los roles y compras completas).

## Estructura

| Carpeta | Qué hay |
|---|---|
| `core/api` | `esquema.ts` (generado, no se edita) y `modelos.ts` (nombres cortos de los tipos) |
| `core/sesion` | Sesión de la empresa (JWT en memoria, refresh en `sessionStorage`), sesión del dueño (MFA) y guards |
| `core/http` | Interceptor que pone el token y renueva la sesión una vez si vence; helpers de URL y errores |
| `core/servicios` | Un servicio por área: única parte que llama a la API |
| `core/formato` | Formato es-CO (pesos, cantidades, fechas) y textos legibles de los estados |
| `core/navegacion` | Menú lateral con los roles y módulos que exige el backend |
| `layout` | Marcos de las tres zonas: `/app` (empresa), `/portal` (proveedor), `/plataforma` (dueño) |
| `features` | Una carpeta por pantalla |

## Reglas

- La pantalla no calcula dinero, stock ni puntajes: muestra lo que devuelve el backend.
- Las llamadas a la API van solo en `core/servicios`.
- Ninguna clave (IA, Maps) en el frontend.
- Si el backend cambia un DTO, corre `npm run api:tipos`: el compilador marca lo que hay que ajustar.

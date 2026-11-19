# AGENTS.md

Supply-Core: plataforma web SaaS multiempresa de abastecimiento (compras, inventario, proveedores) que suma **ventas y
caja** para negocios como ferreterías, repuestos y eléctricos.

## Documentación
- Producto: `agents/product.md`
- Stack: `agents/tech-stack.md`
- Arquitectura y reglas técnicas: `agents/architecture.md`
- Specs: `agents/specs/NNN-nombre/` (índice y reserva de migraciones en `agents/specs/README.md`)
- Reglas no negociables y decisiones vigentes: `CLAUDE.md`. Estado del proyecto: `PROGRESS.md`.

## Flujo de trabajo (Spec Driven Development)
1. No escribas código sin una spec aprobada (`spec.md` con **Estado: Aprobada**).
2. Antes de programar, escribe o revisa `plan.md` (con su sección de Pruebas) y `tasks.md` en la carpeta de la spec.
   La persona revisa el plan antes de tocar código.
3. Haz una task a la vez, con sus pruebas, y márcala con `[x]` solo si las pruebas pasan.
4. Al terminar, verifica los criterios de aceptación de `spec.md` y márcalos.
5. Si algo cambia a mitad de camino, corrige primero la spec y el plan, después el código.
6. Cada spec se construye en su propia rama, sacada de `develop` actualizado. El dueño del repositorio crea el PR y hace
   el merge.
7. Antes de crear una migración, resérvala en `agents/specs/README.md` para que dos ramas no usen el mismo número.

## Comandos
- Backend: `cd backend && ./mvnw spring-boot:run` · Pruebas: `cd backend && ./mvnw test`
- Frontend: `cd frontend && npm start` (puerto 4200) · Pruebas: `cd frontend && npm test` · Tipos: `npm run api:tipos`
- Todo junto: `docker compose up -d --build` (puerto 8088). Guía: `DOCKER.md`.

## Reglas
- Toda spec debe tener pruebas unitarias, y toda tabla de negocio nueva su prueba de fuga entre empresas.
- No agregues dependencias sin preguntar.
- Código, nombres y documentación en español (así está el proyecto).
- Nunca escribas claves ni contraseñas en el repositorio ni en una spec.

# syntax=docker/dockerfile:1.7
#
# Imagen del frontend de Supply-Core (Angular 22 servido por nginx).
# nginx sirve la aplicación y reenvía /api al backend: el navegador solo habla con un origen (sin CORS).
# Se construye con el contexto en esta carpeta: docker build -t supply-core-web ./frontend

# ---------- Etapa 1: compilar ----------
FROM node:24-alpine AS build
WORKDIR /app

# Primero las dependencias: esta capa se reutiliza mientras package-lock.json no cambie.
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund

COPY . .
RUN npx ng build --configuration production

# ---------- Etapa 2: servir ----------
# Imagen de nginx que corre sin root y escucha en el 8080. Se usa la rama mainline (1.31), la que nginx
# recomienda y la que recibe primero los arreglos de seguridad; la 1.28 ya no se actualiza y la 1.30 venía
# con CVE críticas sin parchar. El CI la escanea con Trivy.
FROM nginxinc/nginx-unprivileged:1.31-alpine AS runtime

# Parches del sistema que la imagen base todavía no trae (por ejemplo brotli); luego se vuelve al usuario sin root.
USER root
RUN apk upgrade --no-cache
USER 101

COPY nginx/default.conf /etc/nginx/conf.d/default.conf
COPY nginx/seguridad.conf /etc/nginx/snippets/seguridad.conf
COPY --from=build /app/dist/supply-core-web/browser /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1:8080/healthz || exit 1

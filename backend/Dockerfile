# syntax=docker/dockerfile:1.7
#
# Imagen del backend de Supply-Core (Spring Boot 4, Java 21).
# Dos etapas: compila con Maven y deja solo un JRE con la aplicación, sin código fuente ni herramientas de build.
# Se construye con el contexto en esta carpeta: docker build -t supply-core-api ./backend

# ---------- Etapa 1: compilar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Primero solo el pom: la capa de dependencias se reutiliza mientras el pom no cambie.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Las pruebas corren en CI contra MySQL real; aquí solo se empaqueta.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -Dmaven.test.skip=true package \
 && java -Djarmode=tools -jar target/supply-core-*.jar extract --layers --launcher --destination /build/capas

# ---------- Etapa 2: ejecutar ----------
FROM eclipse-temurin:21-jre-noble AS runtime

# Zona horaria del negocio (los jobs programados y los registros usan la hora de Colombia).
ENV TZ=America/Bogota \
    LANG=C.UTF-8 \
    STORAGE_RUTA=/data/almacenamiento \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.awt.headless=true -Djava.security.egd=file:/dev/./urandom"

# Usuario sin privilegios; la carpeta de archivos subidos es lo único donde puede escribir.
RUN groupadd --system --gid 10001 app \
 && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
 && mkdir -p /data/almacenamiento \
 && chown -R app:app /data

WORKDIR /app
# Las capas van de lo que menos cambia a lo que más cambia: una recompilación solo reemplaza la última.
COPY --from=build --chown=app:app /build/capas/dependencies/ ./
COPY --from=build --chown=app:app /build/capas/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/capas/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/capas/application/ ./

USER app
VOLUME ["/data/almacenamiento"]
EXPOSE 8080

# Listo = la base responde y las migraciones terminaron. Flyway corre antes de abrir el puerto, así que la
# primera vez puede tardar; start-period evita marcar la imagen como caída mientras migra.
HEALTHCHECK --interval=15s --timeout=5s --start-period=120s --retries=5 \
  CMD curl -fsS http://localhost:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

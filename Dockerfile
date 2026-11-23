# syntax=docker/dockerfile:1
# =====================================================================
# Dockerfile - Backend Credito de Proximidad (UPC, Grupo 5)
#
# Etapa 1: build con Maven + JDK 21 (empaqueta el jar ejecutable)
# Etapa 2: imagen de runtime solo con JRE 21 (sin codigo fuente ni SDK)
#
# Flyway corre automaticamente al arrancar la aplicacion y crea las
# 9 tablas del capitulo 8 en la base de datos (V1__init_schema.sql).
# =====================================================================

# ---------------------------------------------------------------------
# Etapa de compilacion
# ---------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# 1) Dependencias primero: se cachea la capa mientras el codigo no cambie
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

# 2) Codigo fuente y empaquetado
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------------------------------------------------------------------
# Etapa de ejecucion
# ---------------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl solo para el healthcheck del contenedor
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && addgroup --system app \
    && adduser --system app --ingroup app

COPY --from=build /workspace/target/*.jar app.jar

USER app

# Puerto del backend (el frontend consume http://localhost:8081/api/v1)
EXPOSE 8081

# Variables inyectadas normalmente desde docker-compose.yml
ENV SERVER_PORT=8081 \
    DB_URL=jdbc:postgresql://postgres:5432/credito_proximidad \
    DB_USER=credito \
    DB_PASSWORD=credito

# Comprueba que la aplicacion responda en /api/v1/actuator/health
HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=6 \
  CMD curl -fsS "http://127.0.0.1:${SERVER_PORT:-8081}/api/v1/actuator/health" >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]

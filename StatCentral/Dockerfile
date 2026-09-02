# syntax=docker/dockerfile:1
#
# Builds the StatCentral application tier (GatewayServer + worker peers) into
# a runnable image. Entry point is cluster.ClusterApp, which starts the whole
# 4-node cluster inside one JVM and keeps it running so it can be deployed as
# a normal long-lived Docker service alongside the database tier.

# ---- Build stage ------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY src ./src

RUN mvn -q -DskipTests dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/build/target/dependency
RUN mvn -q -DskipTests compile

# ---- Runtime stage ------------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /build/target/classes ./classes
COPY --from=build /build/target/dependency ./dependency

ENV HTTP_PORT=8080
ENV BASE_UDP_PORT=8000
ENV PGBOUNCER_HOST=pgbouncer
ENV PGBOUNCER_PORT=6432

EXPOSE 8080

CMD ["java", "-cp", "classes:dependency/*", "cluster.ClusterApp"]

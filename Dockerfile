# syntax=docker/dockerfile:1

# --- Compilar el JAR ---
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -B -q package -DskipTests

# --- Imagen de ejecución (solo JRE) ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring -g 1000 && adduser -S spring -u 1000 -G spring

COPY --from=build --chown=spring:spring /app/target/smarthome-shopper-*.jar app.jar

USER spring:spring

EXPOSE 8080

ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -XX:+UseContainerSupport -jar /app/app.jar"]

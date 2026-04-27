# --- BUILD STAGE (compila el proyecto) ---
    FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

    WORKDIR /app
    
    COPY pom.xml .
    COPY src ./src
    
    RUN mvn clean package -DskipTests
    
    # --- RUN STAGE (solo ejecución) ---
    FROM eclipse-temurin:21-jre-alpine
    
    WORKDIR /app
    
    # Copia el JAR generado (más seguro con wildcard)
    COPY --from=build /app/target/*.jar app.jar
    
    EXPOSE 8080
    
    ENTRYPOINT ["java","-jar","/app/app.jar"]
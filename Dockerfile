FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY backend/target/leap-backend-1.0.0.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]

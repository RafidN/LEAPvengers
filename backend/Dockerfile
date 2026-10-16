# Backend API (Spring Boot). Build context: backend/
#   docker build -t leap-backend backend
# Tests run in the pipeline before images are built, so the image build skips them.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
# Dependencies first, so they stay cached until pom.xml changes
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q package -DskipTests && cp target/*.jar /app.jar

FROM eclipse-temurin:21-jre-alpine
# Quote times are stored without a zone and compared with the server clock, so every container runs in UTC
ENV TZ=UTC \
    SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
RUN addgroup -S leap && adduser -S leap -G leap
WORKDIR /app
COPY --from=build /app.jar app.jar
USER leap
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:8081/api/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

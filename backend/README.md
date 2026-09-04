# LEAPvengers Backend

Minimal Spring Boot backend for the LEAPvengers project.

## Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

## Project Structure

\\
backend/
+-- src/main/java/com/neueda/leap/
   +-- LeapBackendApplication.java
   +-- controller/         (REST endpoints)
   +-- service/            (Business logic)
   +-- model/              (Data models)
   +-- exception/          (Error handling)
+-- src/main/resources/
   +-- application.properties
+-- pom.xml
\\

## Getting Started

### Build

    mvn clean install

### Run

    mvn spring-boot:run

Application runs on http://localhost:8081/api

Try http://localhost:8081/api/health

## Creating Features

1. Create model in model/ (Example.java as template)
2. Create service in service/ (ExampleService.java as template)
3. Create controller in controller/ (ExampleController.java as template)

Delete the Example files when done.

## License

Proprietary - LEAPvengers Project

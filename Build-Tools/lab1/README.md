# Lab 3: Containerization with Java and Docker

## 1. Overview
This lab demonstrates how to containerize a Java-based Calculator application using Docker with two different build strategies.

---

## 2. Part 1: Build Inside Docker (app1)
- **Concept:** The source code is copied inside the container, and Maven builds the application during `docker build`.
- **Dockerfile:**
```dockerfile
FROM maven:3.9.6-eclipse-temurin-17 AS builder
RUN useradd -ms /bin/bash appuser
WORKDIR /app
RUN chown -R appuser:appuser /app
USER appuser
COPY --chown=appuser:appuser . .
RUN mvn clean package -DskipTests
CMD ["java", "-jar", "target/calculator.jar"]

    Commands:

Bash

docker build -t app1 .
docker run -it --name container1 app1

3. Part 2: Build Locally (app2)

    Concept: The application is built locally using Maven, and only the lightweight .jar file and JRE runtime are added to the Docker image.

    Local Build: mvn clean package -DskipTests

    Dockerfile:

Dockerfile

FROM eclipse-temurin:17-jre
RUN useradd -ms /bin/bash appuser
WORKDIR /app
COPY target/calculator.jar app.jar
USER appuser
CMD ["java", "-jar", "app.jar"]

    Commands:

Bash

docker build -t app2 .
docker run -it --name container2 app2

4. Bonus: Image Size Comparison & Analysis
Comparison Summary:

    Image app1 (Built inside Docker with Maven/JDK):

        Total Disk Usage: 770 MB

        Content Size: 239 MB

    Image app2 (Built locally with JRE Runtime only):

        Total Disk Usage: 427 MB

        Content Size: 110 MB


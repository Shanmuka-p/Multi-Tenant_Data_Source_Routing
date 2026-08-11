# Stage 1: Build application with Maven
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Copy root POM and project modules
COPY pom.xml .
COPY multitenancy-spring-boot-starter ./multitenancy-spring-boot-starter
COPY demo-application ./demo-application

# Build executable JAR file
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Install curl for healthcheck requirement
RUN apk add --no-cache curl

# Copy compiled JAR artifact from build stage
COPY --from=build /build/demo-application/target/demo-application-*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

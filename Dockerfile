# Multi-stage Dockerfile for Spring Boot App (Java 21)

# ===== Builder stage =====
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Copy Maven wrapper and pom to leverage layer caching
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Ensure wrapper is executable and warm up dependency cache
RUN chmod +x mvnw \
    && ./mvnw -q -DskipTests dependency:go-offline

# Copy source and build
COPY src src
RUN ./mvnw -q -DskipTests package

# ===== Runtime stage =====
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Copy jar from builder FIRST (as root)
COPY --from=builder /app/target/StudyMode-0.0.1-SNAPSHOT.jar app.jar

# Non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring

# Change ownership
RUN chown spring:spring app.jar

# Switch to non-root user
USER spring:spring

# App Runner listens on the container's exposed port
EXPOSE 8080

# Set default Java options for App Runner
ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# Start the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
# =============================================================
# Stage 1 — BUILD
# Use the full Maven + JDK image to compile and package the app.
# This stage is discarded after the build; it never ships.
# =============================================================
FROM maven:3.9.6-eclipse-temurin-17 AS build

WORKDIR /app

# Copy the POM first and download dependencies in a separate layer.
# Because Docker caches layers, this layer is only re-downloaded
# when pom.xml changes — not on every source code change.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Now copy source and build the fat JAR.
# -DskipTests: tests run in CI, not inside the Docker build.
COPY src ./src
RUN mvn package -DskipTests -q

# =============================================================
# Stage 2 — RUNTIME
# Use a minimal JRE image — no Maven, no JDK, no source code.
# eclipse-temurin:17-jre-alpine is ~180MB vs ~600MB for the JDK image.
# =============================================================
FROM eclipse-temurin:17-jre-alpine AS runtime

# Run as a non-root user — standard security hardening for containers.
# If an attacker escapes the app, they get a restricted user, not root.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

WORKDIR /app

# Copy only the fat JAR from the build stage — nothing else
COPY --from=build /app/target/*.jar app.jar

# Document the port the app listens on (informational; docker-compose sets the real mapping)
EXPOSE 8080

# Use exec form (JSON array) not shell form — this means signals like SIGTERM
# go directly to the JVM process (PID 1), enabling graceful shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]
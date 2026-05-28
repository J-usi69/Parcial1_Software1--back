# Build Stage
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Aseguramos que mvnw tenga permisos de ejecución y corregimos posibles line endings
RUN chmod +x mvnw && sed -i 's/\r$//' mvnw
# Descargar dependencias
RUN ./mvnw dependency:go-offline -B
# Copiar código fuente
COPY src/ src/
# Compilar proyecto saltando tests
RUN ./mvnw clean package -DskipTests

# Runtime Stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Crear el directorio uploads y asignar permisos
RUN addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app/uploads && \
    chown -R spring:spring /app

USER spring:spring

# Copiar el jar compilado (usando un patrón más específico si es posible, o renombrándolo en el builder)
COPY --chown=spring:spring --from=builder /app/target/workflow-backend-*.jar app.jar

# Variables de Enteorno por defecto (A ser sobrescritas por Google Cloud Run)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:InitialRAMPercentage=50.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]

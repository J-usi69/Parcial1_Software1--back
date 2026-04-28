# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
# Aseguramos que mvnw tenga permisos de ejecución
RUN chmod +x mvnw
# Descargar dependencias (se hace por separado para cachear capas si el código cambia pero el pom no)
RUN ./mvnw dependency:go-offline -B
# Copiar código fuente
COPY src/ src/
# Compilar proyecto saltando tests para acelerar la build en la nube
RUN ./mvnw clean package -DskipTests

# Runtime Stage (Imagen Ligera)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Crear el directorio uploads y asignar permisos correctos al usuario de seguridad ANTES de cambiar a él
RUN addgroup -S spring && adduser -S spring -G spring && \
    mkdir -p /app/uploads && \
    chown -R spring:spring /app

USER spring:spring

# Copiar el jar compilado de la fase builder (asegurando permisos)
COPY --chown=spring:spring --from=builder /app/target/*.jar app.jar

# Copiar el archivo de credenciales de Firebase (asegurando permisos)
COPY --chown=spring:spring empresa-22cd0-firebase-adminsdk-fbsvc-f70f471e45.json firebase-credentials.json

# Variables de Enteorno por defecto (A ser sobrescritas por Google Cloud Run)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

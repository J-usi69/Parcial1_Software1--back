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
# Crear un usuario no-root por seguridad
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copiar el jar compilado de la fase builder
COPY --from=builder /app/target/*.jar app.jar

# Variables de Enteorno por defecto (A ser sobrescritas por Google Cloud Run)
ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]

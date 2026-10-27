# Packages the jar built by `./mvnw verify` (CI does this first); run that before `docker build`.
FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app && mkdir -p /data/uploads && chown app /data/uploads
USER app
COPY target/backend-*.jar /app.jar
ENV APP_STORAGE_ROOT=/data/uploads
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]

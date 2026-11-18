# Requiere el jar generado previamente: ./gradlew :plagiarism-service:bootJar
FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY build/libs/plagiarism-service-*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

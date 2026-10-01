FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY auth-service/pom.xml auth-service/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
COPY project-service/pom.xml project-service/pom.xml
RUN mvn -B -ntp -pl auth-service -am dependency:go-offline
COPY auth-service/src auth-service/src
RUN mvn -B -ntp -pl auth-service -am verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S sherlock && adduser -S -G sherlock -u 10001 sherlock
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-*.jar app.jar
USER 10001
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

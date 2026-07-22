# build stage
FROM maven:3.9.0-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml mvnw ./
COPY .mvn .mvn
COPY src src
RUN mvn -B -e -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY docker/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
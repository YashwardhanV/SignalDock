FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S signaldock && adduser -S signaldock -G signaldock
WORKDIR /app
COPY --from=build /workspace/target/signaldock-*.jar app.jar
USER signaldock
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

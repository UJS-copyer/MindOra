FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -s .mvn/mindora-settings.xml -gs .mvn/mindora-settings.xml -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
COPY --from=build /workspace/mindora-app/target/mindora-app-0.0.1-SNAPSHOT.jar /app/mindora-app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/mindora-app.jar"]

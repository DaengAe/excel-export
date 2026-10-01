FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q test package

FROM eclipse-temurin:21.0.12.1_1-jre@sha256:d7051a45dd955e4d5d1db4d3f4269fe13d1c6dff8cc6b7ef89fc8577b96c1982
WORKDIR /app
RUN useradd --system --create-home appuser
COPY --from=build /workspace/target/excel-export-0.0.1-SNAPSHOT.jar app.jar
RUN mkdir -p /app/exports && chown -R appuser:appuser /app
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "/app/app.jar"]

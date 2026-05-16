FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml pom.xml
COPY strands-agents-core/pom.xml strands-agents-core/pom.xml
COPY strands-agents-core/src strands-agents-core/src
COPY strands-agents-sessions/pom.xml strands-agents-sessions/pom.xml
COPY strands-agents-sessions/src strands-agents-sessions/src
COPY strands-agents-skills/pom.xml strands-agents-skills/pom.xml
COPY strands-agents-skills/src strands-agents-skills/src
COPY strands-agents-telemetry/pom.xml strands-agents-telemetry/pom.xml
COPY strands-agents-telemetry/src strands-agents-telemetry/src
COPY strands-agents-vault/pom.xml strands-agents-vault/pom.xml
COPY strands-agents-vault/src strands-agents-vault/src
COPY strands-agents-quarkus/pom.xml strands-agents-quarkus/pom.xml
COPY strands-agents-quarkus/src strands-agents-quarkus/src
RUN mvn package -DskipTests -q -pl strands-agents-quarkus -am

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/strands-agents-quarkus/target/quarkus-app/ /app/
COPY --from=build /app/skills/ /app/skills/
EXPOSE 8080
ENTRYPOINT ["java", "--enable-preview", "-jar", "quarkus-app/quarkus-run.jar"]

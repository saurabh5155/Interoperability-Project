FROM eclipse-temurin:17.0.10_7-jre-alpine AS base
WORKDIR /app
RUN addgroup -S interop && adduser -S interop -G interop

FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY dynamic-adapter-service/pom.xml dynamic-adapter-service/pom.xml
RUN mvn dependency:go-offline -pl dynamic-adapter-service -am -q
COPY common/src common/src
COPY dynamic-adapter-service/src dynamic-adapter-service/src
RUN mvn package -pl dynamic-adapter-service -am -DskipTests -q

FROM base
COPY --from=builder /build/dynamic-adapter-service/target/*.jar app.jar
RUN chown interop:interop app.jar
USER interop
EXPOSE 8084
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

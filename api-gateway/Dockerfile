FROM eclipse-temurin:17.0.10_7-jre-alpine AS base
WORKDIR /app
RUN addgroup -S interop && adduser -S interop -G interop

FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build
COPY pom.xml ./
COPY common/pom.xml common/pom.xml
COPY api-gateway/pom.xml api-gateway/pom.xml
RUN mvn dependency:go-offline -pl api-gateway -am -q
COPY common/src common/src
COPY api-gateway/src api-gateway/src
RUN mvn package -pl api-gateway -am -DskipTests -q

FROM base
COPY --from=builder /build/api-gateway/target/*.jar app.jar
RUN chown interop:interop app.jar
USER interop
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]

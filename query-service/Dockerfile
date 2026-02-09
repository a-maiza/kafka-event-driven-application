FROM maven:3-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
COPY common/pom.xml common/
COPY order-service/pom.xml order-service/
COPY payment-service/pom.xml payment-service/
COPY inventory-service/pom.xml inventory-service/
COPY status-service/pom.xml status-service/
COPY query-service/pom.xml query-service/
COPY streams-analytics-service/pom.xml streams-analytics-service/
RUN mvn dependency:go-offline -pl query-service -am -q || true
COPY common/src common/src
COPY query-service/src query-service/src
RUN mvn package -pl query-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/query-service/target/*.jar app.jar
EXPOSE 8085
ENTRYPOINT ["java", "-jar", "app.jar"]

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
RUN mvn dependency:go-offline -pl order-service -am -q || true
COPY common/src common/src
COPY order-service/src order-service/src
RUN mvn package -pl order-service -am -DskipTests -q

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/order-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

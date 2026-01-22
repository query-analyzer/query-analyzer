FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy entire project
COPY . .

# Build everything from root
RUN mvn clean install -DskipTests -pl query-analyzer-core,query-analyzer-spring-boot-starter,query-analyzer-examples/example-postgres -am

RUN ls -lh query-analyzer-examples/example-postgres/target/

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /build/query-analyzer-examples/example-postgres/target/example-postgres-1.2.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
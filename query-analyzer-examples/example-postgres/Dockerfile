FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /build

# Copy entire project
COPY . .

# Build everything from root
RUN mvn clean install -DskipTests -pl query-analyzer-core,query-analyzer-spring-boot-starter,query-analyzer-examples/example-postgres -am

# copy the boot jar to a fixed name so we don't hard-code the version here
# (target also has -sources/-javadoc jars, skip those)
RUN find query-analyzer-examples/example-postgres/target -maxdepth 1 -name 'example-postgres-*.jar' \
      ! -name '*-sources.jar' ! -name '*-javadoc.jar' -exec cp {} /build/app.jar \; \
 && ls -lh /build/app.jar

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY --from=build /build/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
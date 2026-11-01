# Build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests && cp target/gateway-*.jar app.jar

# Run
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 tokenfest
COPY --from=build /app/app.jar app.jar
USER tokenfest
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

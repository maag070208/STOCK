# Builds the API with the project's Gradle wrapper and runs the jar on a JRE, as a user without privileges.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
# Dependencies in their own layer, so a change in the sources does not download them again.
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 stockflow
COPY --from=build /app/build/libs/stockflow-api.jar app.jar
USER stockflow
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

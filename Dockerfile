FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 barofit
COPY --from=build /workspace/app/build/libs/app-*.jar app.jar
USER barofit
EXPOSE 3002
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

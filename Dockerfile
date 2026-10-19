FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY common ./common
COPY service-runtime ./service-runtime
COPY modules ./modules
COPY app ./app
COPY identity-app ./identity-app
COPY routine-app ./routine-app
ARG SERVICE_MODULE=routine-app
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon :${SERVICE_MODULE}:bootJar && \
    cp ${SERVICE_MODULE}/build/libs/app.jar /workspace/service.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 barofit
COPY --from=build /workspace/service.jar app.jar
USER barofit
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

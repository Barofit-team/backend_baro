FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY shared ./shared
COPY services ./services
COPY integration-tests ./integration-tests
ARG SERVICE_NAME=routine
RUN --mount=type=cache,target=/root/.gradle,sharing=locked \
    ./gradlew --no-daemon :services:${SERVICE_NAME}:bootJar && \
    cp services/${SERVICE_NAME}/build/libs/app.jar /workspace/service.jar

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 barofit
COPY --from=build /workspace/service.jar app.jar
COPY --chmod=755 scripts/docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
EXPOSE 8080
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

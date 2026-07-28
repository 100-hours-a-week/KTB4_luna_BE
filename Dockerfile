FROM eclipse-temurin:25-jdk-noble AS builder

WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN bash ./gradlew dependencies --no-daemon

COPY src/main ./src/main
RUN bash ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-noble AS runtime

WORKDIR /app

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
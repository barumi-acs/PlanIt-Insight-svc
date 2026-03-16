FROM gradle:8.5-jdk17 AS builder
WORKDIR /app

COPY . .

RUN ./gradlew clean build -x test --no-daemon

# ---------------------------------------------------

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app


COPY --from=builder /app/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8084 9094

ENTRYPOINT ["java", "-jar", "app.jar"]
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app

COPY manager/manager-app/target/manager-app-*.jar app.jar

EXPOSE 28477

ENV SPRING_PROFILES_ACTIVE=docker
ENV MANAGER_PORT=28477

ENTRYPOINT ["java", "-jar", "app.jar"]

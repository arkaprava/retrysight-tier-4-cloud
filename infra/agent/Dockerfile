FROM eclipse-temurin:21-jre-alpine

RUN addgroup -S collector && adduser -S collector -G collector

USER collector
WORKDIR /home/collector

RUN mkdir -p /home/collector/.agent-retry

COPY agent/agent-app/target/agent-app-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=docker
ENV AGENT_UPLOAD_BUFFER_PATH=/home/collector/.agent-retry/buffer.db

ENTRYPOINT ["java", "-jar", "app.jar"]

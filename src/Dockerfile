FROM bellsoft/liberica-openjre-alpine-musl:25

WORKDIR /app

ARG BUILD_HASH=unknown
ARG BUILD_TIME=unknown

ENV BUILD_HASH=${BUILD_HASH} \
    BUILD_TIME=${BUILD_TIME}

ADD --checksum=sha256:e43a270df53c6f423e0ab157be9c27ccc01d5dc3232e162fe54777573db2f149 https://repo1.maven.org/maven2/io/opentelemetry/javaagent/opentelemetry-javaagent/2.33.1/opentelemetry-javaagent-2.33.1.jar opentelemetry-javaagent.jar

COPY build/libs/*.jar app.jar

RUN apk update && \
    apk upgrade --no-cache && \
    apk add --no-cache curl ffmpeg && \
    rm -rf /var/cache/apk/* && \
    adduser -D -s /bin/sh appuser && \
    mkdir /recordings && \
    chown -R appuser:appuser /app /recordings

USER appuser

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -Duser.timezone=Europe/Tallinn -javaagent:/app/opentelemetry-javaagent.jar" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=80 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError" \
    OTEL_JAVAAGENT_ENABLED=false \
    SERVER_PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

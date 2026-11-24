FROM bellsoft/liberica-openjre-alpine-musl:25

WORKDIR /app

RUN apk update && \
    apk upgrade --no-cache && \
    apk add --no-cache curl ffmpeg && \
    rm -rf /var/cache/apk/* && \
    adduser -D -s /bin/sh appuser && \
    mkdir /recordings && \
    chown appuser:appuser /app /recordings

USER appuser

ENV JAVA_OPTS="-Xmx1024m -XX:+UseSerialGC -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=30 -XX:+UseCompactObjectHeaders -XX:TieredStopAtLevel=1 -Duser.timezone=Europe/Tallinn" \
    JAVA_TOOL_OPTIONS="-XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

ARG BUILD_HASH=unknown
ARG BUILD_TIME=unknown

ENV BUILD_HASH=${BUILD_HASH} \
    BUILD_TIME=${BUILD_TIME}

COPY --chown=appuser:appuser build/libs/*.jar app.jar

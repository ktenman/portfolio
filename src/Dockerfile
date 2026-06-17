FROM bellsoft/liberica-openjre-alpine-musl:21

WORKDIR /app

ARG BUILD_HASH=unknown
ARG BUILD_TIME=unknown

ENV BUILD_HASH=${BUILD_HASH} \
    BUILD_TIME=${BUILD_TIME}

COPY build/libs/*.jar app.jar

# Update packages to fix CVE-2024-58251, CVE-2025-46394, and openssl CVEs (libssl3/libcrypto3)
RUN apk update && \
    apk upgrade --no-cache busybox busybox-binsh ssl_client libcrypto3 libssl3 && \
    apk add --no-cache curl && \
    rm -rf /var/cache/apk/* && \
    adduser -D -s /bin/sh appuser && \
    chown -R appuser:appuser /app

USER appuser

ENV JAVA_OPTS="-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+UseStringDeduplication -Duser.timezone=Europe/Tallinn" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=80 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError" \
    SERVER_PORT=8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

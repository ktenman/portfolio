FROM ghcr.io/ktenman/portfolio-runtime-base:latest

ARG BUILD_HASH=unknown
ARG BUILD_TIME=unknown

ENV BUILD_HASH=${BUILD_HASH} \
    BUILD_TIME=${BUILD_TIME}

COPY build/libs/*.jar app.jar

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

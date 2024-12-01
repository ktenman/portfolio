# Stage 1: Build the application
FROM gradle:jdk21-alpine AS build
WORKDIR /app

# Copy necessary files
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY src ./src

# Build with optimizations
RUN gradle clean build -x test --no-daemon \
    --build-cache \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.parallel=true \
    -Dorg.gradle.configureondemand=true

# Stage 2: Runtime
FROM bellsoft/liberica-openjre-alpine-musl:21
WORKDIR /app

# Install curl and necessary libraries in a single layer
RUN apk update && \
    apk add --no-cache curl libressl nghttp2-libs libssh2 brotli zlib && \
    rm -rf /var/cache/apk/*

COPY --from=build /app/build/libs/*.jar app.jar

ENV JAVA_OPTS="-Xms512m -Xmx1024m -Duser.timezone=Europe/Tallinn" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=80" \
    SERVER_PORT=8080

CMD ["java", "-jar", "app.jar"]

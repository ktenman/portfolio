# Stage 1: Build the application
FROM gradle:jdk21-ubi AS build
WORKDIR /app

# src/Dockerfile - Add these near the top
ARG BUILD_HASH=unknown
ARG BUILD_TIME=unknown

# Later in the file, in the runtime stage
ENV BUILD_HASH=${BUILD_HASH}
ENV BUILD_TIME=${BUILD_TIME}

# Copy necessary files
COPY build.gradle.kts settings.gradle.kts ./
COPY .editorconfig ./
COPY gradle ./gradle
COPY src ./src

# Build with optimizations
RUN gradle clean build -x test --no-daemon \
    --build-cache \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.parallel=true \
    -Dorg.gradle.configureondemand=true

# Stage 2: Runtime - Use Alpine for reduced attack surface and newer packages
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Install Firefox and dependencies with security updates
RUN apk update && \
    apk upgrade && \
    apk add --no-cache \
        curl \
        firefox \
        ttf-liberation \
        gtk+3.0 \
        libx11 \
        libxcb \
        libxcomposite \
        libxcursor \
        libxdamage \
        libxext \
        libxfixes \
        libxi \
        libxrandr \
        libxrender \
        libxt \
        libxtst \
        dbus-x11 \
        fontconfig \
        mesa-gl \
        mesa-dri-gallium && \
    rm -rf /var/cache/apk/*

# Install geckodriver with architecture detection
RUN ARCH=$(uname -m); \
    case ${ARCH} in \
        aarch64) GECKODRIVER_ARCH="linux-aarch64" ;; \
        x86_64) GECKODRIVER_ARCH="linux64" ;; \
        *) echo "Unsupported architecture: ${ARCH}" && exit 1 ;; \
    esac && \
    GECKODRIVER_VERSION="v0.36.0" && \
    echo "Downloading geckodriver ${GECKODRIVER_VERSION} for ${GECKODRIVER_ARCH}" && \
    curl -sL "https://github.com/mozilla/geckodriver/releases/download/${GECKODRIVER_VERSION}/geckodriver-${GECKODRIVER_VERSION}-${GECKODRIVER_ARCH}.tar.gz" | tar -xz -C /usr/bin/ && \
    chmod +x /usr/bin/geckodriver && \
    # Verify installation
    geckodriver --version

# Create a non-root user (Alpine uses adduser)
RUN adduser -D -s /bin/sh appuser && \
    chown -R appuser:appuser /app

# Set environment variables
ENV JAVA_OPTS="-Xms1024m -Xmx3072m -XX:+UseG1GC -XX:+UseStringDeduplication -Duser.timezone=Europe/Tallinn" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=85 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError" \
    PATH="/usr/bin/firefox:/usr/bin/geckodriver:${PATH}" \
    SERVER_PORT=8080 \
    MOZ_HEADLESS=1 \
    MOZ_MEMORY_LIMIT=2048 \
    MOZ_DISABLE_CONTENT_SANDBOX=1 \
    GECKODRIVER_ARGS="--log trace" \
    LC_ALL=C.UTF-8

# Copy the JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Switch to non-root user
USER appuser

# Create directory for Firefox cache
RUN mkdir -p /home/appuser/.cache/mozilla

# Use exec form for proper signal handling
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]

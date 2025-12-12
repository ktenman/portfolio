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
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY .editorconfig ./
COPY detekt.yml ./
COPY gradle ./gradle
COPY src ./src

# Build with optimizations using Gradle wrapper for version consistency
RUN chmod +x gradlew && ./gradlew clean build -x test --no-daemon \
    --build-cache \
    -Dorg.gradle.caching=true \
    -Dorg.gradle.parallel=true \
    -Dorg.gradle.configureondemand=true

# Stage 2: Runtime
FROM openjdk:25-ea-21-jdk-slim-bookworm
WORKDIR /app

# Install Firefox and dependencies with optimizations
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl \
        firefox-esr \
        fonts-liberation \
        libasound2 \
        libdbus-glib-1-2 \
        libfreetype6 \
        libgtk-3-0 \
        libx11-xcb1 \
        libxcb-dri3-0 \
        libxcomposite1 \
        libxcursor1 \
        libxdamage1 \
        libxfixes3 \
        libxi6 \
        libxrandr2 \
        libxrender1 \
        libxss1 \
        libxt6 \
        libxtst6 \
        tar && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

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

# Create a non-root user
RUN useradd -m -s /bin/bash appuser && \
    chown -R appuser:appuser /app

# Set environment variables
ENV JAVA_OPTS="-Xms1024m -Xmx3072m -XX:+UseG1GC -XX:+UseStringDeduplication -Duser.timezone=Europe/Tallinn -Dwebdriver.gecko.driver=/usr/bin/geckodriver -Dwebdriver.firefox.bin=/usr/bin/firefox-esr" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=85 -XX:+UseContainerSupport -XX:+ExitOnOutOfMemoryError" \
    SE_GECKO_DRIVER="/usr/bin/geckodriver" \
    SE_FIREFOX_BINARY="/usr/bin/firefox-esr" \
    FIREFOX_BIN="/usr/bin/firefox-esr" \
    PATH="/usr/bin/firefox-esr:/usr/bin/geckodriver:${PATH}" \
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

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
FROM openjdk:21-jdk-slim-bookworm
WORKDIR /app

# Install Firefox and dependencies
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    firefox-esr \
    curl \
    tar \
    libx11-xcb1 \
    libdbus-glib-1-2 \
    libgtk-3-0 \
    libasound2 \
    libxt6 && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# Install appropriate geckodriver version based on architecture
RUN ARCH=$(uname -m); \
    case ${ARCH} in \
        aarch64) GECKODRIVER_ARCH="linux-aarch64" ;; \
        x86_64) GECKODRIVER_ARCH="linux64" ;; \
        *) echo "Unsupported architecture: ${ARCH}" && exit 1 ;; \
    esac && \
    curl -sL https://github.com/mozilla/geckodriver/releases/download/v0.35.0/geckodriver-v0.35.0-${GECKODRIVER_ARCH}.tar.gz | tar -xz -C /usr/bin/ && \
    chmod +x /usr/bin/geckodriver

# Setup Firefox profile
RUN mkdir -p /usr/lib/firefox/profiles/default && \
    mkdir -p /root/.mozilla/firefox && \
    echo '[General]\nStartWithLastProfile=1\n\n[Profile0]\nName=default\nIsRelative=0\nPath=/usr/lib/firefox/profiles/default\nDefault=1' > /root/.mozilla/firefox/profiles.ini

# Copy and extract the Firefox profile if needed
COPY src/firefox-profile.tar.gz /tmp/firefox-profile.tar.gz
RUN tar -xzf /tmp/firefox-profile.tar.gz -C /usr/lib/firefox/profiles/default && \
    rm /tmp/firefox-profile.tar.gz

# Set environment variables
ENV JAVA_OPTS="-Xms512m -Xmx1024m -Duser.timezone=Europe/Tallinn" \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=80" \
    PATH="/usr/bin/firefox:/usr/bin/geckodriver:${PATH}" \
    SERVER_PORT=8080 \
    MOZ_HEADLESS=1 \
    MOZ_HEADLESS_WIDTH=1920 \
    MOZ_HEADLESS_HEIGHT=1080 \
    FIREFOX_PROFILE_PATH="/usr/lib/firefox/profiles/default"

# Copy the JAR file from the build stage
COPY --from=build /app/build/libs/*.jar app.jar

# Use shell form to properly expand environment variables
CMD java $JAVA_OPTS -jar app.jar

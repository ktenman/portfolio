# Use Alpine Linux for minimal attack surface
# Pin to specific version for reproducibility and security
FROM python:3.13-alpine3.22 AS builder

# Install build dependencies
RUN apk add --no-cache \
    gcc \
    musl-dev \
    linux-headers \
    g++ \
    make

# Install uv
RUN pip install --no-cache-dir uv

# Set working directory
WORKDIR /app

# Copy only pyproject.toml first for better caching
COPY pyproject.toml .

# Install Python dependencies
RUN uv pip install --system '.[dev]'

# Copy application code and install
COPY . .
RUN uv pip install --system -e .

# Runtime stage - minimal Alpine image
# Pin to specific version for reproducibility and security
FROM python:3.13-alpine3.22

# Install only runtime dependencies with latest security updates
# Force update of all packages to get latest security fixes
# Explicitly upgrade busybox packages to fix CVE-2024-58251 and CVE-2025-46394
RUN apk update && \
    apk upgrade --no-cache busybox busybox-binsh ssl_client && \
    apk add --no-cache \
    firefox \
    curl \
    tzdata \
    # Required libraries for Firefox
    gtk+3.0 \
    dbus-x11 \
    ttf-freefont \
    # Additional security patches for CVEs
    libxml2 \
    libxslt \
    ffmpeg \
    # Ensure we have the patched libssh version
    && apk info -v libssh \
    && rm -rf /var/cache/apk/* \
    # Create non-root user
    && adduser -D -u 1000 appuser

# Install geckodriver
RUN wget -q "https://github.com/mozilla/geckodriver/releases/download/v0.36.0/geckodriver-v0.36.0-linux64.tar.gz" -O /tmp/geckodriver.tar.gz \
    && tar -xzf /tmp/geckodriver.tar.gz -C /usr/local/bin \
    && rm /tmp/geckodriver.tar.gz \
    && chmod +x /usr/local/bin/geckodriver

# Copy Python installation and packages from builder
COPY --from=builder /usr/local/lib/python3.13 /usr/local/lib/python3.13
COPY --from=builder /usr/local/bin /usr/local/bin

# Set working directory
WORKDIR /app

# Copy only the application code
COPY --from=builder --chown=appuser:appuser /app /app

# Set timezone
RUN cp /usr/share/zoneinfo/Europe/Tallinn /etc/localtime && \
    echo "Europe/Tallinn" > /etc/timezone

# Switch to non-root user
USER appuser

# Set environment variables
ENV HEADLESS=True
ENV PYTHONUNBUFFERED=1

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

# Run the script
CMD ["python", "main.py"]

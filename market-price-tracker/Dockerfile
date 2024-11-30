FROM python:3.9-slim

# Install necessary packages
RUN apt-get update && apt-get install -y \
    firefox-esr \
    wget \
    curl \
    tzdata \
    && rm -rf /var/lib/apt/lists/*

# Set the timezone to Europe/Tallinn
RUN ln -fs /usr/share/zoneinfo/Europe/Tallinn /etc/localtime && dpkg-reconfigure -f noninteractive tzdata

# Install geckodriver for Firefox
RUN wget -q "https://github.com/mozilla/geckodriver/releases/download/v0.35.0/geckodriver-v0.35.0-linux64.tar.gz" -O /tmp/geckodriver.tar.gz \
    && tar -xzf /tmp/geckodriver.tar.gz -C /usr/local/bin \
    && rm /tmp/geckodriver.tar.gz

# Install uv
RUN pip install uv

# Set working directory first
WORKDIR /app

# Copy only pyproject.toml first for better caching
COPY pyproject.toml .
RUN uv pip install --system '.[dev]'

# Copy application code
COPY . .
RUN uv pip install --system -e .

# Set environment variable for headless browser
ENV HEADLESS=True

# Add healthcheck
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
    CMD curl -f http://localhost:5000/health || exit 1

# Run the script
CMD ["python", "main.py"]

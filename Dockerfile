FROM python:3.9-slim

# Install necessary packages
RUN apt-get update && apt-get install -y \
    firefox-esr \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Install geckodriver for Firefox
RUN wget -q "https://github.com/mozilla/geckodriver/releases/download/v0.30.0/geckodriver-v0.30.0-linux64.tar.gz" -O /tmp/geckodriver.tar.gz \
    && tar -xzf /tmp/geckodriver.tar.gz -C /usr/local/bin \
    && rm /tmp/geckodriver.tar.gz

# Install pip packages
COPY requirements.txt .
RUN pip install -r requirements.txt

# Copy the application code
COPY . /app
WORKDIR /app

# Set environment variable for headless browser
ENV HEADLESS=True

# Run the script
CMD ["python", "price_retrieval_job.py"]

import logging
import os
import time
from flask import Flask, jsonify
from threading import Thread
from werkzeug.middleware.proxy_fix import ProxyFix
from scheduler import scheduled, scheduler
from price_fetcher import PriceFetcher

# Configure logging
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s.%(msecs)03d [%(threadName)s] %(levelname)-5s %(name)-20s %(message)s',
                    datefmt='%Y-%m-%d %H:%M:%S',
                    handlers=[logging.StreamHandler()])
logger = logging.getLogger()

BACKEND_URL = os.environ.get('BACKEND_URL', 'http://backend:8080/api/instruments')

# Create Flask app
app = Flask(__name__)
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

def price_fetcher_thread():
    fetcher = PriceFetcher(BACKEND_URL)
    fetcher.fetch_all_prices()  # Corrected method name

@scheduled(fixed_rate=30)
def fetch_current_prices():
    logger.info("Scheduling new thread for fetching current prices")
    thread = Thread(target=price_fetcher_thread)
    thread.start()
    thread.join()  # Wait for the thread to complete
    logger.info("Price fetching thread completed")

@app.route('/health')
def health_check():
    return jsonify({"status": "healthy"}), 200

class ExcludeHealthFilter(logging.Filter):
    def filter(self, record):
        return 'GET /health' not in record.getMessage()

logging.getLogger('werkzeug').addFilter(ExcludeHealthFilter())

def run_flask_app():
    app.run(host='0.0.0.0', port=5000)

def run_scheduler():
    while True:
        scheduler.run_pending()
        time.sleep(1)

if __name__ == '__main__':
    # Start Flask app in a separate thread
    flask_thread = Thread(target=run_flask_app)
    flask_thread.start()

    # Run the scheduler in the main thread
    run_scheduler()

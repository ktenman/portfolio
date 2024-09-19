import logging
import os
import time
from flask import Flask, jsonify
from threading import Thread, Event
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
FETCH_INTERVAL = int(os.environ.get('FETCH_INTERVAL', 900))  # Default to 900 seconds if not set

# Create Flask app
app = Flask(__name__)
app.wsgi_app = ProxyFix(app.wsgi_app, x_for=1, x_proto=1, x_host=1, x_prefix=1)

# Event to signal threads to stop
stop_event = Event()


def price_fetcher_thread(stop_event):
  fetcher = PriceFetcher(BACKEND_URL)
  try:
    fetcher.fetch_all_prices()
  except Exception as e:
    logger.error(f"Error in price fetcher: {e}")
  finally:
    logger.info("Price fetcher thread completed")


@scheduled(fixed_rate=FETCH_INTERVAL)
def fetch_current_prices():
  if stop_event.is_set():
    return
  logger.info(f"Scheduling new thread for fetching current prices every {FETCH_INTERVAL} seconds")
  thread = Thread(target=price_fetcher_thread, args=(stop_event,))
  thread.start()

  try:
    # Wait for the thread to complete or for the stop event
    while thread.is_alive() and not stop_event.is_set():
      thread.join(timeout=1.0)
  except Exception as e:
    logger.error(f"Error while waiting for price fetcher thread: {e}")
  finally:
    if thread.is_alive():
      logger.warning("Price fetcher thread did not complete in time. It may still be running.")
    else:
      logger.info("Price fetcher thread completed successfully")


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
  while not stop_event.is_set():
    scheduler.run_pending()
    time.sleep(1)


if __name__ == '__main__':
  # Start Flask app in a separate thread
  flask_thread = Thread(target=run_flask_app)
  flask_thread.start()

  # Run the scheduler in the main thread
  run_scheduler()

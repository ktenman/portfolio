import logging
import os
import time
from decimal import Decimal
from flask import Flask, jsonify
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, WebDriverException, InvalidSessionIdException
from threading import Thread
from werkzeug.middleware.proxy_fix import ProxyFix
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from services import InstrumentService
from scheduler import scheduled, scheduler

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

def setup_webdriver():
    firefox_options = Options()
    if os.getenv("HEADLESS", "False") == "True":
        firefox_options.add_argument("--headless")
    firefox_options.add_argument("--no-sandbox")
    firefox_options.add_argument("--disable-dev-shm-usage")
    return webdriver.Firefox(options=firefox_options)

def accept_cookies(driver):
    try:
        iframes = driver.find_elements(By.TAG_NAME, "iframe")
        logger.info(f"Found {len(iframes)} iframes")
        if len(iframes) != 4:
            return

        driver.switch_to.frame(iframes[-1])
        accept_button = WebDriverWait(driver, 10).until(
            EC.element_to_be_clickable((By.XPATH, "//button[text()='Accept Cookies']"))
        )
        accept_button.click()
        driver.switch_to.default_content()
        logger.info("Accepted cookies")
    except Exception as e:
        logger.error(f"Error accepting cookies: {e}")

@retry(stop=stop_after_attempt(3),
       wait=wait_exponential(multiplier=1, min=4, max=10),
       retry=retry_if_exception_type((TimeoutException, WebDriverException, InvalidSessionIdException)))
def fetch_price_with_retry(instrument):
    driver = setup_webdriver()
    try:
        driver.get(f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}")
        logger.info(f"Opened URL for instrument: {instrument.symbol}")

        accept_cookies(driver)

        price_element = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "mod-ui-data-list__value"))
        )

        price_text = price_element.text
        logger.info(f"Found price text for {instrument.symbol}: {price_text}")
        return Decimal(price_text.replace(",", ""))
    except (TimeoutException, WebDriverException, InvalidSessionIdException) as e:
        logger.error(f"Error fetching price for {instrument.symbol}: {str(e)}")
        raise
    finally:
        driver.quit()

def process_instrument(instrument_service):
    remote_instruments = instrument_service.fetch_instruments_from_backend()
    for instrument in remote_instruments:
        try:
            price = fetch_price_with_retry(instrument)
            instrument.current_price = price
            logger.info(f"Updating instrument {instrument.name} with price {price}")
            instrument_service.log_instrument_save(instrument)
        except Exception as e:
            logger.error(f"Error processing {instrument.name}: {str(e)}")

@scheduled(fixed_rate=900)
def fetch_current_prices():
    logger.info("Fetching current prices")
    instrument_service = InstrumentService(BACKEND_URL)
    process_instrument(instrument_service)
    logger.info("Completed fetching current prices")

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

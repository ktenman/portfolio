import logging
import os
import requests
import schedule
import time
from decimal import Decimal
from flask import Flask, jsonify
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, WebDriverException
from threading import Thread
from werkzeug.middleware.proxy_fix import ProxyFix
from tenacity import retry, stop_after_attempt, wait_exponential

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

class Instrument:
    def __init__(self, name, symbol, id=None, category=None, base_currency=None, current_price=None):
        self.name = name
        self.symbol = symbol
        self.current_price = current_price
        self.id = id
        self.category = category
        self.base_currency = base_currency

    def __str__(self):
        return f"Instrument(id={self.id}, name='{self.name}', symbol='{self.symbol}', category='{self.category}', baseCurrency='{self.base_currency}', current_price={self.current_price})"

    __repr__ = __str__

class InstrumentService:
    def log_instrument_save(self, instrument):
        logger.info(f"Saved {instrument.name} with current price: {instrument.current_price}")
        self.update_backend_instrument(instrument)

    def fetch_instruments_from_backend(self):
        try:
            response = requests.get(BACKEND_URL)
            response.raise_for_status()
            return [self._create_instrument(inst) for inst in response.json()]
        except Exception as e:
            logger.error(f"Error fetching instruments from backend: {e}")
            return []

    def _create_instrument(self, inst):
        return Instrument(
            name=inst['name'],
            symbol=inst['symbol'],
            id=inst['id'],
            category=inst.get('category'),
            base_currency=inst.get('baseCurrency'),
            current_price=Decimal(inst['currentPrice']) if inst['currentPrice'] is not None else None
        )

    def update_backend_instrument(self, instrument):
        if instrument.id is None:
            logger.error(f"Instrument ID is missing for {instrument.name}. Skipping update.")
            return

        payload = {
            "id": instrument.id,
            "symbol": instrument.symbol,
            "name": instrument.name,
            "category": instrument.category,
            "baseCurrency": instrument.base_currency,
            "currentPrice": str(instrument.current_price) if instrument.current_price is not None else None
        }

        try:
            logger.info(f"Updating {instrument.symbol} instrument with current price: {instrument.current_price}")
            response = requests.put(f"{BACKEND_URL}/{instrument.id}", json=payload)
            response.raise_for_status()
            logger.info(f"Successfully updated {instrument.symbol} instrument with current price: {instrument.current_price}")
        except requests.exceptions.RequestException as e:
            logger.error(f"Failed to update {instrument.symbol} instrument. Error: {e}")

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

@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=4, max=10))
def fetch_price_with_retry(driver, instrument):
    driver.get(f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}")
    logger.info(f"Opened URL for instrument: {instrument.symbol}")

    accept_cookies(driver)

    try:
        price_element = WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CLASS_NAME, "mod-ui-data-list__value"))
        )

        price_text = price_element.text
        logger.info(f"Found price text for {instrument.symbol}: {price_text}")
        return Decimal(price_text.replace(",", ""))
    except TimeoutException:
        logger.error(f"Timeout waiting for price element for {instrument.symbol}")
        raise
    except WebDriverException as e:
        logger.error(f"WebDriver exception for {instrument.symbol}: {str(e)}")
        raise

def process_instrument(instrument_service):
    driver = setup_webdriver()
    if driver is None:
        logger.error("Failed to set up WebDriver")
        return

    try:
        remote_instruments = instrument_service.fetch_instruments_from_backend()
        for instrument in remote_instruments:
            try:
                price = fetch_price_with_retry(driver, instrument)
                instrument.current_price = price
                logger.info(f"Updating instrument {instrument.name} with price {price}")
                instrument_service.log_instrument_save(instrument)
            except Exception as e:
                logger.error(f"Error processing {instrument.name}: {str(e)}")
    finally:
        driver.quit()

def fetch_current_prices(instrument_service=None):
    logger.info("Fetching current prices")
    if instrument_service is None:
        instrument_service = InstrumentService()

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

# Schedule the job
schedule.every(900).seconds.do(fetch_current_prices)

# Keep the script running
if __name__ == '__main__':
  # Start Flask app in a separate thread
  flask_thread = Thread(target=run_flask_app)
  flask_thread.start()

  # Run the scheduled job
  while True:
    schedule.run_pending()
    time.sleep(1)

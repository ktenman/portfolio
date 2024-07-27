import os
import time
import logging
from decimal import Decimal
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options
import schedule
import requests
from flask import Flask, jsonify
from threading import Thread
from werkzeug.middleware.proxy_fix import ProxyFix

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
  def __init__(self, name, symbol, id=None, category=None, baseCurrency=None, current_price=None):
    self.name = name
    self.symbol = symbol
    self.current_price = current_price
    self.id = id
    self.category = category
    self.baseCurrency = baseCurrency

  def __str__(self):
    return f"Instrument(id={self.id}, name='{self.name}', symbol='{self.symbol}', category='{self.category}', baseCurrency='{self.baseCurrency}', current_price={self.current_price})"

  def __repr__(self):
    return self.__str__()

class InstrumentService:
  def log_instrument_save(self, instrument):
    logger.info(f"Saved {instrument.name} with current price: {instrument.current_price}")
    self.update_backend_instrument(instrument)

  def fetch_instruments_from_backend(self):
    try:
      response = requests.get(BACKEND_URL)
      response.raise_for_status()
      instruments = response.json()
      return [Instrument(name=inst['name'], symbol=inst['symbol'], id=inst['id'],
                         category=inst.get('category'), baseCurrency=inst.get('baseCurrency'),
                         current_price=Decimal(inst['currentPrice']) if inst['currentPrice'] is not None else None)
              for inst in instruments]
    except Exception as e:
      logger.error(f"Error fetching instruments from backend: {e}")
      return []

  def update_backend_instrument(self, instrument):
    if instrument.id is None:
      logger.error(f"Instrument ID is missing for {instrument.name}. Skipping update.")
      return

    try:
      payload = {
        "id": instrument.id,
        "symbol": instrument.symbol,
        "name": instrument.name,
        "category": instrument.category,
        "baseCurrency": instrument.baseCurrency,
        "currentPrice": str(instrument.current_price) if instrument.current_price is not None else None
      }

      logger.info(f"Updating {instrument.symbol} instrument with current price: {instrument.current_price}")
      response = requests.put(f"{BACKEND_URL}/{instrument.id}", json=payload)

      if response.status_code == 200:
        logger.info(
          f"Successfully updated {instrument.symbol} instrument with current price: {instrument.current_price}")
      else:
        logger.error(f"Failed to update {instrument.symbol} instrument. Status code: {response.status_code}")
        logger.error(f"Response text: {response.text}")
        logger.error(f"Response headers: {response.headers}")
    except Exception as e:
      logger.error(f"Error updating {instrument.symbol} instrument: {e}")

def setup_webdriver():
  firefox_options = Options()
  if os.getenv("HEADLESS", "False") == "True":
    firefox_options.add_argument("--headless")
  return webdriver.Firefox(options=firefox_options)

def accept_cookies(driver):
  iframes = driver.find_elements(By.TAG_NAME, "iframe")
  logger.info(f"Found {len(iframes)} iframes")
  if len(iframes) == 4:
    driver.switch_to.frame(iframes[-1])
    accept_button = driver.find_element(By.XPATH, "//button[text()='Accept Cookies']")
    accept_button.click()
    driver.switch_to.default_content()
    logger.info("Accepted cookies")

def fetch_price(driver, instrument):
  driver.get(f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}")
  logger.info(f"Opened URL for instrument: {instrument.symbol}")
  time.sleep(3)

  accept_cookies(driver)

  price_text = driver.find_element(By.CLASS_NAME, "mod-ui-data-list__value").text
  logger.info(f"Found price text for {instrument.symbol}: {price_text}")
  return Decimal(price_text.replace(",", ""))

def process_instrument(driver, instrument, instrument_service):
  try:
    price = fetch_price(driver, instrument)
    instrument.current_price = price
    logger.info(f"Updating instrument {instrument.name} with price {price}")
    instrument_service.log_instrument_save(instrument)
    logger.info(f"Saved instrument {instrument.name} with current price: {price}")
  except Exception as e:
    logger.error(f"Error retrieving current price for {instrument.name}: {e}")

def fetch_current_prices(instrument_service=None):
  logger.info("Fetching current prices")
  if instrument_service is None:
    instrument_service = InstrumentService()
  remote_instruments = instrument_service.fetch_instruments_from_backend()
  logger.info(f"Fetched instruments from backend: {remote_instruments}")
  instrument_map = {inst.symbol: inst for inst in remote_instruments}

  driver = setup_webdriver()

  try:
    for instrument in remote_instruments:
      logger.info(f"Processing instrument: {instrument.symbol}")
      if instrument.symbol not in instrument_map:
        logger.warning(f"Instrument with symbol {instrument.symbol} not found in remote service.")
        continue

      remote_instrument = instrument_map[instrument.symbol]
      instrument.id = remote_instrument.id

      process_instrument(driver, instrument, instrument_service)
  finally:
    driver.quit()

  logger.info("Completed fetching current prices")

# Add health check endpoint
@app.route('/health')
def health_check():
  return jsonify({"status": "healthy"}), 200

# Custom log filter to exclude health check logs
class ExcludeHealthFilter(logging.Filter):
  def filter(self, record):
    return 'GET /health' not in record.getMessage()

# Apply the custom filter to the Werkzeug logger
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

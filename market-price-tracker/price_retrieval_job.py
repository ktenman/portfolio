import os
import time
import logging
from decimal import Decimal
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options
import schedule
import requests

# Configure logging
logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s.%(msecs)03d [%(threadName)s] %(levelname)-5s %(name)-20s %(message)s',
                    datefmt='%Y-%m-%d %H:%M:%S',
                    handlers=[logging.StreamHandler()])
logger = logging.getLogger()

BACKEND_URL = os.environ.get('BACKEND_URL', 'http://backend:8080/api/instruments')

class Instrument:
  def __init__(self, name, symbol, id=None, category=None, baseCurrency=None, current_price=None):
    self.name = name
    self.symbol = symbol
    self.current_price = current_price
    self.id = id
    self.category = category
    self.baseCurrency = baseCurrency

class InstrumentDto:
  def __init__(self, id, symbol, name, category, baseCurrency, currentPrice):
    self.id = id
    self.symbol = symbol
    self.name = name
    self.category = category
    self.baseCurrency = baseCurrency
    self.currentPrice = currentPrice

  @staticmethod
  def from_entity(instrument):
    return InstrumentDto(
      id=instrument.id,
      symbol=instrument.symbol,
      name=instrument.name,
      category=instrument.category,
      baseCurrency=instrument.baseCurrency,
      currentPrice=str(instrument.current_price) if instrument.current_price is not None else None
    )

  def to_entity(self):
    return Instrument(
      id=self.id,
      symbol=self.symbol,
      name=self.name,
      category=self.category,
      baseCurrency=self.baseCurrency,
      current_price=Decimal(self.currentPrice) if self.currentPrice is not None else None
    )

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
      dto = InstrumentDto.from_entity(instrument)
      payload = {
        "id": dto.id,
        "symbol": dto.symbol,
        "name": dto.name,
        "category": dto.category,
        "baseCurrency": dto.baseCurrency,
        "currentPrice": dto.currentPrice
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

def fetch_current_prices(instrument_service=None):
  logger.info("Fetching current prices")
  if instrument_service is None:
    instrument_service = InstrumentService()
  remote_instruments = instrument_service.fetch_instruments_from_backend()
  logger.info(f"Fetched instruments from backend: {remote_instruments}")
  instrument_map = {inst.symbol: inst for inst in remote_instruments}

  firefox_options = Options()
  if os.getenv("HEADLESS", "False") == "True":
    firefox_options.add_argument("--headless")

  driver = webdriver.Firefox(options=firefox_options)

  try:
    for instrument in remote_instruments:
      logger.info(f"Processing instrument: {instrument.symbol}")
      if instrument.symbol not in instrument_map:
        logger.warning(f"Instrument with symbol {instrument.symbol} not found in remote service.")
        continue

      remote_instrument = instrument_map[instrument.symbol]
      instrument.id = remote_instrument.id

      try:
        driver.get(f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}")
        logger.info(f"Opened URL for instrument: {instrument.symbol}")
        time.sleep(3)

        iframes = driver.find_elements(By.TAG_NAME, "iframe")
        logger.info(f"Found {len(iframes)} iframes for {instrument.symbol}")
        if len(iframes) == 4:
          driver.switch_to.frame(iframes[-1])
          accept_button = driver.find_element(By.XPATH, "//button[text()='Accept Cookies']")
          accept_button.click()
          driver.switch_to.default_content()
          logger.info(f"Accepted cookies for {instrument.symbol}")

        price_text = driver.find_element(By.CLASS_NAME, "mod-ui-data-list__value").text
        logger.info(f"Found price text for {instrument.symbol}: {price_text}")
        price = Decimal(price_text.replace(",", ""))
        instrument.current_price = price
        logger.info(f"Updating instrument {instrument.name} with price {price}")
        instrument_service.log_instrument_save(instrument)
        logger.info(f"Saved instrument {instrument.name} with current price: {price}")
      except Exception as e:
        logger.error(f"Error retrieving current price for {instrument.name}: {e}")
  finally:
    driver.quit()

  logger.info("Completed fetching current prices")

# Schedule the job
schedule.every(900).seconds.do(fetch_current_prices)

# Keep the script running
if __name__ == '__main__':
  while True:
    schedule.run_pending()
    time.sleep(1)

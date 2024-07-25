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
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s:%(message)s',
                    handlers=[logging.StreamHandler()])
logger = logging.getLogger()

OTHER_SERVICE_URL = os.environ.get('OTHER_SERVICE_URL', 'http://backend:8080/api/instruments')

class Instrument:
  def __init__(self, name, symbol, id=None):
    self.name = name
    self.symbol = symbol
    self.current_price = None
    self.id = id

class InstrumentDto:
  def __init__(self, id, symbol, name, category, baseCurrency, currentPrice):
    self.id = id
    self.symbol = symbol
    self.name = name
    self.category = category
    self.baseCurrency = baseCurrency
    self.currentPrice = currentPrice

  @staticmethod
  def from_instrument(instrument):
    return InstrumentDto(
      id=instrument.id,
      symbol=instrument.symbol,
      name=instrument.name,
      category="",
      baseCurrency="USD",
      currentPrice=str(instrument.current_price)
    )

class InstrumentService:
  def save_instrument(self, instrument):
    # Placeholder for saving instrument, replace with actual implementation
    logger.info(f"Saved {instrument.name} with current price: {instrument.current_price}")
    self.update_other_service(instrument)

  def get_all_instruments(self):
    try:
      response = requests.get(OTHER_SERVICE_URL)
      response.raise_for_status()
      instruments = response.json()
      return [Instrument(name=inst['name'], symbol=inst['symbol'], id=inst['id']) for inst in instruments]
    except Exception as e:
      logger.error(f"Error fetching instruments from other service: {e}")
      return []

  def update_other_service(self, instrument):
    if instrument.id is None:
      logger.error(f"Instrument ID is missing for {instrument.name}. Skipping update.")
      return

    try:
      dto = InstrumentDto.from_instrument(instrument)
      payload = {
        "id": dto.id,
        "symbol": dto.symbol,
        "name": dto.name,
        "category": dto.category,
        "baseCurrency": dto.baseCurrency,
        "currentPrice": dto.currentPrice
      }

      logger.info(f"Updating other service for {instrument.name} with current price: {instrument.current_price}")
      response = requests.put(f"{OTHER_SERVICE_URL}/{instrument.id}", json=payload)

      if response.status_code == 200:
        logger.info(f"Successfully updated other service for {instrument.name} with current price: {instrument.current_price}")
      else:
        logger.error(f"Failed to update other service for {instrument.name}. Status code: {response.status_code}")
        logger.error(f"Response text: {response.text}")
        logger.error(f"Response headers: {response.headers}")
    except Exception as e:
      logger.error(f"Error updating other service for {instrument.name}: {e}")

def fetch_current_prices():
  logger.info("Fetching current prices")
  instrument_service = InstrumentService()
  remote_instruments = instrument_service.get_all_instruments()

  instrument_map = {inst.symbol: inst for inst in remote_instruments}

  firefox_options = Options()
  if os.getenv("HEADLESS", "False") == "True":
    firefox_options.add_argument("--headless")

  driver = webdriver.Firefox(options=firefox_options)

  for instrument in remote_instruments:
    if instrument.symbol in instrument_map:
      remote_instrument = instrument_map[instrument.symbol]
      instrument.id = remote_instrument.id

      try:
        driver.get(f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}")
        time.sleep(3)
        iframes = driver.find_elements(By.TAG_NAME, "iframe")
        if len(iframes) == 4:
          driver.switch_to.frame(iframes[-1])
          accept_button = driver.find_element(By.XPATH, "//button[text()='Accept Cookies']")
          accept_button.click()
          driver.switch_to.default_content()
        price_text = driver.find_element(By.CLASS_NAME, "mod-ui-data-list__value").text
        price = Decimal(price_text.replace(",", ""))
        instrument.current_price = price
        instrument_service.save_instrument(instrument)
        logger.info(f"{instrument.name} current price: {price}")
      except Exception as e:
        logger.error(f"Error retrieving current price for {instrument.name}: {e}")
      finally:
        driver.quit()
    else:
      logger.warning(f"Instrument with symbol {instrument.symbol} not found in remote service.")

  logger.info("Completed fetching current prices")

# Schedule the job
schedule.every(10).seconds.do(fetch_current_prices)

# Keep the script running
while True:
  schedule.run_pending()
  time.sleep(1)

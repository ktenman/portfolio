import logging
import os
import schedule
import time
from decimal import Decimal
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s:%(message)s', handlers=[logging.StreamHandler()])
logger = logging.getLogger()

class Instrument:
  def __init__(self, name, symbol):
    self.name = name
    self.symbol = symbol
    self.current_price = None

class InstrumentService:
  def get_all_instruments(self):
    # Placeholder for fetching all instruments, replace with actual implementation
    return [Instrument(name="QDVE", symbol="QDVE:GER:EUR")]

  def save_instrument(self, instrument):
    # Placeholder for saving instrument, replace with actual implementation
    logger.info(f"Saved {instrument.name} with current price: {instrument.current_price}")

def fetch_current_prices():
  logger.info("Fetching current prices")
  instrument_service = InstrumentService()
  instruments = instrument_service.get_all_instruments()

  firefox_options = Options()
  if os.getenv("HEADLESS", "False") == "True":
    firefox_options.add_argument("--headless")

  driver = webdriver.Firefox(options=firefox_options)

  for instrument in instruments:
    if "QDVE" in instrument.symbol:
      try:
        driver.get("https://markets.ft.com/data/etfs/tearsheet/summary?s=QDVE:GER:EUR")
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

  logger.info("Completed fetching current prices")

# Schedule the job
schedule.every(10).seconds.do(fetch_current_prices)

# Keep the script running
while True:
  schedule.run_pending()
  time.sleep(1)

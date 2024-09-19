import logging
import os
from decimal import Decimal
from contextlib import contextmanager
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.firefox.options import Options
from selenium.webdriver.firefox.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from selenium.common.exceptions import TimeoutException, WebDriverException, NoSuchDriverException
from tenacity import retry, stop_after_attempt, wait_exponential, retry_if_exception_type
from services import InstrumentService

logger = logging.getLogger(__name__)

class PriceFetcher:
    def __init__(self, backend_url):
        self.backend_url = backend_url
        self.instrument_service = InstrumentService(backend_url)

    @contextmanager
    def setup_webdriver(self):
        firefox_options = Options()
        if os.getenv("HEADLESS", "False") == "True":
            firefox_options.add_argument("--headless")
        firefox_options.add_argument("--no-sandbox")
        firefox_options.add_argument("--disable-dev-shm-usage")

        # Specify the path to geckodriver if it's not in PATH
        geckodriver_path = os.getenv("GECKODRIVER_PATH", "/usr/local/bin/geckodriver")
        service = Service(geckodriver_path)

        try:
            driver = webdriver.Firefox(options=firefox_options, service=service)
            logger.info("WebDriver initialized successfully")
            yield driver
        except Exception as e:
            logger.error(f"Failed to initialize WebDriver: {str(e)}")
            raise
        finally:
            if 'driver' in locals():
                driver.quit()

    def accept_cookies(self, driver):
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
           retry=retry_if_exception_type((TimeoutException, WebDriverException, NoSuchDriverException)))
    def fetch_price(self, instrument):
        with self.setup_webdriver() as driver:
            url = f"https://markets.ft.com/data/etfs/tearsheet/summary?s={instrument.symbol}"
            logger.info(f"Attempting to open URL for instrument: {instrument.symbol}")
            driver.get(url)
            logger.info(f"Opened URL for instrument: {instrument.symbol}")

            self.accept_cookies(driver)

            price_element = WebDriverWait(driver, 10).until(
                EC.presence_of_element_located((By.CLASS_NAME, "mod-ui-data-list__value"))
            )

            price_text = price_element.text
            logger.info(f"Found price text for {instrument.symbol}: {price_text}")
            return Decimal(price_text.replace(",", ""))

    def fetch_all_prices(self):
        logger.info("Starting price fetching process")
        remote_instruments = self.instrument_service.fetch_instruments_from_backend()
        for instrument in remote_instruments:
            try:
                price = self.fetch_price(instrument)
                instrument.current_price = price
                logger.info(f"Updating instrument {instrument.name} with price {price}")
                self.instrument_service.log_instrument_save(instrument)
            except Exception as e:
                logger.error(f"Error processing {instrument.name}: {str(e)}", exc_info=True)
        logger.info("Completed fetching current prices")

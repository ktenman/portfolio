import logging
import requests
from decimal import Decimal
from models import Instrument

logger = logging.getLogger(__name__)


class InstrumentService:
  def __init__(self, backend_url):
    self.backend_url = backend_url

  def log_instrument_save(self, instrument):
    logger.info(f"Saved {instrument.name} with current price: {instrument.current_price}")
    self.update_backend_instrument(instrument)

  def fetch_instruments_from_backend(self):
    try:
      response = requests.get(self.backend_url)
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
      current_price=Decimal(inst['currentPrice']) if inst['currentPrice'] is not None else None,
      provider_name=inst.get('providerName') if inst.get('providerName') is not None else None
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
      "currentPrice": str(instrument.current_price) if instrument.current_price is not None else None,
      "providerName": instrument.provider_name
    }

    try:
      logger.info(f"Updating {instrument.symbol} instrument with current price: {instrument.current_price}")
      response = requests.put(f"{self.backend_url}/{instrument.id}", json=payload)
      response.raise_for_status()
      logger.info(f"Successfully updated {instrument.symbol} instrument with current price: {instrument.current_price}")
    except requests.exceptions.RequestException as e:
      error_content = e.response.json() if e.response else "No response content"
      logger.error(f"Failed to update {instrument.symbol} instrument. Error: {e}. Response content: {error_content}")

import pytest
import pytest
import requests_mock
import schedule
from decimal import Decimal
from unittest.mock import patch, MagicMock

from price_retrieval_job import Instrument, InstrumentService, fetch_current_prices

BACKEND_URL = 'http://backend:8080/api/instruments'

@pytest.fixture
def instrument_service():
  return InstrumentService()

@pytest.fixture
def instruments():
  return [
    Instrument(name='Instrument1', symbol='SYM1', id=1, current_price=Decimal('100.0')),
    Instrument(name='Instrument2', symbol='SYM2', id=2, current_price=Decimal('200.0'))
  ]

def test_fetch_instruments_from_backend(instrument_service):
  with requests_mock.Mocker() as m:
    m.get(BACKEND_URL, json=[
      {'id': 1, 'name': 'Instrument1', 'symbol': 'SYM1', 'category': 'Category1', 'baseCurrency': 'USD', 'currentPrice': '100.0'},
      {'id': 2, 'name': 'Instrument2', 'symbol': 'SYM2', 'category': 'Category2', 'baseCurrency': 'USD', 'currentPrice': '200.0'}
    ])

    instruments = instrument_service.fetch_instruments_from_backend()

    assert len(instruments) == 2
    assert instruments[0].name == 'Instrument1'
    assert instruments[1].name == 'Instrument2'
    assert instruments[0].current_price == Decimal('100.0')
    assert instruments[1].current_price == Decimal('200.0')

def test_update_backend_instrument(instrument_service):
  instrument = Instrument(name='Instrument1', symbol='SYM1', id=1, current_price=Decimal('150.0'))

  with requests_mock.Mocker() as m:
    m.put(f"{BACKEND_URL}/1", status_code=200)
    instrument_service.update_backend_instrument(instrument)

    assert m.called
    assert m.last_request.json() == {
      'id': 1,
      'symbol': 'SYM1',
      'name': 'Instrument1',
      'category': None,
      'baseCurrency': None,
      'currentPrice': '150.0'
    }

@patch('selenium.webdriver.Firefox')
def test_fetch_current_prices(mock_webdriver, instrument_service, instruments):
  # Set up mock responses
  mock_driver = MagicMock()
  mock_webdriver.return_value = mock_driver

  def mock_find_elements(by, value):
    if value == 'iframe':
      return [MagicMock(), MagicMock(), MagicMock(), MagicMock()]
    return []

  def mock_find_element(by, value):
    if value == 'mod-ui-data-list__value':
      element = MagicMock()
      element.text = '300.0'
      return element
    elif value == "//button[text()='Accept Cookies']":
      return MagicMock()

  mock_driver.find_elements.side_effect = mock_find_elements
  mock_driver.find_element.side_effect = mock_find_element

  # Mock the instrument service methods
  with requests_mock.Mocker() as m:
    m.get(BACKEND_URL, json=[
      {'id': 1, 'name': 'Instrument1', 'symbol': 'SYM1', 'category': 'Category1', 'baseCurrency': 'USD', 'currentPrice': '100.0'},
      {'id': 2, 'name': 'Instrument2', 'symbol': 'SYM2', 'category': 'Category2', 'baseCurrency': 'USD', 'currentPrice': '200.0'}
    ])

    # Ensure the PUT requests are mocked correctly
    m.put(f"{BACKEND_URL}/1", status_code=200)
    m.put(f"{BACKEND_URL}/2", status_code=200)

    # Replace the log_instrument_save method with a mock
    instrument_service.log_instrument_save = MagicMock()

    # Call the function to test
    fetch_current_prices(instrument_service)

    # Assertions
    assert instrument_service.log_instrument_save.called, "log_instrument_save was not called"
    calls = instrument_service.log_instrument_save.call_args_list
    assert len(calls) == 2, f"Expected 2 calls to log_instrument_save, but got {len(calls)}"

    updated_instruments = [call.args[0] for call in calls]
    assert updated_instruments[0].current_price == Decimal('300.0')
    assert updated_instruments[1].current_price == Decimal('300.0')

def test_schedule_job():
  # Clear any previously scheduled jobs
  schedule.clear()

  schedule.every(10).seconds.do(fetch_current_prices)
  assert len(schedule.jobs) == 1

if __name__ == '__main__':
  pytest.main()

from decimal import Decimal


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

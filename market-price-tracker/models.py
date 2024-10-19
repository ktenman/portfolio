class Instrument:
  def __init__(
    self,
    name,
    symbol,
    id=None,
    category=None,
    base_currency=None,
    current_price=None,
    provider_name=None
  ):
    self.name = name
    self.symbol = symbol
    self.current_price = current_price
    self.id = id
    self.category = category
    self.base_currency = base_currency
    self.provider_name = provider_name

  def __str__(self):
    return (
      f"Instrument(id={self.id}, "
      f"name='{self.name}', "
      f"symbol='{self.symbol}', "
      f"category='{self.category}', "
      f"baseCurrency='{self.base_currency}', "
      f"current_price={self.current_price}' "
      f"provider_name='{self.provider_name}')"
    )

  __repr__ = __str__

UPDATE portfolio_transaction
SET commission = 1.00
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'WBIT:GER:EUR');

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 142.470, 100.00 / 142.470, '2020-12-18', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 275.085, 225.79 / 275.085, '2021-10-07', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 526.132, 452.00 / 526.132, '2021-11-01', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 412.860, 357.00 / 412.860, '2021-12-07', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 445.494, 396.00 / 445.494, '2021-12-29', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'SELL', 1792.041, 1568.39 / 1792.041, '2023-10-17', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'BUY', 1128.562, 1200.00 / 1128.562, '2024-09-03', 'TULEVA', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EE3600001707'), 'SELL', 1128.562, 1312.29 / 1128.562, '2025-01-01', 'TULEVA', 0);

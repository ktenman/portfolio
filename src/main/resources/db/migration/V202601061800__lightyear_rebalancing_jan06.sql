INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,
                                   commission)
VALUES ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 59.7649042, 4637.76 / 59.7649042,
        '2026-01-06', 'LIGHTYEAR', 0),
       ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 714.160147828, 4637.76 / 714.160147828,
        '2026-01-06', 'LIGHTYEAR', 0);

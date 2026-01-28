INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform,
                                   commission)
VALUES
       ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 172.767788067, 1473.88 / 172.767788067,
        '2026-01-28', 'LIGHTYEAR', 0),
       ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 28.393596377, 1755.86 / 28.393596377,
        '2026-01-28', 'LIGHTYEAR', 0),
       ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 94.533587004, 3229.74 / 94.533587004,
        '2026-01-28', 'LIGHTYEAR', 0);

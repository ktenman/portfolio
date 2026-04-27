INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'SELL', 2.380789555, 229.77 / 2.380789555, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 45.311708058, 536.40 / 45.311708058, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 78.913593118, 605.50 / 78.913593118, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 8.490067399, 1436.01 / 8.490067399, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'SELL', 78.158901195, 2386.58 / 78.158901195, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'BUY', 204.933395872, 2621.51 / 204.933395872, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 30.133853151, 1159.40 / 30.133853151, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 16.281258402, 605.50 / 16.281258402, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'BUY', 18.327781715, 310.33 / 18.327781715, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 5.251923421, 293.53 / 5.251923421, '2026-04-27', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 26.052362707, 203.99 / 26.052362707, '2026-04-27', 'LIGHTYEAR', 0);

INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 631.827674241, 7.85, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 94.180246913, 52.65, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 8.364074375, 147.36, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 24.801270597, 50.37, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 35.176927343, 36.06, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 8.283488698, 156.62, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 36.664783161, 35.40, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WTAI:MIL:EUR'), 'SELL', 17.585290148, 74.10, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'SELL', 6.053437849, 223.25, '2026-01-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WBIT:GER:EUR'), 'SELL', 267.912301452, 18.16, '2026-01-02', 'LIGHTYEAR', 0);

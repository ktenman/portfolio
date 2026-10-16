INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'SEC0:GER:EUR'), 'SELL', 8.250589622, 139.93 / 8.250589622, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VGLA:GER:EUR'), 'SELL', 35.968219614, 157.88 / 35.968219614, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'BUY', 31.109326845, 1917.89 / 31.109326845, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 0.656478691, 30.50 / 0.656478691, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VXUS:GER:EUR'), 'BUY', 3.611556795, 15.53 / 3.611556795, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 2.200149737, 88.16 / 2.200149737, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VVSM:GER:EUR'), 'SELL', 1.377043747, 124.65 / 1.377043747, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 9.951620264, 130.82 / 9.951620264, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.902069286, 193.99 / 0.902069286, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 47.620565088, 369.11 / 47.620565088, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 54.089382614, 528.29 / 54.089382614, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 11.297758381, 569.52 / 11.297758381, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 40.615835777, 775.60 / 40.615835777, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'SELL', 362.969437806, 2885.97 / 362.969437806, '2026-10-15', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUR'), 'BUY', 5000, 1, '2026-10-15', 'BINANCE', 0);

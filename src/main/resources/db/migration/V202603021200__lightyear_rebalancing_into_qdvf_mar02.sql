INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'SELL', 0.937860345, 58.56 / 0.937860345, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'SELL', 76.647269332, 492.61 / 76.647269332, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XNAS:GER:EUR'), 'SELL', 10.306646058, 500.13 / 10.306646058, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 3.402083889, 509.36 / 3.402083889, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VNRA:GER:EUR'), 'SELL', 3.583734029, 521.72 / 3.583734029, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 15.923030394, 526.50 / 15.923030394, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVF:GER:EUR'), 'BUY', 242.826589596, 2528.80 / 242.826589596, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 0.747284768, 28.21 / 0.747284768, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EUDF:GER:EUR'), 'BUY', 0.468522483, 16.41 / 0.468522483, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'BUY', 1.161005734, 10.53 / 1.161005734, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'VWCG:GER:EUR'), 'BUY', 0.175706512, 10.01 / 0.175706512, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'IS3S:GER:EUR'), 'BUY', 0.138878971, 7.78 / 0.138878971, '2026-03-02', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CSX5:AEX:EUR'), 'BUY', 0.031029986, 7.14 / 0.031029986, '2026-03-02', 'LIGHTYEAR', 0);

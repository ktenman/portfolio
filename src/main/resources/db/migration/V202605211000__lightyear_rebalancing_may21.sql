INSERT INTO portfolio_transaction (instrument_id, transaction_type, quantity, price, transaction_date, platform, commission)
VALUES
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'BUY', 0.582285115, 22.22 / 0.582285115, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 3.099562894, 38.29 / 3.099562894, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'BUY', 3.099562894, 38.29 / 3.099562894, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2.277698863, 96.21 / 2.277698863, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'BUY', 2.277698863, 96.21 / 2.277698863, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFEN:GER:EUR'), 'BUY', 3.169202226, 170.82 / 3.169202226, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'LSMC:GER:EUR'), 'BUY', 2.938578586, 316.72 / 2.938578586, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = '84X0:GER:EUR'), 'BUY', 43.449121006, 338.60 / 43.449121006, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'AIFS:GER:EUR'), 'BUY', 42.383701188, 374.46 / 42.383701188, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'QDVE:GER:EUR'), 'SELL', 2.287718463, 96.21 / 2.287718463, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'WEBN:GER:EUR'), 'SELL', 3.107125466, 38.29 / 3.107125466, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXUS:GER:EUR'), 'SELL', 0.292593079, 11.12 / 0.292593079, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'XAIX:GER:EUR'), 'SELL', 0.042477235, 8.21 / 0.042477235, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'CEMS:GER:EUR'), 'SELL', 1.12057272, 14.87 / 1.12057272, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'DFND:PAR:EUR'), 'SELL', 5.477440319, 43.83 / 5.477440319, '2026-05-21', 'LIGHTYEAR', 0),
    ((SELECT id FROM instrument WHERE symbol = 'EXA1:AEX:EUR'), 'SELL', 73.169068862, 1279.29 / 73.169068862, '2026-05-21', 'LIGHTYEAR', 0);

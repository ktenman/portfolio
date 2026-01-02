UPDATE etf_holding
SET ticker = 'BTCEUR'
WHERE LOWER(name) = 'bitcoin' AND ticker = 'BTC';

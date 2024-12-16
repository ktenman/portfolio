ALTER TABLE portfolio_transaction
    ADD COLUMN platform VARCHAR(50);

CREATE INDEX idx_portfolio_transaction_platform ON portfolio_transaction (platform);

UPDATE portfolio_transaction SET platform = 'SWEDBANK' WHERE id IN (9);
UPDATE portfolio_transaction SET platform = 'BINANCE' WHERE id IN (5, 8);
UPDATE portfolio_transaction SET platform = 'TRADING212' WHERE id IN (1, 2, 3, 4, 10);
UPDATE portfolio_transaction SET platform = 'LIGHTYEAR' WHERE id IN (6, 7, 11);

ALTER TABLE portfolio_transaction
    ALTER COLUMN platform SET NOT NULL;

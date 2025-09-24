-- Add commission and currency columns to portfolio_transaction table
-- commission: stores transaction fees/commissions (default 0)
-- currency: supports multi-currency transactions (default EUR)

ALTER TABLE portfolio_transaction
    ADD COLUMN commission NUMERIC(20, 10) DEFAULT 0,
    ADD COLUMN currency VARCHAR(3) DEFAULT 'EUR';

-- Add index on currency column for performance optimization
CREATE INDEX idx_portfolio_transaction_currency ON portfolio_transaction (currency);
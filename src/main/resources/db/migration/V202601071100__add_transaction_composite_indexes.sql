CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_portfolio_transaction_instrument_platform_date
    ON portfolio_transaction(instrument_id, platform, transaction_date);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_portfolio_transaction_platform_date
    ON portfolio_transaction(platform, transaction_date);

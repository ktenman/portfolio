CREATE TABLE IF NOT EXISTS portfolio_intraday_summary
(
    id                 BIGSERIAL PRIMARY KEY,
    captured_at        TIMESTAMP WITH TIME ZONE NOT NULL UNIQUE,
    total_value        NUMERIC(20, 10)          NOT NULL,
    xirr_annual_return NUMERIC(20, 10)          NOT NULL,
    total_profit       NUMERIC(20, 10)          NOT NULL,
    earnings_per_day   NUMERIC(20, 10)          NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version            BIGINT                   NOT NULL DEFAULT 0
);

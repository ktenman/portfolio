CREATE TABLE exchange_rate
(
    id             BIGSERIAL PRIMARY KEY,
    entry_date     DATE                     NOT NULL,
    base_currency  VARCHAR(3)               NOT NULL,
    quote_currency VARCHAR(3)               NOT NULL,
    rate           NUMERIC(20, 10)          NOT NULL,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version        BIGINT                   NOT NULL DEFAULT 0,
    UNIQUE (entry_date, base_currency, quote_currency)
);

CREATE INDEX idx_exchange_rate_lookup ON exchange_rate (base_currency, quote_currency, entry_date DESC);

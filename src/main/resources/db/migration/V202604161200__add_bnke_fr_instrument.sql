INSERT INTO instrument (
    symbol,
    name,
    instrument_category,
    base_currency,
    provider_name,
    provider_external_id,
    current_price,
    created_at,
    updated_at,
    version
) VALUES (
    'BNKE:PAR:EUR',
    'Amundi Euro Stoxx Banks UCITS ETF Acc',
    'ETF',
    'EUR',
    'TRADING212',
    'BNKEp_EQ',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

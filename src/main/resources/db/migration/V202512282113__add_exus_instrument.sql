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
    'EXUS:GER:EUR',
    'Xtrackers MSCI World Ex USA',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1ef669d5-871f-60c2-9d43-eb48e3c470cb',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

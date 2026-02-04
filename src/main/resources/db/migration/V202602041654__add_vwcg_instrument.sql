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
    'VWCG:GER:EUR',
    'Vanguard FTSE Developed Europe',
    'ETF',
    'EUR',
    'LIGHTYEAR',
    '1eda4008-ca32-66dc-b60a-654bcfbd8ac3',
    0,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    0
);

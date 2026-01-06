UPDATE etf_position ep
SET weight_percentage = 1.79
WHERE ep.holding_id IN (
    SELECT eh.id
    FROM etf_holding eh
    WHERE LOWER(eh.name) = 'meta platforms'
)
AND ep.etf_instrument_id IN (
    SELECT i.id
    FROM instrument i
    WHERE i.symbol = 'WTAI:MIL:EUR'
)
AND ep.weight_percentage > 100;

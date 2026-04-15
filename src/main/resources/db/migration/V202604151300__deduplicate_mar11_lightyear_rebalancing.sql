DELETE FROM portfolio_transaction
WHERE id IN (
    SELECT id
    FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY instrument_id, transaction_type, quantity, price, transaction_date, platform
                   ORDER BY id
               ) AS row_num
        FROM portfolio_transaction
        WHERE transaction_date = '2026-03-11'
          AND platform = 'LIGHTYEAR'
    ) duplicates
    WHERE row_num > 1
);

DELETE FROM portfolio_transaction
WHERE instrument_id = (SELECT id FROM instrument WHERE symbol = 'WEBG:GER:EUR');

DELETE FROM instrument WHERE symbol = 'WEBG:GER:EUR';

DELETE FROM flyway_schema_history WHERE version IN ('202603111000', '202603111100');

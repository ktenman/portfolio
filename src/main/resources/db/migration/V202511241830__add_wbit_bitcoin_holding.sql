DO $$
DECLARE
    v_wbit_instrument_id BIGINT;
    v_bitcoin_holding_id BIGINT;
BEGIN
    SELECT id INTO v_wbit_instrument_id FROM instrument WHERE symbol = 'WBIT:GER:EUR';

    IF v_wbit_instrument_id IS NULL THEN
        RAISE EXCEPTION 'WBIT instrument not found';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM etf_holding WHERE ticker = 'BTC' AND name = 'Bitcoin') THEN
        INSERT INTO etf_holding (name, ticker, sector)
        VALUES ('Bitcoin', 'BTC', 'Cryptocurrency')
        RETURNING id INTO v_bitcoin_holding_id;

        INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage, position_rank)
        VALUES (v_wbit_instrument_id, v_bitcoin_holding_id, CURRENT_DATE, 100.0000, 1);

        RAISE NOTICE 'Created Bitcoin holding for WBIT ETF';
    ELSE
        SELECT id INTO v_bitcoin_holding_id FROM etf_holding WHERE ticker = 'BTC' AND name = 'Bitcoin';

        IF NOT EXISTS (SELECT 1 FROM etf_position WHERE etf_instrument_id = v_wbit_instrument_id AND holding_id = v_bitcoin_holding_id) THEN
            INSERT INTO etf_position (etf_instrument_id, holding_id, snapshot_date, weight_percentage, position_rank)
            VALUES (v_wbit_instrument_id, v_bitcoin_holding_id, CURRENT_DATE, 100.0000, 1);

            RAISE NOTICE 'Created ETF position for existing Bitcoin holding';
        ELSE
            RAISE NOTICE 'Bitcoin holding and position for WBIT already exist';
        END IF;
    END IF;
END $$;

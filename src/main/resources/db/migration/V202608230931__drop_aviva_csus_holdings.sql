DELETE FROM etf_holding h
WHERE EXISTS (
    SELECT 1 FROM etf_position p
    WHERE p.etf_instrument_id = (SELECT id FROM instrument WHERE symbol = 'GB00B0ZDNB53:GBP')
      AND p.holding_id = h.id
  )
  AND NOT EXISTS (
    SELECT 1 FROM etf_position p
    WHERE p.etf_instrument_id <> (SELECT id FROM instrument WHERE symbol = 'GB00B0ZDNB53:GBP')
      AND p.holding_id = h.id
  );

DELETE FROM etf_position
WHERE etf_instrument_id = (SELECT id FROM instrument WHERE symbol = 'GB00B0ZDNB53:GBP');

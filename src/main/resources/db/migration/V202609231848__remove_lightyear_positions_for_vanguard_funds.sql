DELETE FROM etf_position
WHERE etf_instrument_id IN (
  SELECT id FROM instrument
  WHERE symbol IN ('VUAA:GER:EUR', 'VWCE:GER:EUR', 'VNRA:GER:EUR', 'VNRT:AEX:EUR', 'VWCG:GER:EUR')
);

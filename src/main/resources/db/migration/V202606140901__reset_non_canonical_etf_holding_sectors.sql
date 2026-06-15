UPDATE etf_holding
SET sector = NULL
WHERE sector IS NOT NULL
  AND (sector_source IS NULL OR sector_source <> 'LLM');

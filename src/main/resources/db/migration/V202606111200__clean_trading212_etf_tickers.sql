UPDATE etf_holding
SET ticker = NULL
WHERE ticker LIKE '%\_EQ' ESCAPE '\';

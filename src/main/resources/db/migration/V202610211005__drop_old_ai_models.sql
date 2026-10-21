UPDATE etf_holding
SET classified_by_model = CASE
  WHEN classified_by_model LIKE 'GEMINI%' THEN 'GEMINI_4_ARGON'
  WHEN classified_by_model LIKE 'CLAUDE%' THEN 'CLAUDE_OPUS_5_5'
  WHEN classified_by_model LIKE 'GPT%' THEN 'GPT_6_LUNA'
  WHEN classified_by_model LIKE 'DEEPSEEK%' THEN 'DEEPSEEK_V4_PRO'
END
WHERE classified_by_model NOT IN ('GEMINI_4_ARGON', 'CLAUDE_OPUS_5_5', 'GPT_6_LUNA', 'DEEPSEEK_V4_PRO');

UPDATE etf_holding
SET country_classified_by_model = CASE
  WHEN country_classified_by_model LIKE 'GEMINI%' THEN 'GEMINI_4_ARGON'
  WHEN country_classified_by_model LIKE 'CLAUDE%' THEN 'CLAUDE_OPUS_5_5'
  WHEN country_classified_by_model LIKE 'GPT%' THEN 'GPT_6_LUNA'
  WHEN country_classified_by_model LIKE 'DEEPSEEK%' THEN 'DEEPSEEK_V4_PRO'
END
WHERE country_classified_by_model NOT IN ('GEMINI_4_ARGON', 'CLAUDE_OPUS_5_5', 'GPT_6_LUNA', 'DEEPSEEK_V4_PRO');

UPDATE etf_holding
SET industry_classified_by_model = CASE
  WHEN industry_classified_by_model LIKE 'GEMINI%' THEN 'GEMINI_4_ARGON'
  WHEN industry_classified_by_model LIKE 'CLAUDE%' THEN 'CLAUDE_OPUS_5_5'
  WHEN industry_classified_by_model LIKE 'GPT%' THEN 'GPT_6_LUNA'
  WHEN industry_classified_by_model LIKE 'DEEPSEEK%' THEN 'DEEPSEEK_V4_PRO'
END
WHERE industry_classified_by_model NOT IN ('GEMINI_4_ARGON', 'CLAUDE_OPUS_5_5', 'GPT_6_LUNA', 'DEEPSEEK_V4_PRO');

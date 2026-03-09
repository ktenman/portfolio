UPDATE etf_holding SET classified_by_model = 'CLAUDE_OPUS_4_6' WHERE classified_by_model = 'CLAUDE_OPUS_4_5';
UPDATE etf_holding SET classified_by_model = 'CLAUDE_SONNET_4_6' WHERE classified_by_model = 'CLAUDE_SONNET_4_5';
UPDATE etf_holding SET classified_by_model = 'GEMINI_3_FLASH_PREVIEW' WHERE classified_by_model = 'GEMINI_2_5_FLASH';
UPDATE etf_holding SET country_classified_by_model = 'CLAUDE_OPUS_4_6' WHERE country_classified_by_model = 'CLAUDE_OPUS_4_5';
UPDATE etf_holding SET country_classified_by_model = 'CLAUDE_SONNET_4_6' WHERE country_classified_by_model = 'CLAUDE_SONNET_4_5';
UPDATE etf_holding SET country_classified_by_model = 'GEMINI_3_FLASH_PREVIEW' WHERE country_classified_by_model = 'GEMINI_2_5_FLASH';

CREATE TABLE diversification_config (
    id          BIGSERIAL PRIMARY KEY,
    config_data TEXT                                               NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    version     BIGINT                   DEFAULT 0                 NOT NULL
);

CREATE UNIQUE INDEX idx_diversification_config_singleton ON diversification_config ((true));

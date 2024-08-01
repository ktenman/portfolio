CREATE TABLE user_account
(
  id         BIGSERIAL PRIMARY KEY,
  email      VARCHAR(255) UNIQUE                                NOT NULL,
  session_id VARCHAR(255) UNIQUE,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_user_account_session_id ON user_account (session_id);

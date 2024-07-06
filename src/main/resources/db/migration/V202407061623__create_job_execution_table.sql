CREATE TABLE job_execution
(
  id                 BIGSERIAL PRIMARY KEY,
  job_name           VARCHAR(255)             NOT NULL,
  start_time         TIMESTAMP WITH TIME ZONE NOT NULL,
  end_time           TIMESTAMP WITH TIME ZONE NOT NULL,
  duration_in_millis BIGINT                   NOT NULL,
  status             VARCHAR(50)              NOT NULL,
  message            TEXT,
  created_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_job_execution_job_name ON job_execution (job_name);
CREATE INDEX idx_job_execution_start_time ON job_execution (start_time);

CREATE TABLE async_job (
    id BIGSERIAL PRIMARY KEY,
    job_type VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    parameters TEXT,
    result TEXT,
    error_message TEXT,
    progress INTEGER DEFAULT 0,
    total_items INTEGER,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by VARCHAR(100) NOT NULL DEFAULT 'system',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_async_job_status ON async_job(status);
CREATE INDEX idx_async_job_type_status ON async_job(job_type, status);
CREATE INDEX idx_async_job_created_at ON async_job(created_at DESC);
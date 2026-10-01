CREATE TABLE order_data (
    id BIGINT PRIMARY KEY,
    customer_name VARCHAR(100) NOT NULL,
    email VARCHAR(200) NOT NULL,
    amount NUMERIC(12, 2) NOT NULL,
    ordered_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE export_jobs (
    id UUID PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED')),
    requested_at TIMESTAMPTZ NOT NULL,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    lease_until TIMESTAMPTZ,
    logical_file_path VARCHAR(300),
    error_code VARCHAR(100),
    error_message VARCHAR(500)
);

CREATE INDEX idx_export_jobs_requested_at ON export_jobs (requested_at DESC);
CREATE INDEX idx_export_jobs_pending ON export_jobs (requested_at) WHERE status = 'PENDING';
CREATE INDEX idx_export_jobs_expired_lease ON export_jobs (lease_until) WHERE status = 'PROCESSING';

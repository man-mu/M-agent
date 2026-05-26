CREATE TABLE IF NOT EXISTS research_reports (
    id UUID PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL UNIQUE,
    session_id VARCHAR(255) NOT NULL,
    query TEXT NOT NULL,
    report TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_research_reports_session_id
    ON research_reports (session_id);

CREATE INDEX IF NOT EXISTS idx_research_reports_updated_at
    ON research_reports (updated_at);

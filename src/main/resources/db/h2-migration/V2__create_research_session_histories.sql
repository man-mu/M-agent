CREATE TABLE IF NOT EXISTS research_session_histories (
    id UUID PRIMARY KEY,
    thread_id VARCHAR(255) NOT NULL UNIQUE,
    session_id VARCHAR(255) NOT NULL,
    query TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    report_thread_id VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    stopped_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_research_session_histories_session_id
    ON research_session_histories (session_id);

CREATE INDEX IF NOT EXISTS idx_research_session_histories_session_updated_at
    ON research_session_histories (session_id, updated_at);

CREATE INDEX IF NOT EXISTS idx_research_session_histories_status
    ON research_session_histories (status);

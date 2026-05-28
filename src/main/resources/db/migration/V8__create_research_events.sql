CREATE TABLE IF NOT EXISTS research_events (
    id UUID PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    thread_id VARCHAR(255) NOT NULL,
    sequence BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    node_name VARCHAR(255),
    node_type VARCHAR(128),
    executor_id INTEGER,
    step_id VARCHAR(255),
    phase VARCHAR(128),
    status VARCHAR(255),
    display_title VARCHAR(255),
    done BOOLEAN NOT NULL DEFAULT FALSE,
    event_timestamp TIMESTAMP WITH TIME ZONE NOT NULL,
    event_json TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (thread_id, sequence)
);

CREATE INDEX IF NOT EXISTS idx_research_events_session_id
    ON research_events (session_id);

CREATE INDEX IF NOT EXISTS idx_research_events_thread_sequence
    ON research_events (thread_id, sequence);

CREATE INDEX IF NOT EXISTS idx_research_events_session_thread_sequence
    ON research_events (session_id, thread_id, sequence);

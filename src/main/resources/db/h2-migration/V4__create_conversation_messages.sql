CREATE TABLE IF NOT EXISTS conversation_messages (
    id UUID PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    thread_id VARCHAR(255) NOT NULL,
    role VARCHAR(10) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_session_id
    ON conversation_messages (session_id);

CREATE INDEX IF NOT EXISTS idx_conversation_messages_session_created
    ON conversation_messages (session_id, created_at);

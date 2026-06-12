CREATE INDEX IF NOT EXISTS idx_conv_msg_role
    ON conversation_messages (session_id, role, created_at);

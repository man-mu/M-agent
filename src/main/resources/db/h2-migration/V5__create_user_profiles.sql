CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    profile_summary TEXT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_user_profiles_session_id
    ON user_profiles (session_id);

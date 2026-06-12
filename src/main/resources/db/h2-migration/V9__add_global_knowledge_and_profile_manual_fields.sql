-- 全局文档元数据表
CREATE TABLE IF NOT EXISTS rag_documents (
    id UUID PRIMARY KEY,
    file_name VARCHAR(500) NOT NULL,
    scope VARCHAR(20) NOT NULL DEFAULT 'session',
    session_id VARCHAR(100),
    user_id VARCHAR(100),
    chunks INT NOT NULL DEFAULT 0,
    uploaded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_rag_documents_scope ON rag_documents(scope);
CREATE INDEX IF NOT EXISTS idx_rag_documents_session ON rag_documents(session_id);

-- 用户画像新增列
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS scope VARCHAR(20) NOT NULL DEFAULT 'session';
ALTER TABLE user_profiles ADD COLUMN IF NOT EXISTS manual_fields TEXT NOT NULL DEFAULT '[]';
CREATE INDEX IF NOT EXISTS idx_user_profiles_scope ON user_profiles(scope);

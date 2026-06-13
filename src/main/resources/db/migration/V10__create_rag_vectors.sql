-- 创建 RAG 向量表，用于存储文档 embedding
-- 维度 1536 对应 DashScope text-embedding-v1 模型
CREATE TABLE IF NOT EXISTS rag_vectors (
    id VARCHAR(255) PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(1536)
);

-- 创建 HNSW 索引以加速向量检索
CREATE INDEX IF NOT EXISTS rag_vectors_embedding_idx
    ON rag_vectors USING hnsw (embedding vector_cosine_ops);

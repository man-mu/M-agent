package top.lanshan.manmu.rag;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("rag_documents")
public class RagDocumentEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity;

    @Column("file_name")
    private String fileName;

    @Column("scope")
    private String scope;

    @Column("session_id")
    private String sessionId;

    @Column("user_id")
    private String userId;

    @Column("chunks")
    private int chunks;

    @Column("uploaded_at")
    private Instant uploadedAt;

    @Override
    public UUID getId() { return id; }

    @Override
    public boolean isNew() { return newEntity; }

    public void setId(UUID id) { this.id = id; }
    public void setNewEntity(boolean newEntity) { this.newEntity = newEntity; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public int getChunks() { return chunks; }
    public void setChunks(int chunks) { this.chunks = chunks; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}

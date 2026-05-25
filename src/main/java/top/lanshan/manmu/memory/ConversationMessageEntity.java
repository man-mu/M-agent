package top.lanshan.manmu.memory;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("conversation_messages")
public class ConversationMessageEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newMessage;

    @Column("session_id")
    private String sessionId;

    @Column("thread_id")
    private String threadId;

    private String role;

    private String content;

    @Column("created_at")
    private Instant createdAt;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newMessage;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setNewMessage(boolean newMessage) {
        this.newMessage = newMessage;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    ConversationMessageRecord toRecord() {
        return new ConversationMessageRecord(sessionId, threadId, role, content, createdAt);
    }
}

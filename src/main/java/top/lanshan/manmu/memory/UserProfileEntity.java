package top.lanshan.manmu.memory;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("user_profiles")
public class UserProfileEntity implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean newEntity;

    @Column("session_id")
    private String sessionId;

    @Column("profile_summary")
    private String profileSummary;

    @Column("expertise_level")
    private String expertiseLevel;

    @Column("detail_preference")
    private String detailPreference;

    @Column("style_preference")
    private String stylePreference;

    @Column("updated_at")
    private Instant updatedAt;

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public void setNewEntity(boolean newEntity) {
        this.newEntity = newEntity;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getProfileSummary() {
        return profileSummary;
    }

    public void setProfileSummary(String profileSummary) {
        this.profileSummary = profileSummary;
    }

    public String getExpertiseLevel() {
        return expertiseLevel;
    }

    public void setExpertiseLevel(String expertiseLevel) {
        this.expertiseLevel = expertiseLevel;
    }

    public String getDetailPreference() {
        return detailPreference;
    }

    public void setDetailPreference(String detailPreference) {
        this.detailPreference = detailPreference;
    }

    public String getStylePreference() {
        return stylePreference;
    }

    public void setStylePreference(String stylePreference) {
        this.stylePreference = stylePreference;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    UserProfileRecord toRecord() {
        return new UserProfileRecord(sessionId, profileSummary, expertiseLevel, detailPreference, stylePreference, updatedAt);
    }
}

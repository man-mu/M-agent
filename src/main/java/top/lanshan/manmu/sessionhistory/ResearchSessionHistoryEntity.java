package top.lanshan.manmu.sessionhistory;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.UUID;

@Table("research_session_histories")
public class ResearchSessionHistoryEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newHistory;

	@Column("thread_id")
	private String threadId;

	@Column("session_id")
	private String sessionId;

	private String query;

	private String status;

	@Column("report_thread_id")
	private String reportThreadId;

	@Column("error_message")
	private String errorMessage;

	@Column("created_at")
	private Instant createdAt;

	@Column("updated_at")
	private Instant updatedAt;

	@Column("completed_at")
	private Instant completedAt;

	@Column("stopped_at")
	private Instant stoppedAt;

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	@Override
	public boolean isNew() {
		return newHistory;
	}

	public void setNewHistory(boolean newHistory) {
		this.newHistory = newHistory;
	}

	public String getThreadId() {
		return threadId;
	}

	public void setThreadId(String threadId) {
		this.threadId = threadId;
	}

	public String getSessionId() {
		return sessionId;
	}

	public void setSessionId(String sessionId) {
		this.sessionId = sessionId;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getReportThreadId() {
		return reportThreadId;
	}

	public void setReportThreadId(String reportThreadId) {
		this.reportThreadId = reportThreadId;
	}

	public String getErrorMessage() {
		return errorMessage;
	}

	public void setErrorMessage(String errorMessage) {
		this.errorMessage = errorMessage;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getCompletedAt() {
		return completedAt;
	}

	public void setCompletedAt(Instant completedAt) {
		this.completedAt = completedAt;
	}

	public Instant getStoppedAt() {
		return stoppedAt;
	}

	public void setStoppedAt(Instant stoppedAt) {
		this.stoppedAt = stoppedAt;
	}

	ResearchSessionHistory toHistory() {
		return new ResearchSessionHistory(threadId, sessionId, query, status, reportThreadId, errorMessage, createdAt,
				updatedAt, completedAt, stoppedAt);
	}

}

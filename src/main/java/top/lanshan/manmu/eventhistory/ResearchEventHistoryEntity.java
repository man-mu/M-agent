package top.lanshan.manmu.eventhistory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
import top.lanshan.manmu.model.ChatStreamResponse;

import java.time.Instant;
import java.util.UUID;

@Table("research_events")
public class ResearchEventHistoryEntity implements Persistable<UUID> {

	@Id
	private UUID id;

	@Transient
	private boolean newEvent;

	@Column("session_id")
	private String sessionId;

	@Column("thread_id")
	private String threadId;

	private Long sequence;

	@Column("event_type")
	private String eventType;

	@Column("node_name")
	private String nodeName;

	@Column("node_type")
	private String nodeType;

	@Column("executor_id")
	private Integer executorId;

	@Column("step_id")
	private String stepId;

	private String phase;

	private String status;

	@Column("display_title")
	private String displayTitle;

	private boolean done;

	@Column("event_timestamp")
	private Instant eventTimestamp;

	@Column("event_json")
	private String eventJson;

	@Column("created_at")
	private Instant createdAt;

	@Override
	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	@Override
	public boolean isNew() {
		return newEvent;
	}

	public void setNewEvent(boolean newEvent) {
		this.newEvent = newEvent;
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

	public Long getSequence() {
		return sequence;
	}

	public void setSequence(Long sequence) {
		this.sequence = sequence;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public String getNodeName() {
		return nodeName;
	}

	public void setNodeName(String nodeName) {
		this.nodeName = nodeName;
	}

	public String getNodeType() {
		return nodeType;
	}

	public void setNodeType(String nodeType) {
		this.nodeType = nodeType;
	}

	public Integer getExecutorId() {
		return executorId;
	}

	public void setExecutorId(Integer executorId) {
		this.executorId = executorId;
	}

	public String getStepId() {
		return stepId;
	}

	public void setStepId(String stepId) {
		this.stepId = stepId;
	}

	public String getPhase() {
		return phase;
	}

	public void setPhase(String phase) {
		this.phase = phase;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getDisplayTitle() {
		return displayTitle;
	}

	public void setDisplayTitle(String displayTitle) {
		this.displayTitle = displayTitle;
	}

	public boolean isDone() {
		return done;
	}

	public void setDone(boolean done) {
		this.done = done;
	}

	public Instant getEventTimestamp() {
		return eventTimestamp;
	}

	public void setEventTimestamp(Instant eventTimestamp) {
		this.eventTimestamp = eventTimestamp;
	}

	public String getEventJson() {
		return eventJson;
	}

	public void setEventJson(String eventJson) {
		this.eventJson = eventJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	ResearchEventRecord toRecord(ObjectMapper objectMapper) {
		return new ResearchEventRecord(sessionId, threadId, sequence, eventType, nodeName, nodeType, executorId, stepId,
				phase, status, displayTitle, done, eventTimestamp, createdAt, readEvent(objectMapper));
	}

	private ChatStreamResponse readEvent(ObjectMapper objectMapper) {
		try {
			return objectMapper.readValue(eventJson, ChatStreamResponse.class);
		}
		catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to read stored research event JSON", ex);
		}
	}

}

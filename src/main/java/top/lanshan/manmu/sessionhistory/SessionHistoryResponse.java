package top.lanshan.manmu.sessionhistory;

import com.fasterxml.jackson.annotation.JsonProperty;

public record SessionHistoryResponse<T>(
		@JsonProperty("session_id") String sessionId,
		@JsonProperty("thread_id") String threadId,
		String status,
		String message,
		@JsonProperty("session_history") T data) {

	public static <T> SessionHistoryResponse<T> success(String sessionId, String threadId, String message, T data) {
		return new SessionHistoryResponse<>(sessionId, threadId, "success", message, data);
	}

	public static <T> SessionHistoryResponse<T> notFound(String sessionId, String threadId, String message) {
		return new SessionHistoryResponse<>(sessionId, threadId, "notfound", message, null);
	}

	public static <T> SessionHistoryResponse<T> error(String sessionId, String threadId, String message) {
		return new SessionHistoryResponse<>(sessionId, threadId, "error", message, null);
	}

}

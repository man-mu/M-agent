package top.lanshan.manmu.report;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReportResponse<T>(
		@JsonProperty("thread_id") String threadId,
		String status,
		String message,
		@JsonProperty("report_information") T data) {

	public static <T> ReportResponse<T> success(String threadId, String message, T data) {
		return new ReportResponse<>(threadId, "success", message, data);
	}

	public static <T> ReportResponse<T> notFound(String threadId, String message) {
		return new ReportResponse<>(threadId, "notfound", message, null);
	}

	public static <T> ReportResponse<T> error(String threadId, String message) {
		return new ReportResponse<>(threadId, "error", message, null);
	}

}

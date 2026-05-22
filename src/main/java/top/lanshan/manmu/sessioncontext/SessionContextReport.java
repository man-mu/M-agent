package top.lanshan.manmu.sessioncontext;

import java.time.Instant;

public record SessionContextReport(String threadId, String reportThreadId, String query, String reportExcerpt,
		Instant completedAt) {
}

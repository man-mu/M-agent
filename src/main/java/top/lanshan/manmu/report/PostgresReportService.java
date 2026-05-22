package top.lanshan.manmu.report;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostgresReportService implements ReportService {

	private final ResearchReportRepository repository;

	public PostgresReportService(ResearchReportRepository repository) {
		this.repository = repository;
	}

	@Override
	public Mono<ResearchReport> saveCompletedReport(String threadId, String sessionId, String query, String report) {
		Instant now = Instant.now();
		return repository.findByThreadId(threadId)
			.defaultIfEmpty(newEntity(threadId, sessionId, now))
			.flatMap(entity -> {
				entity.setSessionId(sessionId);
				entity.setQuery(query);
				entity.setReport(report);
				entity.setStatus(ReportStatus.COMPLETED.name());
				entity.setErrorMessage(null);
				entity.setUpdatedAt(now);
				return repository.save(entity);
			})
			.map(ResearchReportEntity::toReport);
	}

	@Override
	public Mono<String> getReport(String threadId) {
		return repository.findByThreadId(threadId)
			.filter(entity -> ReportStatus.COMPLETED.name().equals(entity.getStatus()))
			.map(ResearchReportEntity::getReport);
	}

	@Override
	public Mono<Boolean> existsReport(String threadId) {
		return repository.existsByThreadId(threadId);
	}

	@Override
	public Mono<Void> deleteReport(String threadId) {
		return repository.deleteByThreadId(threadId);
	}

	@Override
	public Flux<ResearchReport> findBySessionId(String sessionId) {
		return repository.findBySessionIdOrderByUpdatedAtDesc(sessionId).map(ResearchReportEntity::toReport);
	}

	private ResearchReportEntity newEntity(String threadId, String sessionId, Instant now) {
		ResearchReportEntity entity = new ResearchReportEntity();
		entity.setNewReport(true);
		entity.setId(UUID.randomUUID());
		entity.setThreadId(threadId);
		entity.setSessionId(sessionId);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		return entity;
	}

}

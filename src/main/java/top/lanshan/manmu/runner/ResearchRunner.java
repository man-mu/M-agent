package top.lanshan.manmu.runner;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchRequest;

public interface ResearchRunner {

	Flux<ResearchEvent> run(ResearchRequest request);

	Flux<ResearchEvent> runChat(ResearchRequest request, String sessionId);

	Flux<ResearchEvent> runUntilPlanGate(ResearchRequest request, String sessionId);

	Flux<ResearchEvent> resume(String threadId, ResumeDecision decision);

	Mono<Boolean> stopAndRecord(String threadId);

}

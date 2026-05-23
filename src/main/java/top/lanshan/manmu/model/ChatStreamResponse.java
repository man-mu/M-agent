package top.lanshan.manmu.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record ChatStreamResponse(@JsonProperty("nodeName") String nodeName,
		@JsonProperty("graphId") GraphId graphId,
		@JsonProperty("displayTitle") String displayTitle,
		@JsonProperty("content") Object content,
		@JsonProperty("siteInformation") Object siteInformation,
		@JsonProperty("sequence") Long sequence,
		@JsonProperty("event_type") ResearchStreamEventType eventType,
		@JsonProperty("node_name") String stableNodeName,
		@JsonProperty("node_type") String nodeType,
		@JsonProperty("executor_id") Integer executorId,
		@JsonProperty("step_id") String stepId,
		@JsonProperty("phase") String phase,
		@JsonProperty("status") String status,
		@JsonProperty("display_title") String stableDisplayTitle,
		@JsonProperty("payload") Object payload,
		@JsonProperty("site_information") Object stableSiteInformation,
		@JsonProperty("done") boolean done,
		@JsonProperty("timestamp") Instant timestamp,
		@JsonProperty("graph_id") GraphId stableGraphId) {

	public static ChatStreamResponse from(GraphId graphId, ResearchEvent event, Object content,
			Object siteInformation) {
		ResearchNodeMetadata metadata = ResearchNodeMetadata.from(event.node());
		ResearchStreamEventType eventType = event.eventType() == null ? ResearchStreamEventType.from(event)
				: event.eventType();
		String nodeName = event.nodeName() == null ? metadata.nodeName() : event.nodeName();
		String nodeType = event.nodeType() == null ? metadata.nodeType() : event.nodeType();
		Integer executorId = event.executorId() == null ? metadata.executorId() : event.executorId();
		String displayTitle = event.displayTitle() == null ? metadata.displayTitle() : event.displayTitle();
		String status = event.status() == null ? event.phase() : event.status();
		return new ChatStreamResponse(nodeName, graphId, displayTitle, content, siteInformation, event.sequence(),
				eventType, nodeName, nodeType, executorId, event.stepId(), event.phase(), status, displayTitle,
				event.payload(), siteInformation, event.done(), event.timestamp(), graphId);
	}

}

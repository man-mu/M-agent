package top.lanshan.manmu.model;

public record ChatStreamResponse(String nodeName, GraphId graphId, String displayTitle, Object content,
		Object siteInformation) {
}

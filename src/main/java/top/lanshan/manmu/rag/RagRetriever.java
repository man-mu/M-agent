package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RagRetriever {

    private static final Logger logger = LoggerFactory.getLogger(RagRetriever.class);

    private final VectorStore vectorStore;

    private final int topK;

    private final double similarityThreshold;

    public RagRetriever(VectorStore vectorStore, int topK, double similarityThreshold) {
        this.vectorStore = vectorStore;
        this.topK = topK;
        this.similarityThreshold = similarityThreshold;
    }

    public List<Document> retrieve(String query, Map<String, Object> filterMetadata) {
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        List<Document> filtered = results.stream()
            .filter(doc -> filterMetadata.entrySet().stream()
                .allMatch(e -> e.getValue().equals(doc.getMetadata().get(e.getKey()))))
            .toList();
        logger.info("Retrieved {} docs (filtered to {}) [threshold={}, topK={}] for query: {}",
                results.size(), filtered.size(), similarityThreshold, topK,
                query.length() > 100 ? query.substring(0, 100) + "..." : query);
        return filtered;
    }

    public List<Document> retrieveWithGlobal(String query, String sessionId) {
        // 查询全局文档
        Map<String, Object> globalFilters = Map.of("scope", "global");
        List<Document> globalDocs = retrieve(query, globalFilters);

        // 查询会话级文档
        List<Document> sessionDocs = List.of();
        if (sessionId != null && !sessionId.isBlank()) {
            Map<String, Object> sessionFilters = Map.of("scope", "session", "session_id", sessionId);
            sessionDocs = retrieve(query, sessionFilters);
        }

        // 合并去重（按文本内容去重），全局文档优先
        Map<String, Document> merged = new LinkedHashMap<>();
        for (Document doc : globalDocs) {
            merged.putIfAbsent(doc.getText(), doc);
        }
        for (Document doc : sessionDocs) {
            merged.putIfAbsent(doc.getText(), doc);
        }

        List<Document> result = merged.values().stream().toList();
        logger.info("retrieveWithGlobal: global={}, session={}, merged={} for query: {}",
                globalDocs.size(), sessionDocs.size(), result.size(),
                query.length() > 80 ? query.substring(0, 80) + "..." : query);
        return result;
    }

    public String buildContext(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        return documents.stream()
            .map(doc -> {
                String source = doc.getMetadata().getOrDefault("original_filename", "unknown").toString();
                return "[来源: " + source + "]\n" + doc.getText();
            })
            .collect(Collectors.joining("\n\n---\n\n"));
    }

}

package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

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
        logger.info("Retrieved {} documents (filtered to {}) for query: {}", results.size(), filtered.size(),
                query.length() > 100 ? query.substring(0, 100) + "..." : query);
        return filtered;
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

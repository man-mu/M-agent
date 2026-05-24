package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder.Op;

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
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Op filter = null;
        for (Map.Entry<String, Object> entry : filterMetadata.entrySet()) {
            Op expr = builder.eq(entry.getKey(), entry.getValue());
            filter = filter == null ? expr : builder.and(filter, expr);
        }
        SearchRequest request = SearchRequest.builder()
            .query(query)
            .topK(topK)
            .similarityThreshold(similarityThreshold)
            .filterExpression(filter != null
                    ? filter.build() : new FilterExpressionBuilder().eq("source_type", "user_upload").build())
            .build();
        List<Document> results = vectorStore.similaritySearch(request);
        logger.info("Retrieved {} documents for query: {}", results.size(),
                query.length() > 100 ? query.substring(0, 100) + "..." : query);
        return results;
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

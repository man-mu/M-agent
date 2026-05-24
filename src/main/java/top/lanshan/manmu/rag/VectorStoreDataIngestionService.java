package top.lanshan.manmu.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class VectorStoreDataIngestionService {

    private static final Logger logger = LoggerFactory.getLogger(VectorStoreDataIngestionService.class);

    private final VectorStore vectorStore;

    private final TokenTextSplitter textSplitter;

    public VectorStoreDataIngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.textSplitter = TokenTextSplitter.builder()
            .withChunkSize(800)
            .withMinChunkSizeChars(5)
            .withMinChunkLengthToEmbed(50)
            .withMaxNumChunks(100)
            .withKeepSeparator(true)
            .build();
    }

    public int ingest(Resource resource, String sessionId, String userId) {
        List<Document> documents = new TikaDocumentReader(resource).read();
        logger.info("Read {} documents from resource", documents.size());

        List<Document> chunks = textSplitter.split(documents);
        logger.info("Split into {} chunks", chunks.size());

        Instant timestamp = Instant.now();
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            Map<String, Object> metadata = new java.util.HashMap<>(chunk.getMetadata());
            metadata.put("source_type", "user_upload");
            metadata.put("session_id", sessionId);
            metadata.put("user_id", userId);
            metadata.put("chunk_id", i);
            metadata.put("original_filename", resource.getFilename());
            metadata.put("upload_timestamp", timestamp.toString());
            metadata.put("file_size", contentLengthSafe(resource));
            chunk.getMetadata().putAll(metadata);
        }

        vectorStore.add(chunks);
        logger.info("Ingested {} chunks into vector store for session={}", chunks.size(), sessionId);
        return chunks.size();
    }

    private long contentLengthSafe(Resource resource) {
        try {
            return resource.contentLength();
        }
        catch (java.io.IOException e) {
            logger.warn("Could not determine content length for {}", resource.getFilename(), e);
            return -1;
        }
    }

}

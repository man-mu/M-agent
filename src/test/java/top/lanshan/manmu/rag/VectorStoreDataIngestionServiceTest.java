package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.ByteArrayResource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VectorStoreDataIngestionServiceTest {

    @Test
    void ingestSplitsMarkdownContentIntoChunks() {
        RecordingVectorStore vectorStore = new RecordingVectorStore();
        VectorStoreDataIngestionService service = new VectorStoreDataIngestionService(vectorStore);
        byte[] content = ("# Title\n\nThis is paragraph one.\n\n## Section\n\n"
                + "This is paragraph two with enough tokens to ensure splitting occurs. ").repeat(50).getBytes();

        int chunks = service.ingest(new ByteArrayResource(content), "session-1", "user-1");

        assertThat(chunks).isGreaterThan(1);
        assertThat(vectorStore.acceptedDocuments).hasSize(chunks);
        assertThat(vectorStore.acceptedDocuments.get(0).getMetadata())
            .containsEntry("source_type", "user_upload")
            .containsEntry("session_id", "session-1")
            .containsEntry("user_id", "user-1");
        assertThat(vectorStore.acceptedDocuments.get(0).getMetadata())
            .containsKeys("chunk_id", "original_filename", "upload_timestamp");
    }

    static class RecordingVectorStore implements VectorStore {

        final List<Document> acceptedDocuments = new ArrayList<>();

        @Override
        public void add(List<Document> documents) {
            acceptedDocuments.addAll(documents);
        }

        @Override
        public List<Document> similaritySearch(SearchRequest request) {
            return List.of();
        }

        @Override
        public List<Document> similaritySearch(String query) {
            return List.of();
        }

        @Override
        public void delete(List<String> idList) {
        }

        @Override
        public void delete(Filter.Expression filterExpression) {
        }

    }

}

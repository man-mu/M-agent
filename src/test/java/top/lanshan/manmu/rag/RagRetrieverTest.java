package top.lanshan.manmu.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RagRetrieverTest {

    @Test
    void buildContextFormatsDocumentsWithSourceLabels() {
        RagRetriever retriever = new RagRetriever(null, 5, 0.7);
        Document doc1 = new Document("Content from file A.", Map.of("original_filename", "report.md"));
        Document doc2 = new Document("Content from file B.", Map.of("original_filename", "notes.pdf"));

        String context = retriever.buildContext(List.of(doc1, doc2));

        assertThat(context).contains("[来源: report.md]");
        assertThat(context).contains("[来源: notes.pdf]");
        assertThat(context).contains("Content from file A.");
        assertThat(context).contains("Content from file B.");
        assertThat(context).contains("---");
    }

    @Test
    void buildContextReturnsEmptyForNoDocuments() {
        RagRetriever retriever = new RagRetriever(null, 5, 0.7);

        assertThat(retriever.buildContext(List.of())).isEmpty();
    }

}

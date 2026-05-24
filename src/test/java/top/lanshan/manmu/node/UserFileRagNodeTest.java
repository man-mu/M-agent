package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.core.io.DefaultResourceLoader;
import reactor.test.StepVerifier;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchPlan;
import top.lanshan.manmu.model.ResearchRequest;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.StepType;
import top.lanshan.manmu.rag.RagRetriever;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UserFileRagNodeTest {

    @Test
    void emitsNoEventsWhenNoDocumentsRetrieved() {
        VectorStore emptyStore = new StubVectorStore(List.of());
        RagRetriever retriever = new RagRetriever(emptyStore, 5, 0.7);
        UserFileRagNode node = new UserFileRagNode(retriever, ChatClient.builder(new StubChatModel("unused")),
                new DefaultResourceLoader());
        ResearchState state = stateWithQueryAndSession("What is AI?", "session-test");

        StepVerifier.create(node.run(state)).verifyComplete();
    }

    private ResearchState stateWithQueryAndSession(String query, String sessionId) {
        ResearchState state = ResearchState.from(new ResearchRequest(query, "thread-1", 2));
        ResearchPlan plan = new ResearchPlan("Test", true, "Test plan", List.of(
                new ResearchStep("Step", "Desc", false, StepType.RESEARCH, null, "pending")));
        state.plan(plan);
        return state;
    }

    static class StubVectorStore implements VectorStore {
        private final List<Document> documents;
        StubVectorStore(List<Document> documents) { this.documents = documents; }
        @Override public void add(List<Document> docs) {}
        @Override public List<Document> similaritySearch(SearchRequest request) { return documents; }
        @Override public List<Document> similaritySearch(String query) { return documents; }
        @Override public void delete(List<String> ids) {}
        @Override public void delete(Filter.Expression filterExpression) {}
    }

    static class StubChatModel implements ChatModel {
        private final String response;
        StubChatModel(String response) { this.response = response; }
        @Override public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(response))));
        }
        @Override public ChatOptions getDefaultOptions() {
            return null;
        }
    }

}

package top.lanshan.manmu.node;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.client.ChatClient;
import top.lanshan.manmu.config.RagProperties;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProfessionalKbDecisionNodeTest {

    private ProfessionalKbDecisionNode node;

    private List<RagProperties.ProfessionalKnowledgeBases.KnowledgeBase> enabledKbs;

    @BeforeEach
    void setUp() {
        RagProperties ragProperties = new RagProperties();
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        node = new ProfessionalKbDecisionNode(builder, ragProperties);

        RagProperties.ProfessionalKnowledgeBases.KnowledgeBase kb1 =
                new RagProperties.ProfessionalKnowledgeBases.KnowledgeBase();
        kb1.setId("java-api");
        kb1.setName("Java API");
        kb1.setEnabled(true);
        kb1.setPriority(100);

        RagProperties.ProfessionalKnowledgeBases.KnowledgeBase kb2 =
                new RagProperties.ProfessionalKnowledgeBases.KnowledgeBase();
        kb2.setId("spring-docs");
        kb2.setName("Spring Docs");
        kb2.setEnabled(true);
        kb2.setPriority(90);

        enabledKbs = List.of(kb1, kb2);
    }

    @Test
    void parsesSingleSelectedKb() {
        List<String> selected = node.parseSelection("SELECTED: [java-api]", enabledKbs);
        assertThat(selected).containsExactly("java-api");
    }

    @Test
    void parsesMultipleSelectedKbs() {
        List<String> selected = node.parseSelection("SELECTED: [java-api, spring-docs]", enabledKbs);
        assertThat(selected).containsExactly("java-api", "spring-docs");
    }

    @Test
    void returnsEmptyForNoSelection() {
        List<String> selected = node.parseSelection("SELECTED: []", enabledKbs);
        assertThat(selected).isEmpty();
    }

    @Test
    void filtersInvalidKbIds() {
        List<String> selected = node.parseSelection("SELECTED: [java-api, unknown-kb]", enabledKbs);
        assertThat(selected).containsExactly("java-api");
    }

    @Test
    void returnsEmptyForMalformedResponse() {
        List<String> selected = node.parseSelection("garbage response without marker", enabledKbs);
        assertThat(selected).isEmpty();
    }

    @Test
    void returnsEmptyForEmptyResponse() {
        List<String> selected = node.parseSelection("", enabledKbs);
        assertThat(selected).isEmpty();
    }
}

package top.lanshan.manmu.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RagPropertiesTest {

    @Test
    void defaultsDisableRag() {
        RagProperties properties = new RagProperties();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void defaultTopKIsFive() {
        RagProperties properties = new RagProperties();
        assertThat(properties.getTopK()).isEqualTo(5);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.7);
    }

    @Test
    void settersOverrideDefaults() {
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        properties.setTopK(10);
        properties.setSimilarityThreshold(0.85);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getTopK()).isEqualTo(10);
        assertThat(properties.getSimilarityThreshold()).isEqualTo(0.85);
    }

    @Test
    void professionalKnowledgeBasesDefaultEmpty() {
        RagProperties properties = new RagProperties();
        assertThat(properties.getProfessionalKnowledgeBases().getKnowledgeBases()).isEmpty();
        assertThat(properties.getProfessionalKnowledgeBases().isDecisionEnabled()).isTrue();
    }

}

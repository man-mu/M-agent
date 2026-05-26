package top.lanshan.manmu.graph;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.config.RagProperties;
import top.lanshan.manmu.model.ResearchEvent;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.node.ResearchNode;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchGraphProfessionalKbTest {

    private static List<ResearchNode> baseNodes() {
        return new ArrayList<>(List.of(
                new StubNode("coordinator", 1),
                new StubNode("rewrite_multi_query", 5),
                new StubNode("background_investigator", 7),
                new StubNode("planner", 10),
                new StubNode("plan_validator", 15),
                new StubNode("information", 20),
                new StubNode("research_team", 30),
                new StubNode("parallel_executor", 35),
                new StubNode("reporter", 50),
                new StubNode("researcher_0", 36),
                new StubNode("researcher_1", 37),
                new StubNode("coder_0", 38)));
    }

    @Test
    void graphBuildsSuccessfullyWithProfessionalKbNodes() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);
        ragProperties.getProfessionalKnowledgeBases().setDecisionEnabled(true);

        List<ResearchNode> nodes = baseNodes();
        nodes.add(new StubNode("professional_kb_decision", 32));
        nodes.add(new StubNode("professional_kb_rag", 33));

        ResearchGraphBuilder builder = new ResearchGraphBuilder(nodes,
                new AdvancedExecutionProperties(), null, null, ragProperties);
        CompiledGraph graph = builder.buildAutoResearchGraph();

        assertThat(graph).isNotNull();
    }

    @Test
    void graphBuildsSuccessfullyWhenRagDisabled() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(false);

        ResearchGraphBuilder builder = new ResearchGraphBuilder(baseNodes(),
                new AdvancedExecutionProperties(), null, null, ragProperties);
        CompiledGraph graph = builder.buildAutoResearchGraph();

        assertThat(graph).isNotNull();
    }

    @Test
    void allThreeGraphVariantsBuildWithProfessionalKbNodes() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);

        List<ResearchNode> nodes = baseNodes();
        nodes.add(new StubNode("human_feedback", 17));
        nodes.add(new StubNode("professional_kb_decision", 32));
        nodes.add(new StubNode("professional_kb_rag", 33));

        ResearchGraphBuilder builder = new ResearchGraphBuilder(nodes,
                new AdvancedExecutionProperties(), null, null, ragProperties);

        assertThat(builder.buildAutoResearchGraph()).isNotNull();
        assertThat(builder.buildPlanGateResearchGraph(null)).isNotNull();
        assertThat(builder.buildAcceptedResumeGraph()).isNotNull();
    }

    @Test
    void graphMissingProfessionalKbNodesDoesNotCrashWhenRagEnabled() {
        RagProperties ragProperties = new RagProperties();
        ragProperties.setEnabled(true);

        ResearchGraphBuilder builder = new ResearchGraphBuilder(baseNodes(),
                new AdvancedExecutionProperties(), null, null, ragProperties);
        CompiledGraph graph = builder.buildAutoResearchGraph();

        assertThat(graph).isNotNull();
    }

    private static class StubNode implements ResearchNode {

        private final String name;
        private final int order;

        StubNode(String name, int order) {
            this.name = name;
            this.order = order;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int order() {
            return order;
        }

        @Override
        public Flux<ResearchEvent> run(ResearchState state) {
            return Flux.empty();
        }
    }
}

package top.lanshan.manmu.runner;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import top.lanshan.manmu.agent.ProcessorAgent;
import top.lanshan.manmu.agent.ResearcherAgent;
import top.lanshan.manmu.config.AdvancedExecutionProperties;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.node.ResearchNode;

import java.util.List;

@Configuration
public class ResearchRunnerConfiguration {

	@Bean
	ResearchGraphBuilder researchGraphBuilder(List<ResearchNode> nodes,
			ObjectProvider<AdvancedExecutionProperties> properties,
			ObjectProvider<ResearcherAgent> researcherAgent,
			ObjectProvider<ProcessorAgent> processorAgent) {
		return new ResearchGraphBuilder(nodes, properties.getIfAvailable(AdvancedExecutionProperties::new),
				researcherAgent.getIfAvailable(), processorAgent.getIfAvailable());
	}

}

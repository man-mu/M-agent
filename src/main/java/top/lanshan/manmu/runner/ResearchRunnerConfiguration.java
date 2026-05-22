package top.lanshan.manmu.runner;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import top.lanshan.manmu.graph.ResearchGraphBuilder;
import top.lanshan.manmu.node.ResearchNode;

import java.util.List;

@Configuration
@EnableConfigurationProperties(ResearchRunnerProperties.class)
public class ResearchRunnerConfiguration {

	@Bean
	ResearchGraphBuilder researchGraphBuilder(List<ResearchNode> nodes) {
		return new ResearchGraphBuilder(nodes);
	}

}

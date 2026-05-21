package top.lanshan.manmu.agent;

import org.springframework.stereotype.Component;
import top.lanshan.manmu.agent.client.AgentClient;
import top.lanshan.manmu.model.ResearchState;
import top.lanshan.manmu.model.ResearchStep;
import top.lanshan.manmu.model.SiteInformation;
import top.lanshan.manmu.prompt.PromptService;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
public class LlmProcessorAgent implements ProcessorAgent {

	private final AgentClient agentClient;

	private final PromptService promptService;

	public LlmProcessorAgent(AgentClient agentClient, PromptService promptService) {
		this.agentClient = agentClient;
		this.promptService = promptService;
	}

	@Override
	public String process(ResearchState state, ResearchStep step) {
		String userPrompt = """
				Query:
				%s

				Plan:
				- Title: %s
				- Thought: %s

				Current processing step:
				- Title: %s
				- Description: %s

				Prior observations:
				%s

				Web search sources collected earlier:
				%s

				Use the prior observations and web search sources as the working context. Produce a compact Markdown
				processing result for this step that synthesizes, organizes, or transforms the available information for
				the final report. Do not invent external facts that are not supported by the provided context.
				""".formatted(state.query(), state.plan().title(), state.plan().thought(), step.title(),
				step.description(), observationsText(state), siteInformationText(state));
		return agentClient.call(promptService.load("processor"), userPrompt);
	}

	private String observationsText(ResearchState state) {
		if (state.observations().isEmpty()) {
			return "No prior observations are available.";
		}
		return IntStream.range(0, state.observations().size())
			.mapToObj(index -> "Observation %d\n%s".formatted(index + 1, state.observations().get(index)))
			.collect(Collectors.joining("\n\n"));
	}

	private String siteInformationText(ResearchState state) {
		if (state.siteInformation().isEmpty()) {
			return "No web search sources were collected.";
		}
		return IntStream.range(0, state.siteInformation().size())
			.mapToObj(index -> "Source %d\n%s".formatted(index + 1, siteText(state.siteInformation().get(index))))
			.collect(Collectors.joining("\n\n"));
	}

	private String siteText(SiteInformation site) {
		String text = site.promptText();
		return text.isBlank() ? site.toString() : text;
	}

}

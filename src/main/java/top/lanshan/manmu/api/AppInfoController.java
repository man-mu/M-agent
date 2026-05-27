package top.lanshan.manmu.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/app")
public class AppInfoController {

	private final AppCapabilities capabilities;

	public AppInfoController(@Value("${mvp.skill.enabled:false}") boolean skillEnabled,
			@Value("${mvp.rag.enabled:false}") boolean ragEnabled,
			@Value("${mvp.mcp.enabled:false}") boolean mcpEnabled) {
		this.capabilities = new AppCapabilities(skillEnabled, ragEnabled, mcpEnabled);
	}

	@GetMapping("/capabilities")
	public AppCapabilities capabilities() {
		return capabilities;
	}

	public record AppCapabilities(boolean skillEnabled, boolean ragEnabled, boolean mcpEnabled) {
	}

}

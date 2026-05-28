package top.lanshan.manmu.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(prefix = "mvp.mcp", name = "enabled", havingValue = "true")
public class McpStatusController {

    private final McpToolProvider toolProvider;

    public McpStatusController(McpToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    @GetMapping("/status")
    public McpToolProvider.McpStatus status() {
        return toolProvider.getStatus();
    }
}

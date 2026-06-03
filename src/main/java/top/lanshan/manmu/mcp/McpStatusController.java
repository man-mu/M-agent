package top.lanshan.manmu.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(prefix = "mvp.mcp", name = "enabled", havingValue = "true")
public class McpStatusController {

    private final McpToolProvider toolProvider;

    public McpStatusController(McpToolProvider toolProvider) {
        this.toolProvider = toolProvider;
    }

    @GetMapping("/status")
    public Mono<McpToolProvider.McpStatus> status() {
        return Mono.fromCallable(toolProvider::getStatus)
                .subscribeOn(Schedulers.boundedElastic());
    }
}

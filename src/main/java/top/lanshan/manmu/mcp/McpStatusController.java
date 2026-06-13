package top.lanshan.manmu.mcp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import top.lanshan.manmu.config.McpProperties;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mcp")
@ConditionalOnProperty(prefix = "mvp.mcp", name = "enabled", havingValue = "true")
public class McpStatusController {

    private final McpToolProvider toolProvider;
    private final McpServerConfigService configService;
    private final McpToolInvocationService invocationService;

    public McpStatusController(McpToolProvider toolProvider,
            McpServerConfigService configService,
            McpToolInvocationService invocationService) {
        this.toolProvider = toolProvider;
        this.configService = configService;
        this.invocationService = invocationService;
    }

    @GetMapping("/status")
    public Mono<McpToolProvider.McpStatus> status() {
        return Mono.fromCallable(toolProvider::getStatus)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @GetMapping("/servers")
    public Mono<List<McpServerConfigService.ManagedMcpServerInfo>> servers() {
        return Mono.fromCallable(configService::listServers)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/servers")
    public Mono<ResponseEntity<Object>> createServer(@RequestBody McpProperties.McpServerInfo request) {
        return Mono.fromCallable(() -> {
            try {
                McpServerConfigService.ManagedMcpServerInfo server = configService.create(request);
                toolProvider.clearCache();
                return ResponseEntity.status(201).body((Object) server);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body((Object) Map.of("error", "Failed to write MCP config"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/servers/from-json")
    public Mono<ResponseEntity<Object>> createServerFromJson(@RequestBody Map<String, Object> body) {
        return Mono.fromCallable(() -> {
            try {
                String json = body.get("json") instanceof String s ? s : "";
                String description = body.get("description") instanceof String s ? s : null;
                String apiKey = body.get("apiKey") instanceof String s ? s : null;

                McpServerConfigService.ManagedMcpServerInfo server =
                        configService.createFromModelScopeJson(json, description, apiKey);
                toolProvider.clearCache();
                return ResponseEntity.status(201).body((Object) server);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body((Object) Map.of("error", "Failed to write MCP config"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PutMapping("/servers/{id}")
    public Mono<ResponseEntity<Object>> updateServer(@PathVariable String id,
            @RequestBody McpProperties.McpServerInfo request) {
        return Mono.fromCallable(() -> {
            try {
                McpServerConfigService.ManagedMcpServerInfo server = configService.update(id, request);
                toolProvider.clearCache();
                return ResponseEntity.ok((Object) server);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body((Object) Map.of("error", "Failed to write MCP config"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @DeleteMapping("/servers/{id}")
    public Mono<ResponseEntity<Object>> deleteServer(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            try {
                configService.delete(id);
                toolProvider.clearCache();
                return ResponseEntity.status(204).body((Object) null);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body((Object) Map.of("error", "Failed to write MCP config"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PatchMapping("/servers/{id}/toggle")
    public Mono<ResponseEntity<Object>> toggleServer(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            try {
                McpServerConfigService.ManagedMcpServerInfo server = configService.toggle(id);
                toolProvider.clearCache();
                return ResponseEntity.ok((Object) server);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            } catch (IOException e) {
                return ResponseEntity.internalServerError()
                        .body((Object) Map.of("error", "Failed to write MCP config"));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/servers/{id}/test")
    public Mono<ResponseEntity<Object>> testServer(@PathVariable String id) {
        return Mono.fromCallable(() -> {
            try {
                McpProperties.McpServerInfo server = configService.serverForTest(id);
                return ResponseEntity.ok((Object) toolProvider.testConnection(server));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/reload")
    public Mono<McpToolProvider.McpStatus> reload() {
        return Mono.fromCallable(toolProvider::reload)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @PostMapping("/tools/{toolName}/invoke")
    public Mono<ResponseEntity<Object>> invokeTool(@PathVariable String toolName,
            @RequestBody(required = false) Map<String, Object> input) {
        return Mono.fromCallable(() -> {
            try {
                return ResponseEntity.ok((Object) invocationService.invoke(toolName, input));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body((Object) Map.of("error", e.getMessage()));
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
}

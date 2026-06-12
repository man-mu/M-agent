package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import top.lanshan.manmu.config.McpProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class McpServerConfigServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void listsBuiltinServersWithStableGeneratedIds() throws Exception {
        McpServerConfigService service = serviceWithBuiltin(server(
                null, "http://127.0.0.1:18090", "/sse", "Local weather", true,
                List.of("weather_now")));

        List<McpServerConfigService.ManagedMcpServerInfo> servers = service.listServers();

        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).getId()).startsWith("mcp-127-0-0-1-18090-");
        assertThat(servers.get(0).getSource()).isEqualTo("BUILTIN");
        assertThat(servers.get(0).getAllowedTools()).containsExactly("weather_now");
    }

    @Test
    void keepsConfiguredBuiltinIdsStableForSkillDependenciesAndApiCalls() throws Exception {
        McpServerConfigService service = serviceWithBuiltin(
                server("mcp-qweather", "http://127.0.0.1:18090", "/sse",
                        "本地和风天气 MCP", true, List.of("weather_now")),
                server("mcp-amap", "https://mcp.amap.com", "/sse?key=${AMAP_MAPS_API_KEY}",
                        "高德地图 MCP", false, List.of("maps_weather", "maps_geo")));

        List<McpServerConfigService.ManagedMcpServerInfo> servers = service.listServers();

        assertThat(servers).extracting(McpServerConfigService.ManagedMcpServerInfo::getId)
                .containsExactly("mcp-qweather", "mcp-amap");
        assertThat(service.getServer("mcp-qweather")).isPresent()
                .get()
                .satisfies(server -> {
                    assertThat(server.getSource()).isEqualTo("BUILTIN");
                    assertThat(server.getAllowedTools()).containsExactly("weather_now");
                });
    }

    @Test
    void localConfigCanOverrideBuiltinByStableId() throws Exception {
        McpProperties.McpServerInfo builtin = server(
                "mcp-qweather", "http://127.0.0.1:18090", "/sse", "Builtin weather", true,
                List.of("weather_now"));
        McpServerConfigService service = serviceWithBuiltin(builtin);

        McpProperties.McpServerInfo override = server(
                "mcp-qweather", "http://127.0.0.1:18090", "/sse", "Local override", false,
                List.of("weather_now"));
        Files.createDirectories(service.localConfigPath().getParent());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(service.localConfigPath().toFile(),
                        new McpProperties.McpServerConfig(List.of(override)));

        List<McpServerConfigService.ManagedMcpServerInfo> servers = service.listServers();

        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).getId()).isEqualTo("mcp-qweather");
        assertThat(servers.get(0).getSource()).isEqualTo("LOCAL");
        assertThat(servers.get(0).isLocalOverride()).isTrue();
        assertThat(servers.get(0).isEnabled()).isFalse();
        assertThat(servers.get(0).getDescription()).isEqualTo("Local override");
    }

    @Test
    void localConfigOverridesBuiltinByUrlWithoutEditingSourceConfig() throws Exception {
        McpProperties.McpServerInfo builtin = server(
                null, "http://127.0.0.1:18090", "/sse", "Builtin weather", true,
                List.of("weather_now"));
        McpServerConfigService service = serviceWithBuiltin(builtin);

        String builtinId = service.listServers().get(0).getId();
        McpProperties.McpServerInfo override = server(
                null, "http://127.0.0.1:18090", "sse", "Local override", false,
                List.of("weather_now", "weather_now", " "));
        Files.createDirectories(service.localConfigPath().getParent());
        objectMapper.writerWithDefaultPrettyPrinter()
                .writeValue(service.localConfigPath().toFile(),
                        new McpProperties.McpServerConfig(List.of(override)));

        List<McpServerConfigService.ManagedMcpServerInfo> servers = service.listServers();

        assertThat(servers).hasSize(1);
        assertThat(servers.get(0).getId()).isEqualTo(builtinId);
        assertThat(servers.get(0).getSource()).isEqualTo("LOCAL");
        assertThat(servers.get(0).isLocalOverride()).isTrue();
        assertThat(servers.get(0).isEnabled()).isFalse();
        assertThat(servers.get(0).getDescription()).isEqualTo("Local override");
        assertThat(servers.get(0).getAllowedTools()).containsExactly("weather_now");
        assertThat(builtin.getDescription()).isEqualTo("Builtin weather");
        assertThat(builtin.isEnabled()).isTrue();
    }

    @Test
    void createsTogglesUpdatesAndDeletesLocalServers() throws Exception {
        McpServerConfigService service = serviceWithBuiltin();
        McpProperties.McpServerInfo request = server(
                "local-test", "http://127.0.0.1:19090", "sse", "Local test", true,
                List.of("alpha", "beta"));

        McpServerConfigService.ManagedMcpServerInfo created = service.create(request);
        assertThat(created.getId()).isEqualTo("local-test");
        assertThat(created.getSseEndpoint()).isEqualTo("/sse");

        McpServerConfigService.ManagedMcpServerInfo toggled = service.toggle("local-test");
        assertThat(toggled.isEnabled()).isFalse();

        McpProperties.McpServerInfo update = server(
                "ignored", "http://127.0.0.1:19091", "/events", "Updated", true,
                List.of("gamma"));
        McpServerConfigService.ManagedMcpServerInfo updated = service.update("local-test", update);
        assertThat(updated.getId()).isEqualTo("local-test");
        assertThat(updated.getUrl()).isEqualTo("http://127.0.0.1:19091");
        assertThat(updated.getAllowedTools()).containsExactly("gamma");

        service.delete("local-test");

        assertThat(service.listServers()).isEmpty();
        String localConfig = Files.readString(service.localConfigPath());
        assertThat(localConfig).doesNotContain("local-test");
    }

    @Test
    void rejectsInvalidValuesAndMissingDeleteTargets() throws Exception {
        McpServerConfigService service = serviceWithBuiltin();
        McpProperties.McpServerInfo badUrl = server(
                "bad-url", "ftp://example.com", "/sse", "bad", true, List.of());
        McpProperties.McpServerInfo inlineKey = server(
                "inline-key", "https://example.com", "/sse?key=secret-value", "bad", true, List.of());
        McpProperties.McpServerInfo placeholderKey = server(
                "placeholder-key", "https://example.com", "/sse?key=${EXAMPLE_API_KEY}", "ok", true, List.of());

        assertThatThrownBy(() -> service.create(badUrl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("http:// or https://");
        assertThatThrownBy(() -> service.create(inlineKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${ENV_NAME}");
        assertThat(service.create(placeholderKey).getSseEndpoint())
                .isEqualTo("/sse?key=${EXAMPLE_API_KEY}");
        assertThatThrownBy(() -> service.delete("missing-server"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void rejectsInlineSecretsInHeadersAndApiKey() throws Exception {
        McpServerConfigService service = serviceWithBuiltin();

        // headers with plaintext key should be rejected
        McpProperties.McpServerInfo headerKey = server(
                "header-key", "https://example.com", "/sse", "bad", true, List.of());
        headerKey.setHeaders(Map.of("X-API-Key", "secret-value"));
        assertThatThrownBy(() -> service.create(headerKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${ENV_NAME}");

        // headers with placeholder should pass
        McpProperties.McpServerInfo headerPlaceholder = server(
                "header-ok", "https://example.com", "/sse", "ok", true, List.of());
        headerPlaceholder.setHeaders(Map.of("X-API-Key", "${EXAMPLE_API_KEY}"));
        assertThat(service.create(headerPlaceholder).getHeaders())
                .containsEntry("X-API-Key", "${EXAMPLE_API_KEY}");

        // plaintext apiKey should be rejected
        McpProperties.McpServerInfo apiKeyPlain = server(
                "apikey-plain", "https://example.com", "/sse", "bad", true, List.of());
        apiKeyPlain.setApiKey("sk-plain-secret");
        assertThatThrownBy(() -> service.create(apiKeyPlain))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("${ENV_NAME}");

        // placeholder apiKey should pass
        McpProperties.McpServerInfo apiKeyOk = server(
                "apikey-ok", "https://example.com", "/sse", "ok", true, List.of());
        apiKeyOk.setApiKey("${MODELSCOPE_API_TOKEN}");
        assertThat(service.create(apiKeyOk).getApiKey())
                .isEqualTo("${MODELSCOPE_API_TOKEN}");
    }

    private McpServerConfigService serviceWithBuiltin(McpProperties.McpServerInfo... servers) throws Exception {
        Path localPath = Files.createTempDirectory("mcp-config-service")
                .resolve("mcp-servers.json");
        return new McpServerConfigService(
                new McpProperties.McpServerConfig(List.of(servers)),
                localPath,
                objectMapper);
    }

    private McpProperties.McpServerInfo server(String id, String url, String endpoint,
            String description, boolean enabled, List<String> tools) {
        McpProperties.McpServerInfo info = new McpProperties.McpServerInfo();
        info.setId(id);
        info.setUrl(url);
        info.setSseEndpoint(endpoint);
        info.setDescription(description);
        info.setEnabled(enabled);
        info.setAllowedTools(tools);
        return info;
    }
}

package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.lanshan.manmu.config.McpProperties;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class McpServerConfigService {

    private static final Logger logger = LoggerFactory.getLogger(McpServerConfigService.class);
    private static final Pattern SAFE_ID = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{1,63}$");
    private static final Pattern SENSITIVE_QUERY_PARAM =
            Pattern.compile("(?i)([?&](key|token|api[_-]?key|access[_-]?key)=)([^&\\s]+)");

    private final McpProperties.McpServerConfig staticConfig;
    private final Path localConfigPath;
    private final ObjectMapper objectMapper;

    public McpServerConfigService(McpProperties.McpServerConfig staticConfig,
            McpProperties mcpProperties, ObjectMapper objectMapper) {
        this(staticConfig, Paths.get(mcpProperties.getLocalConfigPath()), objectMapper);
    }

    public McpServerConfigService(McpProperties.McpServerConfig staticConfig,
            Path localConfigPath, ObjectMapper objectMapper) {
        this.staticConfig = staticConfig == null ? new McpProperties.McpServerConfig() : staticConfig;
        this.localConfigPath = localConfigPath.toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public synchronized List<ManagedMcpServerInfo> listServers() {
        return currentConfig().getMcpServers().stream()
                .map(ManagedMcpServerInfo.class::cast)
                .toList();
    }

    public synchronized McpProperties.McpServerConfig currentConfig() {
        Map<String, ManagedMcpServerInfo> merged = new LinkedHashMap<>();
        for (McpProperties.McpServerInfo server : staticServers()) {
            ManagedMcpServerInfo managed = managedCopy(server, "BUILTIN", false);
            merged.put(managed.getId(), managed);
        }
        for (McpProperties.McpServerInfo server : localServers()) {
            String requestedId = server.getId();
            ManagedMcpServerInfo managed = managedCopy(server, "LOCAL", false);
            if (requestedId == null || requestedId.isBlank()) {
                managed.setId(null);
            }
            String targetId = targetIdForLocal(managed, merged);
            managed.setId(targetId);
            managed.setLocalOverride(merged.containsKey(targetId));
            merged.put(targetId, managed);
        }
        return new McpProperties.McpServerConfig(new ArrayList<>(merged.values()));
    }

    public synchronized Optional<ManagedMcpServerInfo> getServer(String id) {
        requireValidId(id);
        return currentConfig().getMcpServers().stream()
                .map(ManagedMcpServerInfo.class::cast)
                .filter(server -> id.equals(server.getId()))
                .findFirst();
    }

    public synchronized ManagedMcpServerInfo create(McpProperties.McpServerInfo request) throws IOException {
        ManagedMcpServerInfo server = sanitizeForWrite(request);
        if (server.getId() == null || server.getId().isBlank()) {
            server.setId(generateId(server));
        }
        requireValidId(server.getId());
        if (getServer(server.getId()).isPresent()) {
            throw new IllegalArgumentException("MCP Server id already exists: " + server.getId());
        }
        List<ManagedMcpServerInfo> locals = localManagedServers();
        locals.add(server);
        writeLocalServers(locals);
        return getServer(server.getId()).orElse(server);
    }

    public synchronized ManagedMcpServerInfo update(String id, McpProperties.McpServerInfo request) throws IOException {
        requireValidId(id);
        ManagedMcpServerInfo server = sanitizeForWrite(request);
        server.setId(id);
        List<ManagedMcpServerInfo> locals = localManagedServers();
        int existing = indexOf(locals, id);
        if (existing >= 0) {
            locals.set(existing, server);
        } else if (builtinIds().contains(id)) {
            locals.add(server);
        } else {
            throw new IllegalArgumentException("MCP Server not found: " + id);
        }
        writeLocalServers(locals);
        return getServer(id).orElse(server);
    }

    public synchronized ManagedMcpServerInfo toggle(String id) throws IOException {
        ManagedMcpServerInfo current = getServer(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found: " + id));
        ManagedMcpServerInfo updated = managedCopy(current, "LOCAL", current.isLocalOverride());
        updated.setEnabled(!current.isEnabled());
        return update(id, updated);
    }

    public synchronized void delete(String id) throws IOException {
        requireValidId(id);
        List<ManagedMcpServerInfo> locals = localManagedServers();
        int existing = indexOf(locals, id);
        if (existing >= 0) {
            locals.remove(existing);
            writeLocalServers(locals);
            return;
        }
        if (builtinIds().contains(id)) {
            throw new IllegalArgumentException("Built-in MCP Server has no local override: " + id);
        }
        throw new IllegalArgumentException("MCP Server not found: " + id);
    }

    public synchronized ManagedMcpServerInfo serverForTest(String id) {
        return getServer(id)
                .orElseThrow(() -> new IllegalArgumentException("MCP Server not found: " + id));
    }

    public Path localConfigPath() {
        return localConfigPath;
    }

    private List<McpProperties.McpServerInfo> staticServers() {
        return staticConfig.getMcpServers() == null ? List.of() : staticConfig.getMcpServers();
    }

    private List<McpProperties.McpServerInfo> localServers() {
        if (!Files.isRegularFile(localConfigPath)) {
            return List.of();
        }
        try {
            McpProperties.McpServerConfig config = objectMapper.readValue(localConfigPath.toFile(),
                    new TypeReference<McpProperties.McpServerConfig>() {});
            return config.getMcpServers() == null ? List.of() : config.getMcpServers();
        } catch (IOException e) {
            logger.warn("Failed to read local MCP config {}: {}", localConfigPath, e.getMessage());
            return List.of();
        }
    }

    private List<ManagedMcpServerInfo> localManagedServers() {
        return localServers().stream()
                .map(server -> managedCopy(server, "LOCAL", false))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void writeLocalServers(List<ManagedMcpServerInfo> servers) throws IOException {
        Files.createDirectories(localConfigPath.getParent());
        List<McpProperties.McpServerInfo> persisted = servers.stream()
                .map(this::persistedCopy)
                .collect(Collectors.toCollection(ArrayList::new));
        McpProperties.McpServerConfig config = new McpProperties.McpServerConfig(persisted);
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(localConfigPath.toFile(), config);
    }

    private McpProperties.McpServerInfo persistedCopy(McpProperties.McpServerInfo source) {
        McpProperties.McpServerInfo copy = new McpProperties.McpServerInfo();
        copy.setId(source.getId());
        copy.setUrl(source.getUrl());
        copy.setSseEndpoint(normalizeEndpoint(source.getSseEndpoint()));
        copy.setDescription(source.getDescription());
        copy.setEnabled(source.isEnabled());
        copy.setAllowedTools(normalizeTools(source.getAllowedTools()));
        copy.setHeaders(source.getHeaders() != null
                ? new LinkedHashMap<>(source.getHeaders()) : new LinkedHashMap<>());
        copy.setApiKey(source.getApiKey());
        return copy;
    }

    private ManagedMcpServerInfo sanitizeForWrite(McpProperties.McpServerInfo request) {
        if (request == null) {
            throw new IllegalArgumentException("MCP Server config must not be empty");
        }
        ManagedMcpServerInfo server = managedCopy(request, "LOCAL", false);
        if (server.getUrl() == null || server.getUrl().isBlank()) {
            throw new IllegalArgumentException("MCP Server url must not be empty");
        }
        String url = server.getUrl().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            throw new IllegalArgumentException("MCP Server url must start with http:// or https://");
        }
        requireNoInlineSecret(url);
        server.setUrl(url);
        server.setSseEndpoint(normalizeEndpoint(server.getSseEndpoint()));
        requireNoInlineSecret(server.getSseEndpoint());
        if (server.getHeaders() != null) {
            server.getHeaders().values().forEach(McpServerConfigService::requireNoInlineSecret);
        }
        if (server.getApiKey() != null) {
            requireNoInlineSecret(server.getApiKey());
        }
        server.setDescription(blankToNull(server.getDescription()));
        server.setAllowedTools(normalizeTools(server.getAllowedTools()));
        if (server.getId() != null && !server.getId().isBlank()) {
            server.setId(server.getId().trim());
            requireValidId(server.getId());
        }
        return server;
    }

    private String targetIdForLocal(ManagedMcpServerInfo local,
            Map<String, ManagedMcpServerInfo> builtins) {
        if (local.getId() != null && !local.getId().isBlank()) {
            requireValidId(local.getId());
            return local.getId();
        }
        return builtins.values().stream()
                .filter(server -> server.getUrl().equals(local.getUrl()))
                .findFirst()
                .map(ManagedMcpServerInfo::getId)
                .orElseGet(() -> generateId(local));
    }

    private List<String> builtinIds() {
        return staticServers().stream()
                .map(server -> managedCopy(server, "BUILTIN", false).getId())
                .toList();
    }

    private int indexOf(List<ManagedMcpServerInfo> servers, String id) {
        for (int i = 0; i < servers.size(); i++) {
            ManagedMcpServerInfo server = servers.get(i);
            if (id.equals(server.getId()) || (server.getId() == null && id.equals(generateId(server)))) {
                return i;
            }
        }
        return -1;
    }

    private ManagedMcpServerInfo managedCopy(McpProperties.McpServerInfo source,
            String sourceType, boolean localOverride) {
        ManagedMcpServerInfo copy = new ManagedMcpServerInfo();
        copy.setId(source.getId());
        copy.setUrl(source.getUrl());
        copy.setSseEndpoint(normalizeEndpoint(source.getSseEndpoint()));
        copy.setDescription(source.getDescription());
        copy.setEnabled(source.isEnabled());
        copy.setAllowedTools(normalizeTools(source.getAllowedTools()));
        copy.setHeaders(source.getHeaders() != null
                ? new LinkedHashMap<>(source.getHeaders()) : new LinkedHashMap<>());
        copy.setApiKey(source.getApiKey());
        copy.setSource(sourceType);
        copy.setLocalOverride(localOverride);
        copy.setEditable(true);
        if (copy.getId() == null || copy.getId().isBlank()) {
            copy.setId(generateId(copy));
        }
        return copy;
    }

    private String generateId(McpProperties.McpServerInfo server) {
        String base = server.getUrl();
        try {
            URI uri = URI.create(server.getUrl());
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                base = host + (uri.getPort() > 0 ? "-" + uri.getPort() : "");
            }
        } catch (IllegalArgumentException ignored) {
            // fallback to raw url
        }
        String slug = base.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (slug.isBlank()) {
            slug = "server";
        }
        int hash = Math.abs((server.getUrl() + "|" + normalizeEndpoint(server.getSseEndpoint())).hashCode());
        return "mcp-" + slug + "-" + Integer.toHexString(hash);
    }

    private String normalizeEndpoint(String endpoint) {
        String value = endpoint == null || endpoint.isBlank() ? "/sse" : endpoint.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private List<String> normalizeTools(List<String> tools) {
        if (tools == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String tool : tools) {
            if (tool == null || tool.isBlank()) {
                continue;
            }
            String trimmed = tool.trim();
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void requireValidId(String id) {
        if (id == null || !SAFE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("MCP Server id is invalid");
        }
    }

    private static void requireNoInlineSecret(String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        var matcher = SENSITIVE_QUERY_PARAM.matcher(value);
        while (matcher.find()) {
            String paramValue = matcher.group(3);
            if (!paramValue.contains("${")) {
                throw new IllegalArgumentException(
                        "MCP Server key/token values must use ${ENV_NAME} placeholders");
            }
        }
    }

    public static class ManagedMcpServerInfo extends McpProperties.McpServerInfo {
        private String source = "LOCAL";
        private boolean editable = true;
        private boolean localOverride;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public boolean isEditable() { return editable; }
        public void setEditable(boolean editable) { this.editable = editable; }
        public boolean isLocalOverride() { return localOverride; }
        public void setLocalOverride(boolean localOverride) { this.localOverride = localOverride; }
    }
}

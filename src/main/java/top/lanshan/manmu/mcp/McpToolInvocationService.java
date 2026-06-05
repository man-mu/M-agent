package top.lanshan.manmu.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public class McpToolInvocationService {

    static final int MAX_OUTPUT_CHARS = 16 * 1024;

    private final McpToolProvider toolProvider;
    private final ObjectMapper objectMapper;

    public McpToolInvocationService(McpToolProvider toolProvider, ObjectMapper objectMapper) {
        this.toolProvider = toolProvider;
        this.objectMapper = objectMapper;
    }

    public McpToolInvocationResult invoke(String toolName, Map<String, Object> input) {
        String normalizedToolName = normalizeToolName(toolName);
        Map<String, Object> safeInput = input == null ? Map.of() : input;
        ToolCallback callback = findTool(normalizedToolName);
        if (callback == null) {
            return McpToolInvocationResult.failed(normalizedToolName, sanitizeInput(safeInput),
                    "MCP tool is not connected or not allowed", 0);
        }

        long started = System.nanoTime();
        try {
            String toolInput = objectMapper.writeValueAsString(safeInput);
            String output = callback.call(toolInput);
            return McpToolInvocationResult.succeeded(normalizedToolName, sanitizeInput(safeInput),
                    limitOutput(sanitizeSensitiveMessage(output)), durationMs(started));
        } catch (JsonProcessingException e) {
            return McpToolInvocationResult.failed(normalizedToolName, sanitizeInput(safeInput),
                    "Failed to serialize MCP tool input", durationMs(started));
        } catch (RuntimeException e) {
            return McpToolInvocationResult.failed(normalizedToolName, sanitizeInput(safeInput),
                    sanitizeSensitiveMessage(safeMessage(e)), durationMs(started));
        }
    }

    private ToolCallback findTool(String toolName) {
        for (ToolCallback callback : toolProvider.getToolCallbacks()) {
            if (callback == null || callback.getToolDefinition() == null) {
                continue;
            }
            if (matchesToolName(toolName, callback.getToolDefinition().name())) {
                return callback;
            }
        }
        return null;
    }

    private boolean matchesToolName(String requestedToolName, String callbackToolName) {
        if (callbackToolName == null || callbackToolName.isBlank()) {
            return false;
        }
        String normalizedCallbackName = callbackToolName.strip();
        return requestedToolName.equals(normalizedCallbackName)
                || normalizedCallbackName.endsWith("_" + requestedToolName);
    }

    private String normalizeToolName(String toolName) {
        String value = toolName == null ? "" : toolName.strip();
        if (value.isBlank()) {
            throw new IllegalArgumentException("MCP tool name must not be empty");
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeInput(Map<String, Object> input) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : input.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (isSensitiveKey(key)) {
                sanitized.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                sanitized.put(key, sanitizeInput((Map<String, Object>) nested));
            } else {
                sanitized.put(key, value);
            }
        }
        return sanitized;
    }

    private boolean isSensitiveKey(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.contains("key")
                || lower.contains("token")
                || lower.contains("secret")
                || lower.contains("password")
                || lower.contains("credential");
    }

    private static String safeMessage(Throwable e) {
        if (e == null) {
            return "Unknown error";
        }
        return e.getMessage() == null || e.getMessage().isBlank()
                ? e.getClass().getSimpleName()
                : e.getMessage();
    }

    private static String sanitizeSensitiveMessage(String value) {
        if (value == null) {
            return "";
        }
        String sanitized = value
                .replaceAll("(?i)(key|token|api[_-]?key|access[_-]?key|password|secret)=([^&\\s]+)", "$1=***")
                .replaceAll("(?i)(\"(?:key|token|api[_-]?key|access[_-]?key|password|secret)\"\\s*:\\s*\")([^\"]+)(\")", "$1***$3");
        String lower = sanitized.toLowerCase(Locale.ROOT);
        if (lower.contains("timeoutexception")
                || lower.contains("did not observe any item or terminal signal")
                || lower.contains("timed out")) {
            return "Connection timed out while invoking MCP tool";
        }
        if (lower.contains("connection refused") || lower.contains("connectexception")) {
            return "MCP server is not reachable";
        }
        return sanitized;
    }

    private static String limitOutput(String output) {
        if (output == null) {
            return "";
        }
        if (output.length() <= MAX_OUTPUT_CHARS) {
            return output;
        }
        return output.substring(0, MAX_OUTPUT_CHARS) + "\n...[truncated]";
    }

    private static long durationMs(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
    }

    public record McpToolInvocationResult(String toolName, Map<String, Object> input,
            String output, long durationMs, String error) {

        static McpToolInvocationResult succeeded(String toolName, Map<String, Object> input,
                String output, long durationMs) {
            return new McpToolInvocationResult(toolName, input, output, durationMs, "");
        }

        static McpToolInvocationResult failed(String toolName, Map<String, Object> input,
                String error, long durationMs) {
            return new McpToolInvocationResult(toolName, input, "", durationMs, error);
        }
    }
}

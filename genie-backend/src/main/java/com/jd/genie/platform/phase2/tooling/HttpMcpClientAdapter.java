package com.jd.genie.platform.phase2.tooling;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!test")
public class HttpMcpClientAdapter implements McpClientAdapter {
    private static final String INTERNAL_MCP_HEADER = "X-Genie-Internal-Mcp-Token";

    private final ObjectMapper mapper;
    private final McpUrlPolicy urlPolicy;
    private final String mcpClientUrl;
    private final String internalMcpToken;

    public HttpMcpClientAdapter(
            ObjectMapper mapper,
            McpUrlPolicy urlPolicy,
            @Value("${autobots.autoagent.mcp_client_url:}") String mcpClientUrl,
            @Value("${GENIE_INTERNAL_MCP_TOKEN:}") String internalMcpToken
    ) {
        this.mapper = mapper;
        this.urlPolicy = urlPolicy;
        this.mcpClientUrl = mcpClientUrl == null ? "" : mcpClientUrl.trim();
        this.internalMcpToken = internalMcpToken == null ? "" : internalMcpToken.trim();
    }

    @Override
    public List<RemoteTool> listTools(String url, AuthType type, String authName, String credential) {
        if (prefersGenieClient(url)) {
            return listToolsViaGenieClient(url, type, authName, credential);
        }
        JsonNode tools = post(url, type, authName, credential, "tools/list", Map.of()).path("result").path("tools");
        if (!tools.isArray() || tools.size() > 200) {
            throw discovery();
        }
        List<RemoteTool> out = new ArrayList<>();
        for (JsonNode tool : tools) {
            out.add(toRemoteTool(tool));
        }
        return out;
    }

    @Override
    public JsonNode callTool(
            String url,
            AuthType type,
            String authName,
            String credential,
            String name,
            Map<String, Object> arguments
    ) {
        if (prefersGenieClient(url)) {
            return callToolViaGenieClient(url, type, authName, credential, name, arguments);
        }
        return post(url, type, authName, credential, "tools/call", Map.of("name", name, "arguments", arguments));
    }

    private List<RemoteTool> listToolsViaGenieClient(
            String url,
            AuthType type,
            String authName,
            String credential
    ) {
        JsonNode payload = genieClientPost(
                "/v1/tool/list",
                Map.of("server_url", resolveRemoteUrl(url, type, authName, credential))
        );
        JsonNode tools = payload.path("data");
        if (!tools.isArray() || tools.size() > 200) {
            throw discovery();
        }
        List<RemoteTool> out = new ArrayList<>();
        for (JsonNode tool : tools) {
            out.add(toRemoteTool(tool));
        }
        return out;
    }

    private JsonNode callToolViaGenieClient(
            String url,
            AuthType type,
            String authName,
            String credential,
            String name,
            Map<String, Object> arguments
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("server_url", resolveRemoteUrl(url, type, authName, credential));
        body.put("name", name);
        body.put("arguments", arguments == null ? Map.of() : arguments);
        return genieClientPost("/v1/tool/call", body).path("data");
    }

    private JsonNode genieClientPost(String path, Map<String, Object> body) {
        if (mcpClientUrl.isBlank() || internalMcpToken.isBlank()) {
            throw unavailable();
        }
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(trimSlash(mcpClientUrl) + path))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .header(INTERNAL_MCP_HEADER, internalMcpToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw auth();
            }
            if (response.statusCode() / 100 != 2 || response.body().length() > 2 * 1024 * 1024) {
                throw unavailable();
            }
            JsonNode payload = mapper.readTree(response.body());
            int code = payload.path("code").asInt(response.statusCode());
            if (code == 401 || code == 403) {
                throw auth();
            }
            if (code / 100 != 2) {
                String message = payload.path("message").asText("");
                if ("MCP_AUTH_INVALID".equals(message)) {
                    throw auth();
                }
                if ("MCP_DISCOVERY_INVALID".equals(message)) {
                    throw discovery();
                }
                throw unavailable();
            }
            return payload;
        } catch (Phase2ContractException error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private JsonNode post(
            String raw,
            AuthType type,
            String authName,
            String credential,
            String method,
            Map<String, Object> params
    ) {
        try {
            URI uri = URI.create(resolveRemoteUrl(raw, type, authName, credential));
            String body = mapper.writeValueAsString(Map.of(
                    "jsonrpc", "2.0",
                    "id", 1,
                    "method", method,
                    "params", params
            ));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            if (type == AuthType.BEARER_TOKEN) {
                builder.header("Authorization", "Bearer " + credential);
            }
            HttpResponse<String> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                throw auth();
            }
            if (response.statusCode() / 100 != 2 || response.body().length() > 2 * 1024 * 1024) {
                throw unavailable();
            }
            return mapper.readTree(response.body());
        } catch (Phase2ContractException error) {
            throw error;
        } catch (Exception error) {
            throw unavailable();
        }
    }

    private String resolveRemoteUrl(String raw, AuthType type, String authName, String credential) {
        URI uri = urlPolicy.validate(raw);
        if (type == AuthType.QUERY_PARAM) {
            if (authName == null || authName.isBlank() || credential == null) {
                throw auth();
            }
            String sep = uri.getQuery() == null ? "?" : "&";
            return uri + sep
                    + URLEncoder.encode(authName, StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(credential, StandardCharsets.UTF_8);
        }
        if (type == AuthType.BEARER_TOKEN && prefersGenieClient(raw)) {
            // genie-client SSE path currently forwards Cookie / X-Server-Keys, not Authorization.
            // Bearer MCP over SSE is not supported here yet.
            throw unavailable();
        }
        return uri.toString();
    }

    private RemoteTool toRemoteTool(JsonNode tool) {
        String name = tool.path("name").asText(null);
        JsonNode schema = tool.path("inputSchema");
        if (!schema.isObject()) {
            schema = tool.path("input_schema");
        }
        if (name == null || name.isBlank() || !schema.isObject() || schema.toString().length() > 256 * 1024) {
            throw discovery();
        }
        return new RemoteTool(name, tool.path("description").asText(""), schema);
    }

    private static boolean prefersGenieClient(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("/sse") || lower.endsWith("sse");
    }

    private static String trimSlash(String value) {
        Objects.requireNonNull(value, "value");
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Phase2ContractException auth() {
        return new Phase2ContractException(MvpErrorCode.MCP_AUTH_INVALID, "MCP authentication is invalid");
    }

    private Phase2ContractException unavailable() {
        return new Phase2ContractException(MvpErrorCode.MCP_UNAVAILABLE, "MCP server unavailable");
    }

    private Phase2ContractException discovery() {
        return new Phase2ContractException(MvpErrorCode.MCP_DISCOVERY_INVALID, "MCP discovery response invalid");
    }
}

package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ExternalMarketplaceServicePaginationTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void skillHubSearchUsesCatalogPageAndReportsHasMore() throws Exception {
        List<String> queries = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/skills", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            byte[] body = """
                {"code":0,"message":"success","data":{"skills":[{"slug":"alpha","name":"Alpha","stars":3}],"total":25}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ExternalMarketplaceService service = service("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v0.1");

        ExternalMarketplacePage first = service.search(ExternalMarketplaceSource.SKILLHUB, "", "stars", 12, null);
        assertThat(queries.get(0)).contains("page=1").contains("pageSize=12");
        assertThat(first.items()).hasSize(1);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isEqualTo("2");

        service.search(ExternalMarketplaceSource.SKILLHUB, "pdf", "stars", 12, "2");
        assertThat(queries.get(1)).contains("page=2").contains("pageSize=12").contains("keyword=pdf");
    }

    @Test
    void mcpRegistrySearchForwardsCursorAndNextCursor() throws Exception {
        List<String> queries = new ArrayList<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v0.1/servers", exchange -> {
            queries.add(exchange.getRequestURI().getQuery());
            byte[] body = """
                {"servers":[{"server":{"name":"io.example/demo","version":"1.0.0","description":"demo"}}],"metadata":{"nextCursor":"abc","count":1}}
                """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ExternalMarketplaceService service = service("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v0.1");

        ExternalMarketplacePage first = service.search(ExternalMarketplaceSource.MCP_REGISTRY, "", "stars", 12, null);
        assertThat(queries.get(0)).contains("limit=12").doesNotContain("cursor=");
        assertThat(first.items()).extracting(ExternalMarketplaceResource::slug).containsExactly("io.example/demo");
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isEqualTo("abc");

        service.search(ExternalMarketplaceSource.MCP_REGISTRY, "fs", "stars", 12, "abc");
        assertThat(queries.get(1)).contains("limit=12").contains("cursor=abc").contains("search=fs");
    }

    private static ExternalMarketplaceService service(String skillHubApi, String mcpRegistryApi) {
        return new ExternalMarketplaceService(new ObjectMapper(), mock(SkillPackageImportService.class),
            skillHubApi, mcpRegistryApi,
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build());
    }
}

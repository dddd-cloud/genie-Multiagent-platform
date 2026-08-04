package com.jd.genie.platform.phase2.tooling;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class McpUrlPolicyTest {
    @Test void rejectsHttpOutsideTestProfile() {
        var p = new McpUrlPolicy(new MockEnvironment());
        assertThatThrownBy(() -> p.validate("http://example.com/mcp")).hasMessageContaining("MCP URL rejected");
    }
    @Test void rejectsLocalAndUserInfo() {
        var p = new McpUrlPolicy(new MockEnvironment().withProperty("spring.profiles.active", "test"));
        assertThatThrownBy(() -> p.validate("http://localhost:8080/mcp")).hasMessageContaining("MCP URL rejected");
        assertThatThrownBy(() -> p.validate("http://user:secret@example.com/mcp", true)).hasMessageContaining("MCP URL rejected");
    }
    @Test void acceptsPublicHttps() { assertThat(new McpUrlPolicy(new MockEnvironment()).validate("https://example.com/mcp").getScheme()).isEqualTo("https"); }
}

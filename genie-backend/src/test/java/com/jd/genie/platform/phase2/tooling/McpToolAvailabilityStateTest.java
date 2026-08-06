package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
class McpToolAvailabilityStateTest {
 @Test void fakeSuccessReturnsRegisteredTools(){var fake=new FakeMcpClientAdapter();fake.register("https://example.com",java.util.List.of(new McpClientAdapter.RemoteTool("search","",com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())));assertThat(fake.listTools("https://example.com",AuthType.NONE,null,null)).hasSize(1);}
}

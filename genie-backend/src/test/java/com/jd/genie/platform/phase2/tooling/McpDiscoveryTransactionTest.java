package com.jd.genie.platform.phase2.tooling;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
class McpDiscoveryTransactionTest {
 @Test void invalidFakeDiscoveryIsRejected(){var fake=new FakeMcpClientAdapter();fake.scenario("https://example.com", FakeMcpClientAdapter.Scenario.INVALID_SCHEMA);assertThatThrownBy(()->fake.listTools("https://example.com",AuthType.NONE,null,null)).hasMessageContaining("MCP request failed");}
 @Test void duplicateScenarioIsRejected(){var fake=new FakeMcpClientAdapter();fake.scenario("https://example.com", FakeMcpClientAdapter.Scenario.DUPLICATE_TOOL);assertThatThrownBy(()->fake.listTools("https://example.com",AuthType.NONE,null,null)).isInstanceOf(RuntimeException.class);}
}

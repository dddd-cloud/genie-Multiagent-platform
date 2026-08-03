package com.jd.genie.platform.phase2contract;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeToolBindingPort;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Phase2FakePortContractTest {

    private final CurrentUser user = new CurrentUser("t1", "u1", "user", "User", UserRole.USER);

    @Test
    void fakesHaveNoSpringAnnotations() {
        assertFalse(FakeAgentRuntimeCatalogPort.class.isAnnotationPresent(Component.class));
        assertFalse(FakeAgentRuntimeCatalogPort.class.isAnnotationPresent(Service.class));
        assertFalse(FakeToolBindingPort.class.isAnnotationPresent(Component.class));
        assertFalse(FakeRuntimeToolCollectionPort.class.isAnnotationPresent(Component.class));
    }

    @Test
    void catalogFakeConfigurableReturnsAndExceptions() {
        FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();
        fake.registerSummary(new AgentCapabilitySummary("a1", 1L, "A", "d"));
        fake.registerProfile(new AgentRuntimeProfile(
            "a1", 1L, "A", "d", "p", "gpt-4o-mini", List.of(), List.of()));

        assertEquals(1, fake.listOnlineCandidates(user, List.of()).size());
        assertEquals("a1", fake.loadOnlineProfile(user, "a1").agentId());

        fake.setListException(new Phase2ContractException(MvpErrorCode.AGENT_OFFLINE, "offline"));
        assertThrows(Phase2ContractException.class, () -> fake.listOnlineCandidates(user, List.of()));
        assertEquals(3, fake.getCalls().size());

        fake.reset();
        assertTrue(fake.getCalls().isEmpty());
        assertTrue(fake.listOnlineCandidates(user, List.of()).isEmpty());
    }

    @Test
    void toolBindingFakeSupportsClearAndIdempotentRemove() {
        FakeToolBindingPort fake = new FakeToolBindingPort();
        fake.setResolveResult(new ToolBindingView(List.of("builtin:file"), Map.of(), List.of()));
        assertNotNull(fake.resolveBindings(user, "a1", List.of()));
        fake.replaceAgentBindings(user, "a1", List.of());
        fake.removeAgentBindings(user, "a1");
        fake.removeAgentBindings(user, "a1");
        fake.failCapabilityKey("builtin:unknown");
        assertThrows(Phase2ContractException.class,
            () -> fake.replaceAgentBindings(user, "a1", List.of("builtin:unknown")));
        assertTrue(fake.getCalls().size() >= 4);
        fake.reset();
        assertTrue(fake.getCalls().isEmpty());
    }

    @Test
    void runtimeToolCollectionFakeRecordsAndExceptions() {
        FakeRuntimeToolCollectionPort fake = new FakeRuntimeToolCollectionPort();
        ToolCollection collection = new ToolCollection();
        fake.setToolCollection(collection);
        AgentRuntimeProfile profile = new AgentRuntimeProfile(
            "a1", 1L, "A", "d", "p", "gpt-4o-mini", List.of(), List.of());
        AgentContext context = AgentContext.builder().requestId("req-1").build();
        assertEquals(collection, fake.build(user, profile, context));
        fake.setBuildException(new Phase2ContractException(MvpErrorCode.TOOL_NOT_BOUND, "missing"));
        assertThrows(Phase2ContractException.class, () -> fake.build(user, profile, context));
        assertEquals(2, fake.getCalls().size());
    }

    @Test
    void concurrentCallRecordReadsDoNotThrow() throws Exception {
        FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch ready = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            pool.submit(() -> {
                try {
                    ready.await(2, TimeUnit.SECONDS);
                    fake.listOnlineCandidates(user, List.of());
                    fake.getCalls().size();
                } catch (Exception ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        ready.countDown();
        assertTrue(done.await(5, TimeUnit.SECONDS));
        pool.shutdownNow();
        assertEquals(4, fake.getCalls().size());
    }
}

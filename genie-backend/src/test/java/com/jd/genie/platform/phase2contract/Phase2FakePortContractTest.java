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
import com.jd.genie.platform.phase2contract.support.FakeAgentSkillBindingPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeSkillRuntimePort;
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
        assertFalse(FakeAgentSkillBindingPort.class.isAnnotationPresent(Component.class));
        assertFalse(FakeSkillRuntimePort.class.isAnnotationPresent(Component.class));
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
    void catalogRejectsNullUserAndNullRegistration() {
        FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();
        Phase2ContractException nullUser = assertThrows(
            Phase2ContractException.class,
            () -> fake.listOnlineCandidates(null, List.of())
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullUser.errorCode());

        assertThrows(Phase2ContractException.class, () -> fake.registerSummary(null));
        assertThrows(Phase2ContractException.class, () -> fake.registerProfile(null));
    }

    @Test
    void catalogEmptyWhitelistOrdersByAgentIdStableAcrossRuns() {
        FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();
        fake.registerSummary(new AgentCapabilitySummary("z-agent", 1L, "Z", "d"));
        fake.registerSummary(new AgentCapabilitySummary("a-agent", 1L, "A", "d"));
        fake.registerSummary(new AgentCapabilitySummary("m-agent", 1L, "M", "d"));

        List<String> first = fake.listOnlineCandidates(user, List.of()).stream()
            .map(AgentCapabilitySummary::agentId)
            .toList();
        List<String> second = fake.listOnlineCandidates(user, List.of()).stream()
            .map(AgentCapabilitySummary::agentId)
            .toList();
        assertEquals(List.of("a-agent", "m-agent", "z-agent"), first);
        assertEquals(first, second);

        List<String> whitelistOrder = fake.listOnlineCandidates(
            user,
            List.of("z-agent", "a-agent")
        ).stream().map(AgentCapabilitySummary::agentId).toList();
        assertEquals(List.of("z-agent", "a-agent"), whitelistOrder);
    }

    @Test
    void catalogCanModelOfflineHiddenAndVersionChangeScenarios() {
        FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();
        fake.registerSummary(new AgentCapabilitySummary("a1", 1L, "A", "d"));
        fake.registerProfile(new AgentRuntimeProfile(
            "a1", 1L, "A", "d", "p", "gpt-4o-mini", List.of(), List.of()));

        fake.markOffline("a1");
        assertTrue(fake.listOnlineCandidates(user, List.of()).isEmpty());
        Phase2ContractException offline = assertThrows(
            Phase2ContractException.class,
            () -> fake.loadOnlineProfile(user, "a1")
        );
        assertEquals(MvpErrorCode.AGENT_OFFLINE, offline.errorCode());

        fake.clearLoadFailure("a1");
        fake.registerProfile(new AgentRuntimeProfile(
            "a1", 2L, "A", "d", "p", "gpt-4o-mini", List.of(), List.of()));
        assertEquals(2L, fake.loadOnlineProfile(user, "a1").agentVersion());

        fake.hideFromUser("a1");
        Phase2ContractException hidden = assertThrows(
            Phase2ContractException.class,
            () -> fake.loadOnlineProfile(user, "a1")
        );
        assertEquals(MvpErrorCode.RESOURCE_NOT_FOUND, hidden.errorCode());
    }

    @Test
    void toolBindingFakeSupportsClearAndIdempotentRemove() {
        FakeToolBindingPort fake = new FakeToolBindingPort();
        fake.setResolveResult(new ToolBindingView(List.of("builtin:file"), Map.of(), List.of()));
        assertNotNull(fake.resolveBindings(user, "a1", List.of()));
        fake.replaceAgentBindings(user, "a1", List.of());
        fake.removeAgentBindings(user, "a1");
        fake.removeAgentBindings(user, "a1");
        fake.failCapabilityKey("builtin:file");
        assertThrows(Phase2ContractException.class,
            () -> fake.replaceAgentBindings(user, "a1", List.of("builtin:unknown")));
        assertTrue(fake.getCalls().size() >= 4);
        fake.reset();
        assertTrue(fake.getCalls().isEmpty());
    }

    @Test
    void toolBindingFakePersistsResolvesAndScopesBindings() {
        FakeToolBindingPort fake = new FakeToolBindingPort();
        CurrentUser otherUser = new CurrentUser("t1", "u2", "other", "Other", UserRole.USER);

        fake.replaceAgentBindings(user, "a1", List.of("builtin:file", "builtin:report"));
        fake.replaceSkillBindings(user, "s1", List.of("builtin:deep_search"));
        fake.replaceAgentBindings(otherUser, "a1", List.of("builtin:data_analysis"));

        ToolBindingView view = fake.resolveBindings(user, "a1", List.of("s1"));
        assertEquals(List.of("builtin:file", "builtin:report"), view.directCapabilities());
        assertEquals(List.of("builtin:deep_search"), view.skillCapabilities().get("s1"));
        assertEquals(List.of("builtin:data_analysis"), fake.getAgentBindings(otherUser, "a1"));

        fake.failCapabilityKey("builtin:file");
        ToolBindingView invalidView = fake.resolveBindings(user, "a1", List.of("s1"));
        assertEquals(List.of("builtin:report"), invalidView.directCapabilities());
        assertEquals(List.of("builtin:file"), invalidView.invalidCapabilities());

        fake.replaceAgentBindings(user, "a1", List.of());
        assertTrue(fake.getAgentBindings(user, "a1").isEmpty());
        assertEquals(List.of("builtin:data_analysis"), fake.getAgentBindings(otherUser, "a1"));
    }

    @Test
    void toolBindingFakeWriteFailureIsAtomic() {
        FakeToolBindingPort fake = new FakeToolBindingPort();
        fake.replaceAgentBindings(user, "a1", List.of("builtin:file"));
        fake.setWriteException(new Phase2ContractException(
            MvpErrorCode.INTERNAL_ERROR,
            "injected write failure"
        ));

        assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceAgentBindings(user, "a1", List.of("builtin:report"))
        );
        assertEquals(List.of("builtin:file"), fake.getAgentBindings(user, "a1"));
    }

    @Test
    void toolBindingRejectsNullUserBlankIdsDuplicatesAndWhitespaceKeys() {
        FakeToolBindingPort fake = new FakeToolBindingPort();

        Phase2ContractException nullUser = assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceAgentBindings(null, "a1", List.of("builtin:file"))
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullUser.errorCode());

        Phase2ContractException blankId = assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceSkillBindings(user, "  ", List.of("builtin:file"))
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, blankId.errorCode());

        Phase2ContractException blankRemove = assertThrows(
            Phase2ContractException.class,
            () -> fake.removeAgentBindings(user, "")
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, blankRemove.errorCode());

        Phase2ContractException duplicate = assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceAgentBindings(user, "a1", List.of("builtin:file", "builtin:file"))
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, duplicate.errorCode());

        Phase2ContractException tabKey = assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceAgentBindings(user, "a1", List.of("mcp:\ttool"))
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, tabKey.errorCode());

        Phase2ContractException newlineKey = assertThrows(
            Phase2ContractException.class,
            () -> fake.replaceSkillBindings(user, "s1", List.of("mcp:tool\n"))
        );
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, newlineKey.errorCode());
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
    void runtimeToolCollectionRejectsNullArgsAndNullCollection() {
        FakeRuntimeToolCollectionPort fake = new FakeRuntimeToolCollectionPort();
        AgentRuntimeProfile profile = new AgentRuntimeProfile(
            "a1", 1L, "A", "d", "p", "gpt-4o-mini", List.of(), List.of());
        AgentContext context = AgentContext.builder().requestId("req-1").build();

        Phase2ContractException nullUser = assertThrows(
            Phase2ContractException.class,
            () -> fake.build(null, profile, context)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullUser.errorCode());

        Phase2ContractException nullCollection = assertThrows(
            Phase2ContractException.class,
            () -> fake.setToolCollection(null)
        );
        assertEquals(MvpErrorCode.VALIDATION_ERROR, nullCollection.errorCode());

        fake.reset();
        assertNotNull(fake.build(user, profile, context));
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

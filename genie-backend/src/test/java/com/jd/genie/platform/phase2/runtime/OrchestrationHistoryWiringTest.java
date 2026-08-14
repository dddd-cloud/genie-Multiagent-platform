package com.jd.genie.platform.phase2.runtime;

import com.jd.genie.platform.contract.ConversationExecutionResult;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import com.jd.genie.platform.contract.support.FakeCurrentUserProvider;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.orchestration.OrchestrationModelPort;
import com.jd.genie.platform.phase2.runtime.orchestration.Phase2OrchestrationRuntime;
import com.jd.genie.platform.phase2.runtime.orchestration.SerialOrchestrationService;
import com.jd.genie.platform.phase2.runtime.orchestration.SummaryEvidence;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlan;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.platform.phase2.runtime.route.RouteDecision;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import com.jd.genie.service.IMultiAgentService;
import com.jd.genie.service.impl.GptProcessServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class OrchestrationHistoryWiringTest {
    private static final CurrentUser USER = new CurrentUser(
            "tenant-1",
            "user-1",
            "alice",
            "Alice",
            UserRole.USER
    );

    @Test
    void orchestratedFollowUpPassesLoadedHistoryIntoPlanner() throws Exception {
        FakeConversationExecutionPort executionPort = preparedExecutionPort();
        executionPort.setLoadCompletedHistoryResult(List.of(
                new ConversationHistoryItem(1L, ConversationMessageRole.USER, "茅台市场规模多大"),
                new ConversationHistoryItem(1L, ConversationMessageRole.ASSISTANT, "约三千亿。")
        ));
        RecordingCatalogPort catalogPort = new RecordingCatalogPort();
        catalogPort.candidates = List.of(new AgentCapabilitySummary("agent-a", 1L, "Agent A", "analysis"));
        HistoryCapturingModel model = new HistoryCapturingModel();
        Phase2OrchestrationRuntime runtime = new Phase2OrchestrationRuntime(
                model,
                new OrchestrationPlanValidator(),
                new SerialOrchestrationService(
                        catalogPort,
                        new FakeRuntimeToolCollectionPort(),
                        mock(ConfiguredAgentExecutor.class),
                        10
                ),
                new OrchestrationEventMapper()
        );
        GptProcessServiceImpl service = service(executionPort, catalogPort, runtime);

        service.queryPhase2AgentStreamIncr(request("ORCHESTRATED", List.of()));

        assertTrue(model.started.await(5, TimeUnit.SECONDS));
        assertTrue(model.planHistory.contains("茅台市场规模多大"));
        assertTrue(model.planHistory.contains("约三千亿。"));
        assertEquals(List.of(
                FakeConversationExecutionPort.CallType.PREPARE_EXECUTION,
                FakeConversationExecutionPort.CallType.LOAD_COMPLETED_HISTORY,
                FakeConversationExecutionPort.CallType.MARK_STREAMING
        ), executionPort.getCalls().stream().map(FakeConversationExecutionPort.CallRecord::type).toList());
        assertEquals(6, executionPort.getCalls().get(1).maxTurns());
        assertEquals(12_000, executionPort.getCalls().get(1).maxCharacters());
    }

    private GptProcessServiceImpl service(
            FakeConversationExecutionPort executionPort,
            AgentRuntimeCatalogPort catalogPort,
            Phase2OrchestrationRuntime runtime
    ) {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("agentRuntimeCatalogPort", catalogPort);
        beanFactory.addBean("phase2OrchestrationRuntime", runtime);
        ObjectProvider<AgentRuntimeCatalogPort> catalogProvider =
                beanFactory.getBeanProvider(AgentRuntimeCatalogPort.class);
        ObjectProvider<Phase2OrchestrationRuntime> runtimeProvider =
                beanFactory.getBeanProvider(Phase2OrchestrationRuntime.class);
        return new GptProcessServiceImpl(
                mock(IMultiAgentService.class),
                new FakeCurrentUserProvider(USER),
                executionPort,
                catalogProvider,
                runtimeProvider,
                3_600_000L,
                8_388_608L,
                6,
                12_000
        );
    }

    private FakeConversationExecutionPort preparedExecutionPort() {
        FakeConversationExecutionPort executionPort = new FakeConversationExecutionPort();
        executionPort.setPrepareExecutionResult(new ConversationExecutionResult(
                "123e4567-e89b-12d3-a456-426614174000",
                "request-1",
                "user-message-1",
                "assistant-message-1",
                1L
        ));
        return executionPort;
    }

    private Phase2GptQueryRequest request(String mode, List<String> allowedAgentIds) {
        return Phase2GptQueryRequest.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("那竞品呢")
                .executionMode(mode)
                .deepThink(0)
                .outputStyle("docs")
                .allowedAgentIds(allowedAgentIds)
                .localContext(Phase2GptQueryRequest.LocalContext.builder()
                        .schemaVersion(1)
                        .longTermMemory("")
                        .conversationSummary("")
                        .build())
                .build();
    }

    private static final class HistoryCapturingModel implements OrchestrationModelPort {
        private final CountDownLatch started = new CountDownLatch(1);
        private volatile String planHistory = "";

        @Override
        public RouteDecision selectRoute(String query, String conversationSummary, List<AgentCapabilitySummary> candidates) {
            return new RouteDecision(RouteDecision.Route.ORCHESTRATED, "TEST");
        }

        @Override
        public OrchestrationPlan createPlan(
                String query,
                List<AgentCapabilitySummary> candidates,
                int attemptNo,
                Map<String, String> successfulResultSummaries,
                Map<String, String> failureMetadata
        ) {
            return createPlan(query, "", candidates, attemptNo, successfulResultSummaries, failureMetadata);
        }

        @Override
        public OrchestrationPlan createPlan(
                String query,
                String conversationHistory,
                List<AgentCapabilitySummary> candidates,
                int attemptNo,
                Map<String, String> successfulResultSummaries,
                Map<String, String> failureMetadata
        ) {
            planHistory = conversationHistory == null ? "" : conversationHistory;
            started.countDown();
            throw new IllegalStateException("stop after capturing history");
        }

        @Override
        public String summarize(String query, Map<String, String> successes, Map<String, String> failures) {
            throw new AssertionError("not reached");
        }

        @Override
        public String summarize(String query, String conversationHistory, List<SummaryEvidence> evidence) {
            throw new AssertionError("not reached");
        }
    }

    private static final class RecordingCatalogPort implements AgentRuntimeCatalogPort {
        private List<AgentCapabilitySummary> candidates = List.of();

        @Override
        public List<AgentCapabilitySummary> listOnlineCandidates(
                CurrentUser user,
                List<String> allowedAgentIds
        ) {
            return candidates;
        }

        @Override
        public com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile loadOnlineProfile(
                CurrentUser user,
                String agentId
        ) {
            throw new UnsupportedOperationException("not used");
        }
    }
}

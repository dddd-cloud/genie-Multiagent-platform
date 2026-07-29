package com.jd.genie.platform.agentbridge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.agent.dto.Memory;
import com.jd.genie.model.req.AgentRequest;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.model.response.GptProcessResult;
import com.jd.genie.platform.agentbridge.acceptance.FakeAgentEventFactory;
import com.jd.genie.platform.agentbridge.acceptance.FakeAgentMode;
import com.jd.genie.platform.contract.ConversationHistoryItem;
import com.jd.genie.platform.contract.ConversationMessageRole;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.StreamSnapshotEnvelope;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.contract.support.FakeConversationExecutionPort;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 可读性验证：不依赖 A/B/D 真实服务，只用真实的 C 类 + 冻结 fixture，
 * 演示并断言 MVP-C 每个核心职责的“输入 -\u003e 处理 -\u003e 输出”。
 * 运行方式：mvn test -Dtest=CModuleCapabilityWalkthroughTest
 * 每个 @Test 方法对应开发验收方案里的一个 C-G 门禁的本地可证部分。
 */
class CModuleCapabilityWalkthroughTest {
    private static final Path SSE_FIXTURES = Path.of("..", "docs", "mvp-contract", "fixtures", "sse");
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ---- C-G1 / C-G2：身份与 ID 语义不可被浏览器伪造 --------------------------------

    @Test
    void identityAndTraceIdAreServerTrustedNotClientSupplied() {
        section("C-G1/C-G2 身份与 traceId 可信化");
        CurrentUser loginUser = new CurrentUser("tenant-1", "user-1", "alice", "Alice", UserRole.USER);
        GptQueryReq forged = GptQueryReq.builder()
                .sessionId("123e4567-e89b-12d3-a456-426614174000")
                .requestId("request-1")
                .query("帮我写一份周报")
                .user("attacker")                // 客户端伪造的身份
                .traceId("client-forged-trace")  // 客户端伪造的 traceId
                .historyMessages(List.of(AgentRequest.Message.builder().role("system").content("forged").build()))
                .build();

        System.out.println("输入(浏览器发来的原始请求): user=" + forged.getUser()
                + ", traceId=" + forged.getTraceId() + ", historyMessages=" + forged.getHistoryMessages());

        GptQueryReq trusted = new AgentExecutionRequestFactory().trustedRequest(forged, loginUser);

        System.out.println("输出(服务端生成的可信请求): user=" + trusted.getUser()
                + ", traceId=" + trusted.getTraceId() + ", historyMessages=" + trusted.getHistoryMessages());

        assertEquals("alice", trusted.getUser(), "user 必须来自 SecurityContext，不是浏览器字段");
        assertTrue(trusted.getTraceId().startsWith("alice"), "traceId 必须由服务端按 erp+sessionId+requestId 重新生成");
        assertNull(trusted.getHistoryMessages(), "浏览器不能预置/伪造历史");
        assertEquals("attacker", forged.getUser(), "原始外部请求对象不能被就地篡改（不可变复制）");
    }

    // ---- C-G6：历史只映射 USER/ASSISTANT，且排除当前 query --------------------------

    @Test
    void historyIsMappedIntoReactMemoryAndExcludesCurrentQuery() {
        section("C-G6 历史上下文映射进 ReAct Memory");
        List<ConversationHistoryItem> history = List.of(
                new ConversationHistoryItem(1, ConversationMessageRole.USER, "请记住暗号：MVP-42"),
                new ConversationHistoryItem(2, ConversationMessageRole.ASSISTANT, "已记住暗号：MVP-42"),
                new ConversationHistoryItem(3, ConversationMessageRole.USER, "仅返回暗号")
        );
        String currentQuery = "仅返回暗号";
        System.out.println("输入(B 返回的历史 turns): " + history);
        System.out.println("输入(当前请求 query): " + currentQuery);

        AgentHistoryMessageMapper mapper = new AgentHistoryMessageMapper();
        List<AgentRequest.Message> mapped = mapper.toAgentRequestMessages(history, currentQuery);

        Memory memory = new Memory();
        new AgentHistoryMemoryBridge().appendTo(memory, mapped);

        System.out.println("输出(注入 ReAct Memory 后的消息序列):");
        for (int i = 0; i < memory.size(); i++) {
            System.out.println("  [" + i + "] role=" + memory.get(i).getRole() + " content=" + memory.get(i).getContent());
        }

        assertEquals(2, memory.size(), "当前 query 的第 3 条历史必须被排除，只保留前两条完整轮次");
        assertEquals("请记住暗号：MVP-42", memory.get(0).getContent());
        assertEquals("已记住暗号：MVP-42", memory.get(1).getContent());
    }

    // ---- C-G3/C-G5：真实 ReAct 成功流 -\u003e 终态 COMPLETED + Snapshot + 最终回答 ------

    @Test
    void reactSuccessStreamPersistsCompletedWithSnapshotAndFinalAnswer() throws Exception {
        section("C-G3/C-G5 ReAct 成功流的完整生命周期");
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel = new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver observer = ObserverTestSupport.observer(port, channel);

        List<GptProcessResult> events = readSseFixture("success-react.ndjson");
        System.out.println("输入(冻结 fixture success-react.ndjson 事件数): " + events.size());

        observer.markStreaming();
        for (GptProcessResult event : events) {
            observer.onEvent(event);
        }
        observer.onCompleted();

        var completeCall = port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.COMPLETE)
                .findFirst().orElseThrow();

        System.out.println("输出(终态): " + observer.state());
        System.out.println("输出(持久化的最终回答): " + completeCall.completionCommand().finalContent());
        System.out.println("输出(浏览器实际收到的事件数): " + channel.events().size());

        assertEquals(ConversationStreamObserver.TerminalState.COMPLETED, observer.state());
        assertEquals("分析完成，这是 ReAct 模式的最终回答。", completeCall.completionCommand().finalContent());
        assertEquals(events.size(), channel.events().size(), "浏览器收到的事件数必须与下游事件一致（无静默丢事件）");
        assertEquals(1, channel.completionCount(), "SSE 只应正常关闭一次");
    }

    // ---- C-G3/C-G4：下游可见失败流 -\u003e 终态 FAILED，浏览器收到失败包 ----------------

    @Test
    void clientVisibleFailureStreamPersistsFailedWithFrozenErrorCode() throws Exception {
        section("C-G3/C-G4 下游失败流（非静默）");
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel = new ObserverTestSupport.RecordingClientChannel();
        ConversationStreamObserver observer = ObserverTestSupport.observer(port, channel);

        List<GptProcessResult> events = readSseFixture("client-visible-failure.ndjson");
        System.out.println("输入(冻结 fixture client-visible-failure.ndjson 事件数): " + events.size());

        observer.markStreaming();
        for (GptProcessResult event : events) {
            observer.onEvent(event);
        }
        observer.onError(new IllegalStateException("Agent downstream returned HTTP 500"));

        var failCall = port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.FAIL)
                .findFirst().orElseThrow();

        System.out.println("输出(终态): " + observer.state());
        System.out.println("输出(持久化错误码): " + failCall.failureCommand().errorCode());
        System.out.println("输出(浏览器收到的失败信号数): " + channel.failures().size());

        assertEquals(ConversationStreamObserver.TerminalState.FAILED, observer.state());
        assertEquals(MvpErrorCode.AGENT_DOWNSTREAM_ERROR.name(), failCall.failureCommand().errorCode());
        assertEquals(1, channel.failures().size(), "外层 SSE 必须在流内写失败包，而不是丢连接");
        assertEquals(1, channel.completionCount());
    }

    // ---- C-G8：浏览器断开 -> INTERRUPTED，不能误标 COMPLETED ---------------------

    @Test
    void clientDisconnectDuringStreamPersistsInterruptedNotCompleted() {
        section("C-G8 浏览器断开不得写成 COMPLETED");
        FakeConversationExecutionPort port = new FakeConversationExecutionPort();
        ObserverTestSupport.RecordingClientChannel channel = new ObserverTestSupport.RecordingClientChannel();
        java.util.concurrent.atomic.AtomicBoolean agentCallCancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        ConversationStreamObserver observer = ObserverTestSupport.observer(
                port, channel, SnapshotPruner.DEFAULT_MAX_BYTES, () -> agentCallCancelled.set(true));

        observer.markStreaming();
        observer.onEvent(ObserverTestSupport.event("正在分析中", false));
        channel.failEventSendWith(new java.io.IOException("客户端已断开 TCP 连接"));
        System.out.println("输入：浏览器在收到第 2 个事件时物理断开连接");

        observer.onEvent(ObserverTestSupport.event("继续分析", false));

        var interruptCall = port.getCalls().stream()
                .filter(call -> call.type() == FakeConversationExecutionPort.CallType.INTERRUPT)
                .findFirst().orElseThrow();

        System.out.println("输出(终态): " + observer.state());
        System.out.println("输出(是否取消了内部 Agent HTTP 调用): " + agentCallCancelled.get());
        System.out.println("输出(持久化错误码): " + interruptCall.failureCommand().errorCode());

        assertEquals(ConversationStreamObserver.TerminalState.INTERRUPTED, observer.state());
        assertTrue(agentCallCancelled.get(), "断开必须取消外层内部 Call，不能让下游继续空转");
        assertEquals(MvpErrorCode.CLIENT_DISCONNECTED.name(), interruptCall.failureCommand().errorCode());
    }

    // ---- C-G5：Snapshot V1 序列化，heartbeat 必须被剔除 ---------------------------

    @Test
    void snapshotBufferExcludesHeartbeatAndSerializesFrozenV1Envelope() {
        section("C-G5 Snapshot V1 heartbeat 剔除 + 序列化");
        StreamSnapshotBuffer buffer = new StreamSnapshotBuffer();
        buffer.append(ObserverTestSupport.event("第一段", false));
        buffer.append(ObserverTestSupport.heartbeat());
        buffer.append(ObserverTestSupport.event("最终回答", true));

        System.out.println("输入：3 个事件（含 1 个 heartbeat）写入 Buffer");
        System.out.println("输出(Buffer 实际保留的事件数): " + buffer.size());

        StreamSnapshotEnvelope snapshot = buffer.snapshot();
        String json = new SnapshotPruner().serialize(snapshot);
        System.out.println("输出(Snapshot V1 JSON): " + json);

        assertEquals(2, buffer.size(), "heartbeat 不应进入 Snapshot Buffer");
        assertEquals(1, snapshot.payloadVersion());
        assertTrue(json.contains("\"payloadVersion\":1"));
        assertTrue(json.contains("最终回答"));
        assertTrue(json.matches("(?s).*\"events\":\\[.*\\].*"));
    }

    // ---- C-G5：超限 Snapshot 必须触发确定性裁剪且最终事件不被截断 --------------------

    @Test
    void oversizedSnapshotIsPrunedDeterministicallyWithoutTruncatingFinalAnswer() {
        section("C-G5 Snapshot 超限裁剪且保留最终回答");
        String oversized = "x".repeat((int) SnapshotPruner.DEFAULT_MAX_BYTES);
        StreamSnapshotEnvelope oversizedSnapshot = new StreamSnapshotEnvelope(1, false, List.of(
                ObserverTestSupport.event(oversized, false),
                ObserverTestSupport.event("最终结论不能被裁剪", true)
        ));

        long before = new SnapshotPruner().utf8Size(oversizedSnapshot);
        System.out.println("输入：单事件 " + before + " 字节，超过冻结上限 " + SnapshotPruner.DEFAULT_MAX_BYTES);

        StreamSnapshotEnvelope pruned = new SnapshotPruner().prune(oversizedSnapshot);
        long after = new SnapshotPruner().utf8Size(pruned);

        System.out.println("输出(裁剪后字节数): " + after + "，truncated=" + pruned.truncated());
        System.out.println("输出(最终事件是否完整): " + pruned.events().get(1).getResponse());

        assertTrue(before > SnapshotPruner.DEFAULT_MAX_BYTES);
        assertTrue(after <= SnapshotPruner.DEFAULT_MAX_BYTES);
        assertTrue(pruned.truncated());
        assertEquals(SnapshotPruner.TRUNCATED_VALUE, pruned.events().get(0).getResponse());
        assertEquals("最终结论不能被裁剪", pruned.events().get(1).getResponse());
    }

    // ---- C-G4：Fake Agent SUCCESS 模式生成的原始下游事件语义正确 ---------------------

    @Test
    void fakeAgentSuccessEventsCarryFinishedFlagOnlyOnLastEvent() {
        section("C-G4/C-G7 Fake Agent SUCCESS 事件序列语义");
        AgentRequest request = AgentRequest.builder().requestId("trace-demo").agentType(5).build();
        List<String> rawEvents = new FakeAgentEventFactory().successfulEvents(request, 3);

        System.out.println("输入：FakeAgentMode=" + FakeAgentMode.SUCCESS + ", 请求 requestId=" + request.getRequestId());
        rawEvents.forEach(e -> System.out.println("输出(下游原始 JSON): " + e));

        assertEquals(3, rawEvents.size());
        assertTrue(rawEvents.get(0).contains("\"finish\":false"));
        assertTrue(rawEvents.get(1).contains("\"finish\":false"));
        assertTrue(rawEvents.get(2).contains("\"finish\":true"));
        assertTrue(rawEvents.get(2).contains("trace-demo"), "requestId 必须透传，不能被 Fake 覆盖");
    }

    private void section(String title) {
        System.out.println();
        System.out.println("===== " + title + " =====");
    }

    private List<GptProcessResult> readSseFixture(String fileName) throws Exception {
        List<GptProcessResult> events = new ArrayList<>();
        for (String line : Files.readAllLines(SSE_FIXTURES.resolve(fileName))) {
            if (!line.isBlank()) {
                events.add(objectMapper.readValue(line, GptProcessResult.class));
            }
        }
        return List.copyOf(events);
    }
}


package com.jd.genie.agent.agent;

import com.alibaba.fastjson.JSON;
import com.jd.genie.agent.dto.Memory;
import com.jd.genie.agent.dto.Message;
import com.jd.genie.agent.dto.tool.ToolCall;
import com.jd.genie.agent.dto.tool.ToolChoice;
import com.jd.genie.agent.enums.AgentState;
import com.jd.genie.agent.enums.RoleType;
import com.jd.genie.agent.llm.LLM;
import com.jd.genie.agent.prompt.ToolCallPrompt;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.agent.tool.ToolCollection;
import com.jd.genie.agent.util.FileUtil;
import com.jd.genie.agent.util.SpringContextHolder;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.model.response.AgentResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReactImplAgent extends ReActAgent {

    static final int FINISH_TURN_TIMEOUT_SECONDS = 120;
    static final String FINISH_TURN_FAILURE =
            "{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"EXECUTION_ERROR\",\"retryable\":true}";
    private static final Pattern JSON_FENCE = Pattern.compile("(?s)```(?:json)?\\s*(\\{.*?})\\s*```");

    private List<ToolCall> toolCalls;
    private Integer maxObserve;
    private Integer llmTimeoutSeconds = 300;
    private String systemPromptSnapshot;
    private String nextStepPromptSnapshot;
    /** After tool results exist, the next LLM turn must emit text (no tools). */
    private boolean finishWithoutToolsAfterObservations;
    /** Bound the dependent tool chain instead of stopping after its first observation. */
    private int maxToolObservationCount = 1;

    public ReactImplAgent(AgentContext context) {
        setName("react");
        setDescription("an agent that can execute tool calls.");
        ApplicationContext applicationContext = SpringContextHolder.getApplicationContext();
        GenieConfig genieConfig = applicationContext.getBean(GenieConfig.class);

        StringBuilder toolPrompt = new StringBuilder();
        for (BaseTool tool : context.getToolCollection().getToolMap().values()) {
            toolPrompt.append(String.format("工具名：%s 工具描述：%s\n", tool.getName(), tool.getDescription()));
        }

        String promptKey = "default";
        String nextPromptKey = "default";

        setSystemPrompt(genieConfig.getReactSystemPromptMap().getOrDefault(promptKey, ToolCallPrompt.SYSTEM_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt()));
        setNextStepPrompt(genieConfig.getReactNextStepPromptMap().getOrDefault(nextPromptKey, ToolCallPrompt.NEXT_STEP_PROMPT)
                .replace("{{tools}}", toolPrompt.toString())
                .replace("{{query}}", context.getQuery())
                .replace("{{date}}", context.getDateInfo())
                .replace("{{basePrompt}}", context.getBasePrompt()));

        setSystemPromptSnapshot(getSystemPrompt());
        setNextStepPromptSnapshot(getNextStepPrompt());

        setPrinter(context.printer);
        setMaxSteps(genieConfig.getReactMaxSteps());
        setLlm(new LLM(genieConfig.getReactModelName(), ""));
        setContext(context);

        // 初始化工具集合
        availableTools = context.getToolCollection();
        setDigitalEmployeePrompt(genieConfig.getDigitalEmployeePrompt());
    }

    @Override
    public boolean think() {
        boolean finishTurn = finishWithoutToolsAfterObservations
                && toolExecutionRoundCount(getMemory()) >= Math.max(1, maxToolObservationCount);
        String filesStr = finishWithoutToolsAfterObservations
                ? FileUtil.formatFileNames(context.getProductFiles(), true)
                : FileUtil.formatFileInfo(context.getProductFiles(), true);
        setSystemPrompt(getSystemPromptSnapshot().replace("{{files}}", filesStr));
        setNextStepPrompt(getNextStepPromptSnapshot().replace("{{files}}", filesStr));

        boolean firstTurn = getMemory().getLastMessage().getRole().equals(RoleType.USER);
        if (!firstTurn) {
            Message userMsg = Message.userMessage(getNextStepPrompt(), null);
            getMemory().addMessage(userMsg);
        }
        if (printer != null) {
            printer.send("tool_thought", firstTurn ? "正在判断需要哪些资料" : "正在根据已有结果继续思考");
        }
        try {
            // 获取带工具选项的响应
            context.setStreamMessageType("tool_thought");

            int timeout = llmTimeoutSeconds == null || llmTimeoutSeconds <= 0 ? 300 : llmTimeoutSeconds;
            int requestTimeout = finishTurn ? Math.min(timeout, FINISH_TURN_TIMEOUT_SECONDS) : timeout;
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(
                    context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    finishTurn ? new ToolCollection() : availableTools,
                    finishTurn ? ToolChoice.NONE : ToolChoice.AUTO, null, context.getIsStream(), requestTimeout
            );

            LLM.ToolCallResponse response = awaitResponse(future, requestTimeout, TimeUnit.SECONDS);

            List<ToolCall> acceptedToolCalls = toolCallsForTurn(finishTurn, response.getToolCalls());
            if (acceptedToolCalls.isEmpty() && !finishTurn) {
                acceptedToolCalls = embeddedToolCalls(response.getContent(), availableTools);
            }
            String acceptedContent = contentForTurn(finishTurn, response.getContent(), getMemory());
            setToolCalls(acceptedToolCalls);

            // 记录响应信息
            if (!context.getIsStream() && acceptedContent != null && !acceptedContent.isEmpty()) {
                printer.send("tool_thought", acceptedContent);
            }

            // 创建并添加助手消息
            Message assistantMsg = !acceptedToolCalls.isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(acceptedContent, acceptedToolCalls) :
                    Message.assistantMessage(acceptedContent, null);
            getMemory().addMessage(assistantMsg);

        } catch (Exception e) {

            log.error("{} react think error", context.getRequestId(), e);
            // Emit a parseable Phase2 FAILURE envelope so orchestration does not map
            // timeouts to AGENT_INVALID_RESULT.
            getMemory().addMessage(Message.assistantMessage(toolResultFallback(getMemory()), null));
            setState(AgentState.FINISHED);
            return false;
        }

        return true;
    }

    static <T> T awaitResponse(CompletableFuture<T> future, long timeout, TimeUnit unit) throws Exception {
        try {
            return future.get(timeout, unit);
        } catch (TimeoutException ex) {
            future.cancel(true);
            throw ex;
        }
    }

    static List<ToolCall> toolCallsForTurn(boolean finishTurn, List<ToolCall> responseToolCalls) {
        if (finishTurn || responseToolCalls == null) {
            return List.of();
        }
        return responseToolCalls;
    }

    static String contentForTurn(boolean finishTurn, String responseContent) {
        return contentForTurn(finishTurn, responseContent, null);
    }

    static String contentForTurn(boolean finishTurn, String responseContent, Memory memory) {
        if (finishTurn && (responseContent == null || responseContent.isBlank())) {
            return toolResultFallback(memory);
        }
        return responseContent;
    }

    /** Preserve already successful tool evidence when the final wording call times out or is empty. */
    static String toolResultFallback(Memory memory) {
        if (memory == null || memory.getMessages() == null) {
            return FINISH_TURN_FAILURE;
        }
        StringBuilder evidence = new StringBuilder();
        for (Message message : memory.getMessages()) {
            if (message == null || message.getRole() != RoleType.TOOL
                    || message.getContent() == null || message.getContent().isBlank()
                    || message.getContent().startsWith("Tool input validation failed")) {
                continue;
            }
            if (evidence.length() > 0) {
                evidence.append("\n\n");
            }
            evidence.append(message.getContent().trim());
            if (evidence.length() >= 12_000) {
                evidence.setLength(12_000);
                evidence.append("…");
                break;
            }
        }
        if (evidence.isEmpty()) {
            return FINISH_TURN_FAILURE;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "SUCCESS");
        payload.put("output", "已取得以下工具结果，最终整理模型未正常返回，请由主规划据此汇总：\n" + evidence);
        payload.put("errorCode", null);
        payload.put("retryable", false);
        return JSON.toJSONString(payload);
    }

    /**
     * Some OpenAI-compatible providers occasionally print a function call as a
     * fenced JSON object instead of returning it in the native tool_calls field.
     * Accept one call only when its name belongs to this Agent's frozen tools.
     */
    static List<ToolCall> embeddedToolCalls(String content, ToolCollection availableTools) {
        if (content == null || content.isBlank() || availableTools == null) {
            return List.of();
        }
        Matcher matcher = JSON_FENCE.matcher(content);
        ToolCall accepted = null;
        while (matcher.find()) {
            try {
                Map<String, Object> value = JSON.parseObject(matcher.group(1), Map.class);
                Object rawName = value.get("name");
                if (rawName == null) rawName = value.get("tool");
                String name = rawName == null ? "" : String.valueOf(rawName).trim();
                if (name.isEmpty() || !availableTools.getToolMap().containsKey(name)) {
                    continue;
                }
                Object rawArguments = value.get("arguments");
                if (!(rawArguments instanceof Map<?, ?>)) {
                    continue;
                }
                if (accepted != null) {
                    return List.of();
                }
                accepted = ToolCall.builder()
                        .id("text-tool-" + UUID.randomUUID())
                        .type("function")
                        .function(ToolCall.Function.builder()
                                .name(name)
                                .arguments(JSON.toJSONString(rawArguments))
                                .build())
                        .build();
            } catch (RuntimeException ignored) {
                // Provider text remains untrusted unless every check above passes.
            }
        }
        return accepted == null ? List.of() : List.of(accepted);
    }

    @Override
    public String act() {

        if (toolCalls == null || toolCalls.isEmpty()) {
            setState(AgentState.FINISHED);
            return getMemory().getLastMessage().getContent();
        }

        if (printer != null) {
            for (ToolCall command : toolCalls) {
                String intent = describeToolStart(command);
                if (intent != null && !intent.isBlank()) {
                    printer.send("tool_thought", intent);
                }
            }
        }

        // action
        Map<String, String> toolResults = executeTools(toolCalls);
        List<String> results = new ArrayList<>();
        for (ToolCall command : toolCalls) {
            String result = toolResults.get(command.getId());
            if (!Arrays.asList("code_interpreter", "report_tool", "file_tool", "deep_search", "data_analysis").contains(command.getFunction().getName())) {
                String toolName = command.getFunction().getName();
                printer.send("tool_result", AgentResponse.ToolResult.builder()
                        .toolName(toolName)
                        .toolParam(JSON.parseObject(command.getFunction().getArguments(), Map.class))
                        .toolResult(result)
                        .build(), null);
            }

            if (maxObserve != null) {
                result = result.substring(0, Math.min(result.length(), maxObserve));
            }

            // 添加工具响应到记忆
            if ("struct_parse".equals(llm.getFunctionCallType())) {
                String content = getMemory().getLastMessage().getContent();
                getMemory().getLastMessage().setContent(content + "\n 工具执行结果为:\n" + result);
            } else { // function_call
                Message toolMsg = Message.toolMessage(
                        result,
                        command.getId(),
                        null
                );
                getMemory().addMessage(toolMsg);
            }
            results.add(result);
        }

        return String.join("\n\n", results);
    }

    @Override
    public String run(String request) {
        return super.run(request);
    }

    private static String describeToolStart(ToolCall command) {
        if (command == null || command.getFunction() == null || command.getFunction().getName() == null) {
            return null;
        }
        String name = command.getFunction().getName();
        String query = extractArg(command.getFunction().getArguments(), "query");
        String task = extractArg(command.getFunction().getArguments(), "task");
        return switch (name) {
            case "deep_search" -> query == null || query.isBlank()
                    ? "准备联网搜索"
                    : "准备联网搜索：" + truncate(query, 80);
            case "code_interpreter" -> "准备运行代码";
            case "data_analysis" -> task == null || task.isBlank()
                    ? "准备分析数据"
                    : "准备分析数据：" + truncate(task, 80);
            case "file_tool" -> "准备读写文件";
            case "report_tool" -> "准备生成报告";
            default -> "准备使用工具 " + name;
        };
    }

    private static String extractArg(String arguments, String key) {
        if (arguments == null || arguments.isBlank() || key == null) {
            return null;
        }
        try {
            Object parsed = JSON.parse(arguments);
            if (parsed instanceof Map<?, ?> map) {
                Object value = map.get(key);
                return value == null ? null : String.valueOf(value);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    static boolean hasToolObservation(Memory memory) {
        return toolObservationCount(memory) > 0;
    }

    static int toolObservationCount(Memory memory) {
        if (memory == null || memory.getMessages() == null) {
            return 0;
        }
        return (int) memory.getMessages().stream()
                .filter(message -> message != null && message.getRole() == RoleType.TOOL)
                .count();
    }

    /**
     * One reasoning round may intentionally issue several independent tools in
     * parallel. Count the assistant tool-call batches, not individual TOOL
     * messages, so a three-step chain still permits follow-up calls that depend
     * on coordinates or ids returned by the first batch.
     */
    static int toolExecutionRoundCount(Memory memory) {
        if (memory == null || memory.getMessages() == null) {
            return 0;
        }
        return (int) memory.getMessages().stream()
                .filter(message -> message != null
                        && message.getRole() == RoleType.ASSISTANT
                        && message.getToolCalls() != null
                        && !message.getToolCalls().isEmpty())
                .count();
    }

    private static String truncate(String text, int maxChars) {
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "…";
    }

}

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
import java.util.concurrent.CompletableFuture;

/**
 * 工具调用代理 - 处理工具/函数调用的基础代理类
 */
@Data
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ReactImplAgent extends ReActAgent {

    private List<ToolCall> toolCalls;
    private Integer maxObserve;
    private Integer llmTimeoutSeconds = 300;
    private String systemPromptSnapshot;
    private String nextStepPromptSnapshot;
    /** After tool results exist, the next LLM turn must emit text (no tools). */
    private boolean finishWithoutToolsAfterObservations;

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
        boolean finishTurn = finishWithoutToolsAfterObservations && hasToolObservation(getMemory());
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
            CompletableFuture<LLM.ToolCallResponse> future = getLlm().askTool(
                    context,
                    getMemory().getMessages(),
                    Message.systemMessage(getSystemPrompt(), null),
                    finishTurn ? new ToolCollection() : availableTools,
                    finishTurn ? ToolChoice.NONE : ToolChoice.AUTO, null, context.getIsStream(), timeout
            );

            LLM.ToolCallResponse response = future.get();

            setToolCalls(response.getToolCalls());

            // 记录响应信息
            if (!context.getIsStream() && response.getContent() != null && !response.getContent().isEmpty()) {
                printer.send("tool_thought", response.getContent());
            }

            // 创建并添加助手消息
            Message assistantMsg = response.getToolCalls() != null && !response.getToolCalls().isEmpty() && !"struct_parse".equals(llm.getFunctionCallType()) ?
                    Message.fromToolCalls(response.getContent(), response.getToolCalls()) :
                    Message.assistantMessage(response.getContent(), null);
            getMemory().addMessage(assistantMsg);

        } catch (Exception e) {

            log.error("{} react think error", context.getRequestId(), e);
            // Emit a parseable Phase2 FAILURE envelope so orchestration does not map
            // timeouts to AGENT_INVALID_RESULT.
            getMemory().addMessage(Message.assistantMessage(
                    "{\"status\":\"FAILURE\",\"output\":null,\"errorCode\":\"EXECUTION_ERROR\",\"retryable\":true}",
                    null));
            setState(AgentState.FINISHED);
            return false;
        }

        return true;
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
        if (memory == null || memory.getMessages() == null) {
            return false;
        }
        for (Message message : memory.getMessages()) {
            if (message != null && message.getRole() == RoleType.TOOL) {
                return true;
            }
        }
        return false;
    }

    private static String truncate(String text, int maxChars) {
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "…";
    }

}
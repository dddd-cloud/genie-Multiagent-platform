package com.jd.genie.platform.phase2.runtime.request;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.agentbridge.AgentExecutionRequestFactory;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class Phase2GptQueryRequestValidator {
    private static final Set<String> EXECUTION_MODES = Set.of("AUTO", "DIRECT", "ORCHESTRATED");
    private static final Pattern TEAM_ID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final int MAX_ALLOWED_AGENT_IDS = 20;
    private static final int MAX_LONG_TERM_MEMORY_CODE_POINTS = 12_000;
    private static final int MAX_CONVERSATION_SUMMARY_CODE_POINTS = 20_000;
    private static final int MAX_LOCAL_CONTEXT_CODE_POINTS = 30_000;

    private final AgentExecutionRequestFactory requestFactory;

    public Phase2GptQueryRequestValidator() {
        this(new AgentExecutionRequestFactory());
    }

    public Phase2GptQueryRequestValidator(AgentExecutionRequestFactory requestFactory) {
        this.requestFactory = requestFactory;
    }

    public ValidatedPhase2Request validate(
            Phase2GptQueryRequest request,
            CurrentUser currentUser
    ) {
        if (request == null) {
            throw validationError("request must not be null");
        }
        String executionMode = normalizedExecutionMode(request.getExecutionMode());
        LocalContextSnapshot localContext = normalizedLocalContext(request.getLocalContext());
        List<String> allowedAgentIds = normalizedAllowedAgentIds(request.getAllowedAgentIds(), executionMode);
        String teamId = normalizedTeamId(request.getTeamId(), executionMode, allowedAgentIds);
        List<String> attachmentIds = normalizedAttachmentIds(request.getAttachmentIds());
        String modelName = normalizedModelName(request.getModelName());
        GptQueryReq trustedRequest = requestFactory.trustedRequest(
                GptQueryReq.builder()
                        .sessionId(request.getSessionId())
                        .requestId(request.getRequestId())
                        .query(request.getQuery())
                        .deepThink(request.getDeepThink())
                        .outputStyle(request.getOutputStyle())
                        .attachmentIds(attachmentIds)
                        .modelName(modelName)
                        .build(),
                currentUser
        );
        trustedRequest.setAttachmentIds(attachmentIds);
        trustedRequest.setModelName(modelName);
        return new ValidatedPhase2Request(
                trustedRequest,
                executionMode,
                allowedAgentIds,
                teamId,
                localContext,
                attachmentIds
        );
    }

    private String normalizedExecutionMode(String value) {
        String mode = trim(value);
        if (!EXECUTION_MODES.contains(mode)) {
            throw validationError("executionMode must be AUTO, DIRECT, or ORCHESTRATED");
        }
        return mode;
    }

    private LocalContextSnapshot normalizedLocalContext(Phase2GptQueryRequest.LocalContext value) {
        if (value == null || !Integer.valueOf(1).equals(value.getSchemaVersion())) {
            throw localContextInvalid("localContext.schemaVersion must be 1");
        }
        String longTermMemory = value.getLongTermMemory();
        String conversationSummary = value.getConversationSummary();
        if (longTermMemory == null || conversationSummary == null) {
            throw localContextInvalid("localContext fields must not be null");
        }
        int memoryLength = codePointLength(longTermMemory);
        int summaryLength = codePointLength(conversationSummary);
        if (memoryLength > MAX_LONG_TERM_MEMORY_CODE_POINTS
                || summaryLength > MAX_CONVERSATION_SUMMARY_CODE_POINTS
                || memoryLength + summaryLength > MAX_LOCAL_CONTEXT_CODE_POINTS) {
            throw new AgentBridgeException(
                    MvpErrorCode.LOCAL_CONTEXT_TOO_LARGE,
                    "localContext exceeds its Unicode code point limit"
            );
        }
        return new LocalContextSnapshot(longTermMemory, conversationSummary);
    }

    private List<String> normalizedAllowedAgentIds(List<String> value, String executionMode) {
        if (value == null || value.size() > MAX_ALLOWED_AGENT_IDS) {
            throw validationError("allowedAgentIds must contain at most 20 unique values");
        }
        List<String> normalized = value.stream().map(this::trim).toList();
        if (normalized.stream().anyMatch(item -> item == null || item.isBlank())
                || normalized.stream().distinct().count() != normalized.size()) {
            throw validationError("allowedAgentIds must contain unique non-blank values");
        }
        if ("DIRECT".equals(executionMode) && !normalized.isEmpty()) {
            throw validationError("DIRECT requests must not specify allowedAgentIds");
        }
        return List.copyOf(normalized);
    }

    private String normalizedTeamId(String value, String executionMode, List<String> allowedAgentIds) {
        String teamId = trim(value);
        if (teamId == null || teamId.isEmpty()) {
            return null;
        }
        if (!TEAM_ID_PATTERN.matcher(teamId).matches()) {
            throw validationError("teamId must be a UUID");
        }
        if ("DIRECT".equals(executionMode)) {
            throw validationError("DIRECT requests must not specify teamId");
        }
        if (!allowedAgentIds.isEmpty()) {
            throw validationError("teamId and allowedAgentIds are mutually exclusive");
        }
        return teamId;
    }

    private List<String> normalizedAttachmentIds(List<String> value) {
        if (value == null || value.isEmpty()) {
            return List.of();
        }
        if (value.size() > 10) {
            throw validationError("attachmentIds must contain at most 10 values");
        }
        List<String> normalized = value.stream().map(this::trim).toList();
        if (normalized.stream().anyMatch(item -> item == null || item.isBlank())
                || normalized.stream().distinct().count() != normalized.size()) {
            throw validationError("attachmentIds must contain unique non-blank values");
        }
        for (String id : normalized) {
            if (!TEAM_ID_PATTERN.matcher(id).matches()) {
                throw validationError("attachmentIds must be UUIDs");
            }
        }
        return List.copyOf(normalized);
    }

    private String normalizedModelName(String value) {
        String modelName = trim(value);
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }
        if (modelName.length() > 128) {
            throw validationError("modelName must contain at most 128 characters");
        }
        return modelName;
    }

    private AgentBridgeException validationError(String message) {
        return new AgentBridgeException(MvpErrorCode.VALIDATION_ERROR, message);
    }

    private AgentBridgeException localContextInvalid(String message) {
        return new AgentBridgeException(MvpErrorCode.LOCAL_CONTEXT_INVALID, message);
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }

    private int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }

    public record ValidatedPhase2Request(
            GptQueryReq trustedRequest,
            String executionMode,
            List<String> allowedAgentIds,
            String teamId,
            LocalContextSnapshot localContext,
            List<String> attachmentIds
    ) {
        public ValidatedPhase2Request(
                GptQueryReq trustedRequest,
                String executionMode,
                List<String> allowedAgentIds,
                LocalContextSnapshot localContext
        ) {
            this(trustedRequest, executionMode, allowedAgentIds, null, localContext, List.of());
        }

        public ValidatedPhase2Request {
            attachmentIds = attachmentIds == null ? List.of() : List.copyOf(attachmentIds);
        }
    }

    public record LocalContextSnapshot(String longTermMemory, String conversationSummary) {
    }
}

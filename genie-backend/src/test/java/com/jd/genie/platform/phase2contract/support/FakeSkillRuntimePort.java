package com.jd.genie.platform.phase2contract.support;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.agent.tool.BaseTool;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.AgentSkillBindingSpec;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionCommand;
import com.jd.genie.platform.phase2contract.dto.SkillExecutionResult;
import com.jd.genie.platform.phase2contract.dto.SkillResource;
import com.jd.genie.platform.phase2contract.dto.SkillRuntimePackage;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import com.jd.genie.platform.phase2contract.port.SkillRuntimePort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class FakeSkillRuntimePort implements SkillRuntimePort {

    public enum CallType {
        RESOLVE_FOR_BINDINGS,
        RESOLVE_FOR_AGENT,
        READ_RESOURCE,
        BUILD_RUNTIME_TOOLS,
        EXECUTE_ENTRYPOINT
    }

    public record CallRecord(
        CallType type,
        String userId,
        String agentId,
        String skillId,
        List<AgentSkillBindingSpec> bindings,
        Boolean requireEnabled
    ) {
    }

    private final List<CallRecord> calls = new CopyOnWriteArrayList<>();
    private final Map<String, SkillRuntimePackage> packagesBySkillId = new ConcurrentHashMap<>();
    private final Map<String, List<AgentSkillBindingSpec>> bindingsByAgent = new ConcurrentHashMap<>();
    private final Map<String, SkillResource> resources = new ConcurrentHashMap<>();
    private final Map<String, List<BaseTool>> runtimeToolsByAgent = new ConcurrentHashMap<>();
    private final Map<String, SkillExecutionResult> executionResults = new ConcurrentHashMap<>();
    private volatile RuntimeException resolveException;
    private volatile RuntimeException readException;
    private volatile RuntimeException executeException;

    public void registerPackage(SkillRuntimePackage skillPackage) {
        if (skillPackage == null || skillPackage.skillId() == null || skillPackage.skillId().isBlank()) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "skill package must have skillId"
            );
        }
        packagesBySkillId.put(skillPackage.skillId(), skillPackage);
    }

    public void seedAgentBindings(CurrentUser user, String agentId, List<AgentSkillBindingSpec> bindings) {
        requireUser(user);
        requireId(agentId, "agentId");
        String key = scopedKey(user, agentId);
        if (bindings == null || bindings.isEmpty()) {
            bindingsByAgent.remove(key);
        } else {
            bindingsByAgent.put(key, List.copyOf(bindings));
        }
    }

    public void registerResource(SkillResource resource) {
        if (resource == null || resource.skillId() == null || resource.relativePath() == null) {
            throw new Phase2ContractException(
                MvpErrorCode.VALIDATION_ERROR,
                "resource must include skillId and relativePath"
            );
        }
        resources.put(resourceKey(resource.skillId(), resource.relativePath()), resource);
    }

    public void setRuntimeTools(CurrentUser user, String agentId, List<BaseTool> tools) {
        requireUser(user);
        requireId(agentId, "agentId");
        runtimeToolsByAgent.put(scopedKey(user, agentId), tools == null ? List.of() : List.copyOf(tools));
    }

    public void setExecutionResult(String skillId, String entrypointName, SkillExecutionResult result) {
        requireId(skillId, "skillId");
        requireId(entrypointName, "entrypointName");
        executionResults.put(skillId + "\u0000" + entrypointName, result);
    }

    public void setResolveException(RuntimeException exception) {
        this.resolveException = exception;
    }

    public void setReadException(RuntimeException exception) {
        this.readException = exception;
    }

    public void setExecuteException(RuntimeException exception) {
        this.executeException = exception;
    }

    public List<CallRecord> getCalls() {
        return Collections.unmodifiableList(new ArrayList<>(calls));
    }

    public void reset() {
        calls.clear();
        packagesBySkillId.clear();
        bindingsByAgent.clear();
        resources.clear();
        runtimeToolsByAgent.clear();
        executionResults.clear();
        resolveException = null;
        readException = null;
        executeException = null;
    }

    @Override
    public List<SkillRuntimePackage> resolveForBindings(
        CurrentUser user,
        List<AgentSkillBindingSpec> bindings,
        boolean requireEnabled
    ) {
        calls.add(new CallRecord(
            CallType.RESOLVE_FOR_BINDINGS,
            user == null ? null : user.userId(),
            null,
            null,
            bindings == null ? null : List.copyOf(bindings),
            requireEnabled
        ));
        if (resolveException != null) {
            throw resolveException;
        }
        requireUser(user);
        List<AgentSkillBindingSpec> normalized = bindings == null ? List.of() : List.copyOf(bindings);
        return resolvePackages(normalized, requireEnabled);
    }

    @Override
    public List<SkillRuntimePackage> resolveForAgent(
        CurrentUser user,
        String agentId,
        boolean requireEnabled
    ) {
        calls.add(new CallRecord(
            CallType.RESOLVE_FOR_AGENT,
            user == null ? null : user.userId(),
            agentId,
            null,
            null,
            requireEnabled
        ));
        if (resolveException != null) {
            throw resolveException;
        }
        requireUser(user);
        requireId(agentId, "agentId");
        List<AgentSkillBindingSpec> bindings = bindingsByAgent.getOrDefault(scopedKey(user, agentId), List.of());
        return resolvePackages(bindings, requireEnabled);
    }

    @Override
    public SkillResource readResource(CurrentUser user, String skillId, String relativePath) {
        calls.add(new CallRecord(
            CallType.READ_RESOURCE,
            user == null ? null : user.userId(),
            null,
            skillId,
            null,
            null
        ));
        if (readException != null) {
            throw readException;
        }
        requireUser(user);
        requireId(skillId, "skillId");
        requireId(relativePath, "relativePath");
        if (relativePath.contains("..") || relativePath.startsWith("/") || relativePath.startsWith("\\")) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "path rejected");
        }
        SkillResource resource = resources.get(resourceKey(skillId, relativePath));
        if (resource == null) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_RESOURCE_NOT_FOUND, "resource not found");
        }
        return resource;
    }

    @Override
    public List<BaseTool> buildRuntimeTools(
        CurrentUser user,
        AgentRuntimeProfile profile,
        AgentContext context
    ) {
        calls.add(new CallRecord(
            CallType.BUILD_RUNTIME_TOOLS,
            user == null ? null : user.userId(),
            profile == null ? null : profile.agentId(),
            null,
            null,
            null
        ));
        requireUser(user);
        if (profile == null) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "profile must not be null");
        }
        return runtimeToolsByAgent.getOrDefault(scopedKey(user, profile.agentId()), List.of());
    }

    @Override
    public SkillExecutionResult executeEntrypoint(CurrentUser user, SkillExecutionCommand command) {
        calls.add(new CallRecord(
            CallType.EXECUTE_ENTRYPOINT,
            user == null ? null : user.userId(),
            null,
            command == null ? null : command.skillId(),
            null,
            null
        ));
        if (executeException != null) {
            throw executeException;
        }
        requireUser(user);
        if (command == null) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "command must not be null");
        }
        requireId(command.skillId(), "skillId");
        requireId(command.entrypointName(), "entrypointName");
        SkillExecutionResult configured = executionResults.get(command.skillId() + "\u0000" + command.entrypointName());
        if (configured != null) {
            return configured;
        }
        return new SkillExecutionResult(
            false,
            "",
            "",
            null,
            MvpErrorCode.SKILL_ENTRYPOINT_NOT_FOUND,
            "entrypoint not found"
        );
    }

    private List<SkillRuntimePackage> resolvePackages(List<AgentSkillBindingSpec> bindings, boolean requireEnabled) {
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<SkillRuntimePackage> result = new ArrayList<>();
        for (AgentSkillBindingSpec binding : bindings.stream()
            .sorted(Comparator.comparingInt(AgentSkillBindingSpec::sortOrder))
            .toList()) {
            requireId(binding.skillId(), "skillId");
            SkillRuntimePackage skillPackage = packagesBySkillId.get(binding.skillId());
            if (skillPackage == null) {
                throw new Phase2ContractException(MvpErrorCode.RESOURCE_NOT_FOUND, "skill not found");
            }
            if (!"ENABLED".equals(skillPackage.status())) {
                if (requireEnabled) {
                    throw new Phase2ContractException(MvpErrorCode.AGENT_INVALID_STATE, "skill not enabled");
                }
                continue;
            }
            result.add(new SkillRuntimePackage(
                skillPackage.skillId(),
                skillPackage.skillVersion(),
                binding.sortOrder(),
                skillPackage.status(),
                skillPackage.skillKey(),
                skillPackage.name(),
                skillPackage.description(),
                skillPackage.packageMode(),
                skillPackage.packageVersion(),
                skillPackage.packageHash(),
                skillPackage.instructionMarkdown(),
                skillPackage.outputRequirement(),
                skillPackage.resourceManifest(),
                skillPackage.entrypoints(),
                skillPackage.capabilityKeys()
            ));
        }
        return List.copyOf(result);
    }

    private static void requireUser(CurrentUser user) {
        if (user == null) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, "user must not be null");
        }
    }

    private static void requireId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new Phase2ContractException(MvpErrorCode.VALIDATION_ERROR, name + " must not be blank");
        }
    }

    private static String scopedKey(CurrentUser user, String resourceId) {
        return user.tenantId() + "\u0000" + user.userId() + "\u0000" + resourceId.trim();
    }

    private static String resourceKey(String skillId, String relativePath) {
        return skillId + "\u0000" + relativePath;
    }
}

package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentSkillBindingRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

abstract class AgentRuntimeCatalogTestSupport extends Phase2AMySqlTestSupport {
    static final String RAW_PROMPT = "Answer with {{query}} and use {{tools}} when needed.";
    static final String STRUCTURED_PROMPT = """
        {"role":"assistant","objective":"Help the user with {{query}}","outputFormat":"concise"}
        """;

    @Autowired
    protected AgentRuntimeCatalogPort runtimeCatalogPort;
    @Autowired
    protected AgentDefinitionService agentService;
    @Autowired
    protected SkillDefinitionService skillService;
    @Autowired
    protected AgentDefinitionMapper agentMapper;

    protected SkillResponse skill(String name, int index) {
        return skillService.createSkill(userA(), new SkillCreateRequest(
            name,
            "Skill " + index + " description",
            "Instruction " + index,
            "Requirement " + index,
            List.of()
        ));
    }

    protected SkillResponse skill(CurrentUser user, String name, int index) {
        return skillService.createSkill(user, new SkillCreateRequest(
            name,
            "Skill " + index + " description",
            "Instruction " + index,
            "Requirement " + index,
            List.of()
        ));
    }

    protected AgentResponse draftAgent(String name, List<SkillResponse> skills) {
        return agentService.createAgent(userA(), agentRequest(name, null, skills));
    }

    protected AgentResponse onlineAgent(String name, List<SkillResponse> skills) {
        AgentResponse draft = draftAgent(name, skills);
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of()));
        return agentService.onlineAgent(userA(), draft.id(), draft.version());
    }

    protected AgentResponse onlineAgent(CurrentUser user, String name, List<SkillResponse> skills) {
        AgentResponse draft = agentService.createAgent(user, agentRequest(name, null, skills));
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of()));
        return agentService.onlineAgent(user, draft.id(), draft.version());
    }

    protected AgentResponse offlineAgent(String name) {
        AgentResponse online = onlineAgent(name, List.of());
        return agentService.offlineAgent(userA(), online.id(), online.version());
    }

    protected AgentResponse onlineAgentWithModel(String name, String modelName) {
        AgentResponse draft = agentService.createAgent(userA(), agentRequest(name, modelName, List.of()));
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of()));
        return agentService.onlineAgent(userA(), draft.id(), draft.version());
    }

    protected AgentResponse updateAgentPrompt(AgentResponse agent, String systemPrompt) {
        return agentService.updateAgent(userA(), agent.id(), new AgentUpdateRequest(
            agent.version(),
            agent.name(),
            agent.description(),
            "RAW",
            null,
            systemPrompt,
            agent.modelName(),
            List.of(),
            List.of()
        ));
    }

    protected SkillResponse updateSkill(SkillResponse skill, String instruction) {
        return skillService.updateSkill(userA(), skill.id(), new SkillUpdateRequest(
            skill.version(),
            skill.name(),
            skill.description(),
            instruction,
            skill.outputRequirement(),
            List.of()
        ));
    }

    private AgentCreateRequest agentRequest(String name, String modelName, List<SkillResponse> skills) {
        return new AgentCreateRequest(
            name,
            name + " description",
            "RAW",
            null,
            RAW_PROMPT,
            modelName,
            skills.stream()
                .map(skill -> new AgentSkillBindingRequest(skill.id(), skills.indexOf(skill) + 1))
                .toList(),
            List.of()
        );
    }
}

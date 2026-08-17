package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2.configuration.agent.api.AgentApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.agent.api.Phase2AgentController;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.model.ModelCatalogService;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import com.jd.genie.platform.phase2.configuration.prompt.api.Phase2PromptPreviewController;
import com.jd.genie.platform.phase2.configuration.prompt.api.PromptApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.skill.api.Phase2SkillController;
import com.jd.genie.platform.phase2.configuration.skill.api.SkillApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import com.jd.genie.platform.phase2.configuration.team.api.Phase2TeamController;
import com.jd.genie.platform.phase2.configuration.team.api.TeamApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

abstract class Phase2AApiMySqlTestSupport extends Phase2AMySqlTestSupport {
    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    protected CurrentUser currentUser = userA();
    protected final CurrentUserProvider currentUserProvider = () -> currentUser;

    @Autowired protected AgentDefinitionService agentService;
    @Autowired protected SkillDefinitionService skillService;
    @Autowired protected AgentTeamService teamService;
    @Autowired protected ModelCatalogService modelCatalogService;
    @Autowired protected PromptPreviewService promptPreviewService;

    protected MockMvc agentMvc() {
        return MockMvcBuilders.standaloneSetup(new Phase2AgentController(agentService, currentUserProvider, objectMapper))
            .setControllerAdvice(new AgentApiExceptionHandler())
            .build();
    }

    protected MockMvc skillMvc() {
        return MockMvcBuilders.standaloneSetup(new Phase2SkillController(skillService, currentUserProvider))
            .setControllerAdvice(new SkillApiExceptionHandler())
            .build();
    }

    protected MockMvc teamMvc() {
        return MockMvcBuilders.standaloneSetup(new Phase2TeamController(teamService, currentUserProvider))
            .setControllerAdvice(new TeamApiExceptionHandler())
            .build();
    }

    protected MockMvc promptMvc() {
        return MockMvcBuilders.standaloneSetup(new Phase2PromptPreviewController(promptPreviewService, currentUserProvider))
            .setControllerAdvice(new PromptApiExceptionHandler())
            .build();
    }

    protected JsonNode read(String body) throws Exception {
        return objectMapper.readTree(body);
    }

    protected String rawAgentBody(String name) {
        return """
            {
              "name":"%s",
              "description":"description",
              "promptMode":"RAW",
              "promptConfig":null,
              "systemPrompt":"raw prompt # Skills {\\"json\\":true}",
              "modelName":"system-default",
              "skills":[],
              "capabilityKeys":["builtin:file"]
            }
            """.formatted(name);
    }

    protected String structuredAgentBody(String name, String skillId) {
        return """
            {
              "name":"%s",
              "description":"description",
              "promptMode":"STRUCTURED",
              "promptConfig":"{\\"objective\\":\\"Do research\\",\\"role\\":\\"Assistant\\"}",
              "systemPrompt":"forged frontend prompt",
              "modelName":"system-default",
              "skills":[{"skillId":"%s","sortOrder":1}],
              "capabilityKeys":["builtin:file"]
            }
            """.formatted(name, skillId);
    }

    protected String skillBody(String name) {
        return """
            {
              "name":"%s",
              "description":"description",
              "instruction":"Instruction",
              "outputRequirement":"Requirement",
              "capabilityKeys":["builtin:file"]
            }
            """.formatted(name);
    }
}

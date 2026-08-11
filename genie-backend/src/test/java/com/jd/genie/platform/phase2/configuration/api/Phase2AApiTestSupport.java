package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.configuration.agent.api.AgentApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.memory.api.MemoryApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.model.api.ModelApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.prompt.api.PromptApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.skill.api.SkillApiExceptionHandler;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

abstract class Phase2AApiTestSupport {
    protected final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    protected CurrentUser user = new CurrentUser("tenant-a", "owner-a", "owner-a", "Owner A", UserRole.USER);
    protected final CurrentUserProvider currentUserProvider = () -> user;

    protected MockMvc mvc(Object... controllers) {
        return MockMvcBuilders.standaloneSetup(controllers)
            .setControllerAdvice(
                new AgentApiExceptionHandler(),
                new SkillApiExceptionHandler(),
                new MemoryApiExceptionHandler(),
                new ModelApiExceptionHandler(),
                new PromptApiExceptionHandler()
            )
            .build();
    }

    protected AgentResponse agent(String id, String status, long version) {
        return new AgentResponse(
            id,
            "Research Agent",
            "Researches topics",
            "STRUCTURED",
            "{\"role\":\"researcher\"}",
            "compiled prompt",
            "qwen-plus",
            status,
            version,
            List.of("skill-1"),
            List.of("builtin:file"),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z")
        );
    }

    protected AgentResponse rawAgent(String id, String status, long version) {
        return new AgentResponse(
            id,
            "Raw Agent",
            "Uses raw prompt",
            "RAW",
            null,
            "raw prompt source",
            null,
            status,
            version,
            List.of(),
            List.of(),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z")
        );
    }

    protected SkillResponse skill(String id, String status, long version) {
        return new SkillResponse(
            id,
            "Summarize",
            "Summarize source material",
            "Produce a concise summary.",
            "Markdown summary with key points.",
            status,
            version,
            List.of("builtin:file"),
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-08-02T00:00:00Z")
        );
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}

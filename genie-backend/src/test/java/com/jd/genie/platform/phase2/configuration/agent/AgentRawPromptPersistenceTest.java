package com.jd.genie.platform.phase2.configuration.agent;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentCreateRequest;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentUpdateRequest;
import com.jd.genie.platform.phase2.configuration.agent.entity.AgentDefinitionEntity;
import com.jd.genie.platform.phase2.configuration.agent.exception.AgentConfigurationException;
import com.jd.genie.platform.phase2.configuration.agent.mapper.AgentDefinitionMapper;
import com.jd.genie.platform.phase2.configuration.agent.service.AgentDefinitionService;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewRequest;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewResponse;
import com.jd.genie.platform.phase2.configuration.prompt.PromptPreviewService;
import com.jd.genie.platform.phase2.configuration.support.Phase2AMySqlTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRawPromptPersistenceTest extends Phase2AMySqlTestSupport {

    private static final String RAW_WITH_RESERVED_HEADINGS = """
        # Skills
        Treat this as user-authored source, not a generated skills section.

        # 运行时上下文
        This heading is literal user content.

        # Agent Configuration
        This heading is also literal user content.

        Return JSON like {"ok": true} for {{query}}.
        """.trim();

    @Autowired
    private AgentDefinitionService agentService;
    @Autowired
    private AgentDefinitionMapper agentMapper;
    @Autowired
    private PromptPreviewService previewService;

    @Test
    void rawCreateStoresSourcePromptAndNullPromptConfigEvenWithReservedHeadings() {
        AgentResponse created = createRaw("Raw Agent", RAW_WITH_RESERVED_HEADINGS);

        AgentDefinitionEntity stored = stored(created.id());
        assertEquals("RAW", stored.getPromptMode());
        assertEquals(RAW_WITH_RESERVED_HEADINGS, stored.getSystemPrompt());
        assertNull(stored.getPromptConfig());
        assertFalse(stored.getSystemPrompt().contains("# Platform Execution Boundary"));
        assertFalse(stored.getSystemPrompt().contains("No enabled skills are attached."));
    }

    @Test
    void promptPreviewReturnsCompiledRawTemplateWithoutWritingDatabase() {
        AgentResponse created = createRaw("Preview Agent", RAW_WITH_RESERVED_HEADINGS);
        AgentDefinitionEntity before = stored(created.id());

        PromptPreviewResponse preview = previewService.preview(userA(), new PromptPreviewRequest(
            "RAW",
            "{\"objective\":\"ignored\"}",
            RAW_WITH_RESERVED_HEADINGS,
            null,
            List.of()
        ));

        AgentDefinitionEntity after = stored(created.id());
        assertTrue(preview.compiledSystemPromptTemplate().contains("# Platform Execution Boundary"));
        assertTrue(preview.compiledSystemPromptTemplate().contains(RAW_WITH_RESERVED_HEADINGS));
        assertEquals(before.getSystemPrompt(), after.getSystemPrompt());
        assertEquals(before.getPromptConfig(), after.getPromptConfig());
        assertEquals(before.getVersion(), after.getVersion());
    }

    @Test
    void rawUpdatesAndModeTransitionsUseCorrectPersistenceSemantics() {
        AgentResponse raw = createRaw("Transition Agent", "initial raw prompt");

        AgentResponse rawUpdated = agentService.updateAgent(userA(), raw.id(), new AgentUpdateRequest(
            raw.version(),
            raw.name(),
            raw.description(),
            "RAW",
            "{\"objective\":\"ignored\"}",
            "new raw prompt",
            raw.modelName(),
            List.of(),
            List.of()
        ));
        assertRawStored(rawUpdated.id(), "new raw prompt");

        AgentResponse structured = agentService.updateAgent(userA(), rawUpdated.id(), new AgentUpdateRequest(
            rawUpdated.version(),
            rawUpdated.name(),
            rawUpdated.description(),
            "STRUCTURED",
            "{\"role\":\"planner\",\"objective\":\"Plan with {{query}}\"}",
            "FORGED_FRONTEND_COMPILED_TEXT",
            rawUpdated.modelName(),
            List.of(),
            List.of()
        ));
        AgentDefinitionEntity structuredStored = stored(structured.id());
        assertEquals("STRUCTURED", structuredStored.getPromptMode());
        assertTrue(structuredStored.getPromptConfig().contains("planner"));
        assertTrue(structuredStored.getPromptConfig().contains("Plan with {{query}}"));
        assertTrue(structuredStored.getSystemPrompt().contains("# Platform Execution Boundary"));
        assertTrue(structuredStored.getSystemPrompt().contains("Plan with {{query}}"));
        assertFalse(structuredStored.getSystemPrompt().contains("FORGED_FRONTEND_COMPILED_TEXT"));

        AgentResponse rawAgain = agentService.updateAgent(userA(), structured.id(), new AgentUpdateRequest(
            structured.version(),
            structured.name(),
            structured.description(),
            "RAW",
            "{\"objective\":\"ignored again\"}",
            RAW_WITH_RESERVED_HEADINGS,
            structured.modelName(),
            List.of(),
            List.of()
        ));
        assertRawStored(rawAgain.id(), RAW_WITH_RESERVED_HEADINGS);
    }

    @Test
    void rawValidationStillRejectsUnknownPlaceholdersAndOversizedSource() {
        AgentConfigurationException unknown = assertThrows(AgentConfigurationException.class,
            () -> createRaw("Unknown Placeholder", "Use {{secret}}"));
        assertEquals(MvpErrorCode.PROMPT_INVALID, unknown.code());

        String oversized = "a".repeat(20_001);
        AgentConfigurationException tooLong = assertThrows(AgentConfigurationException.class,
            () -> createRaw("Too Long", oversized));
        assertEquals(MvpErrorCode.VALIDATION_ERROR, tooLong.code());
    }

    private AgentResponse createRaw(String name, String rawPrompt) {
        return agentService.createAgent(userA(), new AgentCreateRequest(
            name,
            "description",
            "RAW",
            null,
            rawPrompt,
            null,
            List.of(),
            List.of()
        ));
    }

    private void assertRawStored(String agentId, String expectedPrompt) {
        AgentDefinitionEntity stored = stored(agentId);
        assertEquals("RAW", stored.getPromptMode());
        assertEquals(expectedPrompt, stored.getSystemPrompt());
        assertNull(stored.getPromptConfig());
        assertFalse(stored.getSystemPrompt().contains("# Platform Execution Boundary"));
    }

    private AgentDefinitionEntity stored(String agentId) {
        return agentMapper.selectOwnedById(userA().tenantId(), userA().userId(), agentId);
    }
}

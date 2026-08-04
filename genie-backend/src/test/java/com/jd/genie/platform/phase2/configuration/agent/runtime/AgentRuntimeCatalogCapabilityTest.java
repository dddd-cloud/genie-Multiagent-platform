package com.jd.genie.platform.phase2.configuration.agent.runtime;

import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.agent.dto.AgentResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2contract.capability.CapabilityKeys;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.dto.ToolBindingView;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeCatalogCapabilityTest extends AgentRuntimeCatalogTestSupport {

    @Test
    void mergesDirectAndEnabledSkillCapabilitiesWithStableDedupedOrder() {
        SkillResponse first = skill("First", 1);
        SkillResponse second = skill("Second", 2);
        AgentResponse online = onlineAgent("Runtime", List.of(first, second));
        fakeToolBindingPort.setResolveResult(new ToolBindingView(
            List.of(CapabilityKeys.BUILTIN_FILE, CapabilityKeys.BUILTIN_REPORT),
            Map.of(
                first.id(), List.of(CapabilityKeys.BUILTIN_REPORT, CapabilityKeys.BUILTIN_DEEP_SEARCH),
                second.id(), List.of(CapabilityKeys.BUILTIN_FILE, CapabilityKeys.BUILTIN_DATA_ANALYSIS),
                "unbound-skill", List.of(CapabilityKeys.BUILTIN_CODE_INTERPRETER)
            ),
            List.of()
        ));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(List.of(
            CapabilityKeys.BUILTIN_FILE,
            CapabilityKeys.BUILTIN_REPORT,
            CapabilityKeys.BUILTIN_DEEP_SEARCH,
            CapabilityKeys.BUILTIN_DATA_ANALYSIS
        ), profile.capabilityKeys());
    }

    @Test
    void disabledSkillCapabilitiesAreIgnored() {
        SkillResponse enabled = skill("Enabled", 1);
        SkillResponse disabled = skill("Disabled", 2);
        AgentResponse online = onlineAgent("Runtime", List.of(enabled, disabled));
        skillService.disableSkill(userA(), disabled.id(), disabled.version());
        fakeToolBindingPort.setResolveResult(new ToolBindingView(
            List.of(),
            Map.of(
                enabled.id(), List.of(CapabilityKeys.BUILTIN_FILE),
                disabled.id(), List.of(CapabilityKeys.BUILTIN_REPORT)
            ),
            List.of()
        ));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertEquals(List.of(CapabilityKeys.BUILTIN_FILE), profile.capabilityKeys());
    }

    @Test
    void invalidCapabilitiesFailTheWholeProfile() {
        AgentResponse online = onlineAgent("Runtime", List.of());
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of(CapabilityKeys.BUILTIN_FILE)));

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), online.id()));
        assertEquals(MvpErrorCode.AGENT_INVALID_STATE, ex.errorCode());
    }

    @Test
    void emptyCapabilitiesAreLegalImmutableEmptyList() {
        AgentResponse online = onlineAgent("Runtime", List.of());
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of(), Map.of(), List.of()));

        AgentRuntimeProfile profile = runtimeCatalogPort.loadOnlineProfile(userA(), online.id());

        assertTrue(profile.capabilityKeys().isEmpty());
        assertThrows(UnsupportedOperationException.class, () -> profile.capabilityKeys().add(CapabilityKeys.BUILTIN_FILE));
    }

    @Test
    void malformedResolvedCapabilityFailsAsToolBindingInvalid() {
        AgentResponse online = onlineAgent("Runtime", List.of());
        fakeToolBindingPort.setResolveResult(new ToolBindingView(List.of("bad capability"), Map.of(), List.of()));

        Phase2ContractException ex = assertThrows(Phase2ContractException.class,
            () -> runtimeCatalogPort.loadOnlineProfile(userA(), online.id()));
        assertEquals(MvpErrorCode.TOOL_BINDING_INVALID, ex.errorCode());
    }
}

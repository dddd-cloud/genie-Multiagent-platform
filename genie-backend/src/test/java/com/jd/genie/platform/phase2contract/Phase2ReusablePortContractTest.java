package com.jd.genie.platform.phase2contract;

import com.jd.genie.agent.agent.AgentContext;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2contract.dto.AgentCapabilitySummary;
import com.jd.genie.platform.phase2contract.dto.AgentRuntimeProfile;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.port.ToolBindingPort;
import com.jd.genie.platform.phase2contract.support.AgentRuntimeCatalogPortContractTest;
import com.jd.genie.platform.phase2contract.support.FakeAgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.support.FakeRuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.support.FakeToolBindingPort;
import com.jd.genie.platform.phase2contract.support.RuntimeToolCollectionPortContractTest;
import com.jd.genie.platform.phase2contract.support.ToolBindingPortContractTest;
import org.junit.jupiter.api.Nested;

import java.util.List;

class Phase2ReusablePortContractTest {

    private static final CurrentUser USER = new CurrentUser(
        "tenant-contract",
        "user-contract",
        "contract-user",
        "Contract User",
        UserRole.USER
    );

    @Nested
    class CatalogFakeConformance extends AgentRuntimeCatalogPortContractTest {
        private final FakeAgentRuntimeCatalogPort fake = new FakeAgentRuntimeCatalogPort();

        @Override
        protected AgentRuntimeCatalogPort port() {
            return fake;
        }

        @Override
        protected CurrentUser currentUser() {
            return USER;
        }

        @Override
        protected String onlineAgentId() {
            return "agent-contract";
        }

        @Override
        protected void resetContractFixture() {
            fake.reset();
            fake.registerSummary(new AgentCapabilitySummary(
                onlineAgentId(), 1L, "Contract Agent", "Contract fixture"
            ));
            fake.registerProfile(profileFixture());
        }
    }

    @Nested
    class BindingFakeConformance extends ToolBindingPortContractTest {
        private final FakeToolBindingPort fake = new FakeToolBindingPort();

        @Override
        protected ToolBindingPort port() {
            return fake;
        }

        @Override
        protected CurrentUser currentUser() {
            return USER;
        }

        @Override
        protected String agentId() {
            return "agent-contract";
        }

        @Override
        protected String skillId() {
            return "skill-contract";
        }

        @Override
        protected void resetContractFixture() {
            fake.reset();
        }
    }

    @Nested
    class RuntimeCollectionFakeConformance extends RuntimeToolCollectionPortContractTest {
        private final FakeRuntimeToolCollectionPort fake = new FakeRuntimeToolCollectionPort();

        @Override
        protected RuntimeToolCollectionPort port() {
            return fake;
        }

        @Override
        protected CurrentUser currentUser() {
            return USER;
        }

        @Override
        protected AgentRuntimeProfile profile() {
            return profileFixture();
        }

        @Override
        protected AgentContext context() {
            return AgentContext.builder().requestId("request-contract").build();
        }

        @Override
        protected void resetContractFixture() {
            fake.reset();
        }

        @org.junit.jupiter.api.Test
        void nullAdditionalToolsIsValidationErrorOnFake() {
            var error = org.junit.jupiter.api.Assertions.assertThrows(
                com.jd.genie.platform.phase2contract.error.Phase2ContractException.class,
                () -> fake.build(
                    USER,
                    profileFixture(),
                    AgentContext.builder().requestId("request-contract").build(),
                    null
                )
            );
            org.junit.jupiter.api.Assertions.assertEquals(
                com.jd.genie.platform.contract.MvpErrorCode.VALIDATION_ERROR,
                error.errorCode()
            );
        }
    }

    private static AgentRuntimeProfile profileFixture() {
        return new AgentRuntimeProfile(
            "agent-contract",
            1L,
            "Contract Agent",
            "Contract fixture",
            "{{basePrompt}}\n{{query}}",
            "gpt-4o-mini",
            List.of(),
            List.of()
        );
    }
}

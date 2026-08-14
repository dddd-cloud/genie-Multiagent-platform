package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.phase2.runtime.agent.AgentTaskResultParser;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredAgentExecutor;
import com.jd.genie.platform.phase2.runtime.agent.ConfiguredReactAgentFactory;
import com.jd.genie.platform.phase2.runtime.event.OrchestrationEventMapper;
import com.jd.genie.platform.phase2.runtime.plan.OrchestrationPlanValidator;
import com.jd.genie.platform.phase2contract.port.AgentRuntimeCatalogPort;
import com.jd.genie.platform.phase2contract.port.RuntimeToolCollectionPort;
import com.jd.genie.platform.phase2contract.port.SkillRuntimePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires Phase2 C orchestration runtime for production use by GptProcessServiceImpl.
 */
@Configuration
public class Phase2OrchestrationConfiguration {

    @Bean
    public OrchestrationModelPort orchestrationModelPort(GenieConfig genieConfig) {
        return new OpenAiOrchestrationModelPort(genieConfig);
    }

    @Bean
    public OrchestrationPlanValidator orchestrationPlanValidator() {
        return new OrchestrationPlanValidator();
    }

    @Bean
    public OrchestrationEventMapper orchestrationEventMapper() {
        return new OrchestrationEventMapper();
    }

    @Bean
    public ConfiguredAgentExecutor configuredAgentExecutor() {
        return new ConfiguredAgentExecutor(new ConfiguredReactAgentFactory(), new AgentTaskResultParser());
    }

    @Bean
    public SerialOrchestrationService serialOrchestrationService(
            AgentRuntimeCatalogPort catalogPort,
            RuntimeToolCollectionPort toolCollectionPort,
            SkillRuntimePort skillRuntimePort,
            ConfiguredAgentExecutor executor,
            OrchestrationModelPort modelPort,
            GenieConfig genieConfig,
            @Value("${GENIE_ORCHESTRATION_MAX_AGENT_STEPS:10}") int maxAgentSteps
    ) {
        int capped = Math.max(1, Math.min(maxAgentSteps, 20));
        Integer reactMax = genieConfig.getReactMaxSteps();
        if (reactMax != null && reactMax > 0) {
            capped = Math.max(1, Math.min(Math.min(capped, reactMax), 20));
        }
        return new SerialOrchestrationService(catalogPort, toolCollectionPort, skillRuntimePort, executor, capped, modelPort);
    }

    @Bean
    public Phase2OrchestrationRuntime phase2OrchestrationRuntime(
            OrchestrationModelPort modelPort,
            OrchestrationPlanValidator planValidator,
            SerialOrchestrationService serialService,
            OrchestrationEventMapper eventMapper
    ) {
        return new Phase2OrchestrationRuntime(modelPort, planValidator, serialService, eventMapper);
    }
}

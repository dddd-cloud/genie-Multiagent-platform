package com.jd.genie.platform.phase2.runtime.plan;

import com.jd.genie.platform.agentbridge.AgentBridgeException;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.enums.StepMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrchestrationPlanParserTest {
    private final OrchestrationPlanParser parser = new OrchestrationPlanParser();

    @Test
    void parsesTheFixedThreeModePlanShape() {
        OrchestrationPlan plan = parser.parse("""
                {"steps":[
                  {"stepId":"main","mode":"MAIN_ONLY","objective":"Prepare scope","inputRefs":[],"agentId":null,"subTasks":[]},
                  {"stepId":"specialist","mode":"SINGLE_AGENT","objective":"Gather facts","inputRefs":["main"],"agentId":"research","subTasks":[]},
                  {"stepId":"parallel","mode":"PARALLEL_AGENTS","objective":"Compare findings","inputRefs":["specialist"],"agentId":null,"subTasks":[
                    {"subTaskId":"comparison-a","agentId":"research","objective":"Evaluate source A"},
                    {"subTaskId":"comparison-b","agentId":"writer","objective":"Evaluate source B"}
                  ]}
                ]}
                """);

        assertEquals(3, plan.steps().size());
        assertEquals(StepMode.MAIN_ONLY, plan.steps().get(0).mode());
        assertEquals(StepMode.SINGLE_AGENT, plan.steps().get(1).mode());
        assertEquals(StepMode.PARALLEL_AGENTS, plan.steps().get(2).mode());
        assertEquals(2, plan.steps().get(2).subTasks().size());
    }

    @Test
    void rejectsMarkdownAndTextOutsideTheSingleJsonObject() {
        assertPlanInvalid("""
                ```json
                {"steps":[]}
                ```
                """);
        assertPlanInvalid("prefix {\"steps\":[]} suffix");
        assertPlanInvalid("{\"steps\":[]} trailing");
    }

    @Test
    void rejectsUnknownDuplicateAndMalformedFields() {
        assertPlanInvalid("{\"steps\":[],\"unexpected\":true}");
        assertPlanInvalid("{\"steps\":[],\"steps\":[]}");
        assertPlanInvalid("""
                {"steps":[
                  {"stepId":"step-1","mode":"UNKNOWN","objective":"Do work","inputRefs":[],"agentId":null,"subTasks":[]}
                ]}
                """);
        assertPlanInvalid("""
                {"steps":[
                  {"stepId":"step-1","mode":"MAIN_ONLY","objective":"Do work","inputRefs":[],"agentId":null}
                ]}
                """);
    }

    private void assertPlanInvalid(String value) {
        AgentBridgeException error = assertThrows(AgentBridgeException.class, () -> parser.parse(value));
        assertEquals(MvpErrorCode.ORCHESTRATION_PLAN_INVALID, error.getErrorCode());
    }
}

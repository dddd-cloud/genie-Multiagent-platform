package com.jd.genie.platform.phase2.runtime.orchestration;

/**
 * One specialist step's evidence for the final user-facing synthesis.
 */
public record SummaryEvidence(
        String stepId,
        String agentName,
        String objective,
        String output,
        String errorCode
) {
    public boolean failed() {
        return errorCode != null && !errorCode.isBlank();
    }

    public String displayName() {
        if (agentName != null && !agentName.isBlank()) {
            return agentName;
        }
        return stepId == null || stepId.isBlank() ? "专家" : stepId;
    }
}

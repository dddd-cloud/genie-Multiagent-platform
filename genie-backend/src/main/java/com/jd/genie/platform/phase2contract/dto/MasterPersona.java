package com.jd.genie.platform.phase2contract.dto;

/**
 * Persona overlay for the orchestration master role.
 * When a team is selected, the team's master Agent contributes its compiled prompt and model here;
 * the platform's hardcoded planner/summarizer rules always stay in effect on top of it.
 */
public record MasterPersona(
    String agentId,
    String agentName,
    String personaPrompt,
    String modelName
) {
    private static final MasterPersona NONE = new MasterPersona(null, null, "", null);

    public static MasterPersona none() {
        return NONE;
    }

    public boolean present() {
        return personaPrompt != null && !personaPrompt.isBlank();
    }

    public String displayName() {
        return agentName == null || agentName.isBlank() ? "主 Agent" : agentName;
    }
}

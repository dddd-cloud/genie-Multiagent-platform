package com.jd.genie.platform.phase2.runtime.route;

/**
 * System-master handoff for AUTO: either one specialist or one team.
 * After this decision the platform master steps down.
 */
public record DispatchDecision(Kind kind, String targetId, String targetName, String reasonCode) {

    public enum Kind {
        AGENT,
        TEAM
    }

    public static DispatchDecision agent(String agentId, String agentName, String reasonCode) {
        return new DispatchDecision(Kind.AGENT, agentId, agentName, reasonCode);
    }

    public static DispatchDecision team(String teamId, String teamName, String reasonCode) {
        return new DispatchDecision(Kind.TEAM, teamId, teamName, reasonCode);
    }
}

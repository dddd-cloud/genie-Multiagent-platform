package com.jd.genie.platform.phase2.runtime.route;

public record RouteDecision(Route route, String reasonCode) {

    public enum Route {
        DIRECT,
        ORCHESTRATED
    }
}

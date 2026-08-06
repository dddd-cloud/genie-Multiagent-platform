package com.jd.genie.service;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface IGptProcessService {

    /**
     * Starts one Agent execution from an untrusted browser request.
     * Trusted user, trace ID and history are always resolved by the service.
     */
    SseEmitter queryMultiAgentIncrStream(GptQueryReq req);

    SseEmitter queryPhase2AgentStreamIncr(Phase2GptQueryRequest req);
}

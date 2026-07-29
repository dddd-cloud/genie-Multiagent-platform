package com.jd.genie.service;

import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.agentbridge.CancellableAgentCall;
import com.jd.genie.platform.agentbridge.ConversationStreamObserver;

public interface IMultiAgentService {
    /**
     * Starts the internal Agent stream and delegates every event and terminal signal
     * to the execution observer.
     */
    void searchForAgentRequest(
            GptQueryReq request,
            ConversationStreamObserver observer,
            CancellableAgentCall cancellableCall
    );
}

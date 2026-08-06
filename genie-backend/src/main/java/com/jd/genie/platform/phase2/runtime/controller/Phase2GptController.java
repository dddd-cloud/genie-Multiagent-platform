package com.jd.genie.platform.phase2.runtime.controller;

import com.jd.genie.platform.phase2.runtime.request.Phase2GptQueryRequest;
import com.jd.genie.service.IGptProcessService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/web/api/v2/gpt")
public class Phase2GptController {
    private final IGptProcessService gptProcessService;

    public Phase2GptController(IGptProcessService gptProcessService) {
        this.gptProcessService = gptProcessService;
    }

    @PostMapping(value = "/queryAgentStreamIncr", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryAgentStreamIncr(@RequestBody Phase2GptQueryRequest request) {
        return gptProcessService.queryPhase2AgentStreamIncr(request);
    }
}

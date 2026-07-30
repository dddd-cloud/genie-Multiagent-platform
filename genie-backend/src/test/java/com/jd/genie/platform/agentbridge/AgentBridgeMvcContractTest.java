package com.jd.genie.platform.agentbridge;

import com.jd.genie.controller.GenieController;
import com.jd.genie.model.req.GptQueryReq;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.MvpErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AgentBridgeMvcContractTest {

    @Test
    void streamEndpointOnlyDeclaresTheFrozenPostSseMapping() throws Exception {
        Method endpoint = GenieController.class.getMethod("queryAgentStreamIncr", GptQueryReq.class);

        PostMapping mapping = endpoint.getAnnotation(PostMapping.class);

        assertNotNull(mapping);
        assertEquals("/web/api/v1/gpt/queryAgentStreamIncr", mapping.value()[0]);
        assertEquals(MediaType.TEXT_EVENT_STREAM_VALUE, mapping.produces()[0]);
        assertNull(endpoint.getAnnotation(RequestMapping.class));
    }

    @Test
    void nonPostStreamMethodsAreRejectedWithMethodNotAllowed() throws Exception {
        GenieController controller = new GenieController();
        ReflectionTestUtils.setField(
                controller,
                "gptProcessService",
                mock(com.jd.genie.service.IGptProcessService.class)
        );
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller).build();

        mvc.perform(get("/web/api/v1/gpt/queryAgentStreamIncr"))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/web/api/v1/gpt/queryAgentStreamIncr"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void bridgeAdvicePreservesMappedFrozenCodesAndHttpStatuses() {
        AgentBridgeExceptionHandler handler = new AgentBridgeExceptionHandler();
        Map<MvpErrorCode, HttpStatus> mappings = Map.of(
                MvpErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST,
                MvpErrorCode.AUTH_REQUIRED, HttpStatus.UNAUTHORIZED,
                MvpErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                MvpErrorCode.CONVERSATION_BUSY, HttpStatus.CONFLICT,
                MvpErrorCode.DUPLICATE_REQUEST, HttpStatus.CONFLICT,
                MvpErrorCode.MESSAGE_STATE_CONFLICT, HttpStatus.CONFLICT,
                MvpErrorCode.SNAPSHOT_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE,
                MvpErrorCode.DATABASE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE
        );

        for (Map.Entry<MvpErrorCode, HttpStatus> mapping : mappings.entrySet()) {
            ResponseEntity<ApiResponse<Void>> response = handler.handle(
                    new AgentBridgeException(mapping.getKey(), "frozen error")
            );

            assertEquals(mapping.getValue(), response.getStatusCode());
            assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
            assertEquals(mapping.getKey().name(), response.getBody().code());
        }
    }

    @Test
    void bridgeAdviceMapsUnrecognizedBridgeErrorsToInternalError() {
        ResponseEntity<ApiResponse<Void>> response = new AgentBridgeExceptionHandler().handle(
                new AgentBridgeException(MvpErrorCode.AGENT_DOWNSTREAM_ERROR, "downstream failed")
        );

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(MvpErrorCode.INTERNAL_ERROR.name(), response.getBody().code());
    }
}

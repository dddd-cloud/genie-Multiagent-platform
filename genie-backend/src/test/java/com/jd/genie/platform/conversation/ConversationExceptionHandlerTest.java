package com.jd.genie.platform.conversation;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.exception.ConversationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest(classes = ConversationExceptionHandlerTest.TestConfig.class)
class ConversationExceptionHandlerTest {

    @jakarta.annotation.Resource
    private MockMvc mockMvc;

    @Test
    void adviceMapsConversationExceptionThrownOutsideConversationController() throws Exception {
        mockMvc.perform(get("/conversation-advice-test/conflict"))
            .andExpect(status().isConflict())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("MESSAGE_STATE_CONFLICT"));
    }

    @Test
    void adviceMapsDataAccessExceptionWithoutLeakingDatabaseDetails() throws Exception {
        mockMvc.perform(get("/conversation-advice-test/data-access"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("Database unavailable"))
            .andExpect(content().string(not(containsString("SELECT secret FROM table"))))
            .andExpect(content().string(not(containsString("conversation_message"))));
    }

    @Configuration
    @Import({ConversationExceptionHandler.class, ThrowingController.class})
    @ImportAutoConfiguration({
        DispatcherServletAutoConfiguration.class,
        WebMvcAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        JacksonAutoConfiguration.class,
        MybatisPlusAutoConfiguration.class
    })
    static class TestConfig {
    }

    @RestController
    static class ThrowingController {
        @GetMapping("/conversation-advice-test/conflict")
        void conflict() {
            throw new ConversationException(MvpErrorCode.MESSAGE_STATE_CONFLICT, "Message state conflict");
        }

        @GetMapping("/conversation-advice-test/data-access")
        void dataAccess() {
            throw new TransientDataAccessResourceException("SELECT secret FROM table conversation_message");
        }
    }
}
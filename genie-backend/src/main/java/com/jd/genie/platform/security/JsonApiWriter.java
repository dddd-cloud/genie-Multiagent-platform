package com.jd.genie.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

final class JsonApiWriter {
    private JsonApiWriter() { }
    static void write(ObjectMapper objectMapper, HttpServletResponse response, int status, String code, String message, Object data) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiResponse<>(code, message, data));
    }
}

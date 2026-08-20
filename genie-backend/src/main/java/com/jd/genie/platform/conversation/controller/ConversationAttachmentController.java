package com.jd.genie.platform.conversation.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.conversation.dto.ConversationAttachmentResponse;
import com.jd.genie.platform.conversation.service.ConversationAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/conversations/{conversationId}/attachments")
@RequiredArgsConstructor
public class ConversationAttachmentController {
    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "success";

    private final ConversationAttachmentService attachmentService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ConversationAttachmentResponse> upload(
        @PathVariable String conversationId,
        @RequestParam("file") MultipartFile file
    ) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return success(attachmentService.upload(user, conversationId, file));
    }

    @DeleteMapping("/{attachmentId}")
    public ApiResponse<Void> delete(
        @PathVariable String conversationId,
        @PathVariable String attachmentId
    ) {
        CurrentUser user = currentUserProvider.requireCurrentUser();
        attachmentService.delete(user, conversationId, attachmentId);
        return success(null);
    }

    private static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }
}

package com.jd.genie.platform.conversation.controller;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.conversation.dto.ConversationCreateRequest;
import com.jd.genie.platform.conversation.dto.ConversationListItemResponse;
import com.jd.genie.platform.conversation.dto.ConversationMessageResponse;
import com.jd.genie.platform.conversation.dto.ConversationResponse;
import com.jd.genie.platform.conversation.dto.ConversationUpdateRequest;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private static final String SUCCESS_CODE = "OK";
    private static final String SUCCESS_MESSAGE = "success";

    private final ConversationService conversationService;
    private final ObjectProvider<CurrentUserProvider> currentUserProvider;

    @PostMapping
    public ApiResponse<ConversationResponse> create(@RequestBody(required = false) ConversationCreateRequest request) {
        CurrentUser user = currentUser();
        return success(conversationService.createConversation(user, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<ConversationListItemResponse>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        CurrentUser user = currentUser();
        return success(conversationService.listConversations(user, page, pageSize));
    }

    @GetMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> detail(@PathVariable String conversationId) {
        CurrentUser user = currentUser();
        return success(conversationService.getConversation(user, conversationId));
    }

    @GetMapping("/{conversationId}/messages")
    public ApiResponse<List<ConversationMessageResponse>> messages(@PathVariable String conversationId) {
        CurrentUser user = currentUser();
        return success(conversationService.listMessages(user, conversationId));
    }

    @PatchMapping("/{conversationId}")
    public ApiResponse<ConversationResponse> rename(@PathVariable String conversationId,
                                                    @RequestBody ConversationUpdateRequest request) {
        CurrentUser user = currentUser();
        return success(conversationService.renameConversation(user, conversationId, request));
    }

    @DeleteMapping("/{conversationId}")
    public ApiResponse<Void> delete(@PathVariable String conversationId) {
        CurrentUser user = currentUser();
        conversationService.deleteConversation(user, conversationId);
        return success(null);
    }

    @ExceptionHandler(ConversationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConversationException(ConversationException exception) {
        return error(status(exception.code()), exception.code(), exception.getMessage());
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, MvpErrorCode.VALIDATION_ERROR, "请求参数不合法");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataAccess(DataAccessException exception) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, MvpErrorCode.DATABASE_UNAVAILABLE, "数据库暂不可用");
    }

    private CurrentUser currentUser() {
        CurrentUserProvider provider = currentUserProvider.getIfAvailable();
        if (provider == null) {
            throw new ConversationException(MvpErrorCode.AUTH_REQUIRED, "请先登录");
        }
        return provider.requireCurrentUser();
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(SUCCESS_CODE, SUCCESS_MESSAGE, data);
    }

    private ResponseEntity<ApiResponse<Void>> error(HttpStatus status, MvpErrorCode code, String message) {
        return ResponseEntity.status(status).body(new ApiResponse<>(code.name(), message, null));
    }

    private HttpStatus status(MvpErrorCode code) {
        return switch (code) {
            case VALIDATION_ERROR -> HttpStatus.BAD_REQUEST;
            case AUTH_REQUIRED -> HttpStatus.UNAUTHORIZED;
            case RESOURCE_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONVERSATION_BUSY -> HttpStatus.CONFLICT;
            case DATABASE_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}

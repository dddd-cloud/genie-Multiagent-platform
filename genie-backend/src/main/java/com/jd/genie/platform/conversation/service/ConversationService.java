package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.contract.ConversationMessageStatus;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.conversation.dto.ConversationCreateRequest;
import com.jd.genie.platform.conversation.dto.ConversationListItemResponse;
import com.jd.genie.platform.conversation.dto.ConversationMessagePreviewRow;
import com.jd.genie.platform.conversation.dto.ConversationMessageResponse;
import com.jd.genie.platform.conversation.dto.ConversationResponse;
import com.jd.genie.platform.conversation.dto.ConversationUpdateRequest;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.entity.ConversationMessageEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private static final String DEFAULT_TITLE = "新对话";
    private static final int MAX_TITLE_CODE_POINTS = 200;
    private static final int MAX_PREVIEW_CODE_POINTS = 80;

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ConversationResponse createConversation(CurrentUser user, ConversationCreateRequest request) {
        Instant now = Instant.now(clock);
        ConversationEntity entity = new ConversationEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTenantId(user.tenantId());
        entity.setOwnerId(user.userId());
        entity.setTitle(normalizeOptionalTitle(request == null ? null : request.title()));
        entity.setNextTurnNo(1L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        conversationMapper.insert(entity);
        return toConversationResponse(entity);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationListItemResponse> listConversations(CurrentUser user, Integer page, Integer pageSize) {
        int validPage = page == null ? 1 : page;
        int validPageSize = pageSize == null ? 20 : pageSize;
        validatePage(validPage, validPageSize);

        int limit = validPageSize + 1;
        int offset = (validPage - 1) * validPageSize;
        List<ConversationEntity> rows = conversationMapper.selectOwnedConversationPage(
            user.tenantId(), user.userId(), limit, offset);
        boolean hasMore = rows.size() > validPageSize;
        List<ConversationEntity> pageRows = hasMore ? rows.subList(0, validPageSize) : rows;
        Map<String, String> previews = loadLatestUserPreviews(user, pageRows);

        List<ConversationListItemResponse> items = pageRows.stream()
            .map(row -> new ConversationListItemResponse(
                row.getId(),
                row.getTitle(),
                row.getLastMessageAt(),
                row.getCreatedAt(),
                row.getUpdatedAt(),
                preview(previews.get(row.getId()))
            ))
            .toList();
        return new PageResponse<>(items, validPage, validPageSize, hasMore);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(CurrentUser user, String conversationId) {
        return toConversationResponse(requireOwnedConversation(user, conversationId));
    }

    @Transactional(readOnly = true)
    public List<ConversationMessageResponse> listMessages(CurrentUser user, String conversationId) {
        requireOwnedConversation(user, conversationId);
        return conversationMessageMapper.selectRecentMessagesByOwnedConversation(
                user.tenantId(), user.userId(), conversationId)
            .stream()
            .map(this::toMessageResponse)
            .toList();
    }

    @Transactional
    public ConversationResponse renameConversation(CurrentUser user, String conversationId, ConversationUpdateRequest request) {
        String title = normalizeRequiredTitle(request == null ? null : request.title());
        Instant now = Instant.now(clock);
        int updated = conversationMapper.updateTitleOwned(user.tenantId(), user.userId(), conversationId, title, now);
        if (updated != 1) {
            throw resourceNotFound();
        }
        return getConversation(user, conversationId);
    }

    @Transactional
    public void deleteConversation(CurrentUser user, String conversationId) {
        ConversationEntity locked = conversationMapper.selectOwnedConversationForUpdate(
            user.tenantId(), user.userId(), conversationId);
        if (locked == null) {
            throw resourceNotFound();
        }
        if (conversationMessageMapper.existsActiveAssistant(user.tenantId(), user.userId(), conversationId)) {
            throw new ConversationException(MvpErrorCode.CONVERSATION_BUSY, "当前会话正在执行，请稍后再试");
        }
        int deleted = conversationMapper.softDeleteOwned(
            user.tenantId(), user.userId(), conversationId, Instant.now(clock));
        if (deleted != 1) {
            throw resourceNotFound();
        }
    }

    private ConversationEntity requireOwnedConversation(CurrentUser user, String conversationId) {
        validateId(conversationId);
        ConversationEntity entity = conversationMapper.selectOwnedConversation(
            user.tenantId(), user.userId(), conversationId);
        if (entity == null) {
            throw resourceNotFound();
        }
        return entity;
    }

    private Map<String, String> loadLatestUserPreviews(CurrentUser user, List<ConversationEntity> conversations) {
        if (conversations.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> ids = conversations.stream().map(ConversationEntity::getId).toList();
        Map<String, String> previews = new java.util.HashMap<>();
        for (ConversationMessagePreviewRow row : conversationMessageMapper.selectLatestUserPreviews(
            user.tenantId(), user.userId(), ids)) {
            previews.put(row.getConversationId(), row.getContent());
        }
        return previews;
    }

    private ConversationResponse toConversationResponse(ConversationEntity entity) {
        return new ConversationResponse(
            entity.getId(),
            entity.getTitle(),
            entity.getLastMessageAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private ConversationMessageResponse toMessageResponse(ConversationMessageEntity entity) {
        return new ConversationMessageResponse(
            entity.getId(),
            entity.getTurnNo(),
            entity.getRole(),
            entity.getStatus(),
            entity.getRequestId(),
            entity.getContent(),
            entity.getStreamSnapshot(),
            entity.getPayloadVersion(),
            entity.getDeepThink(),
            entity.getOutputStyle(),
            entity.getErrorCode(),
            entity.getErrorMessage(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }

    private String normalizeOptionalTitle(String rawTitle) {
        if (rawTitle == null) {
            return DEFAULT_TITLE;
        }
        String title = rawTitle.trim();
        if (title.isEmpty()) {
            return DEFAULT_TITLE;
        }
        validateTitleLength(title);
        return title;
    }

    private String normalizeRequiredTitle(String rawTitle) {
        if (rawTitle == null) {
            throw validationError();
        }
        String title = rawTitle.trim();
        if (title.isEmpty()) {
            throw validationError();
        }
        validateTitleLength(title);
        return title;
    }

    private void validateTitleLength(String title) {
        if (title.codePointCount(0, title.length()) > MAX_TITLE_CODE_POINTS) {
            throw validationError();
        }
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw validationError();
        }
    }

    private void validateId(String id) {
        if (id == null || id.isBlank()) {
            throw validationError();
        }
    }

    private String preview(String content) {
        if (content == null) {
            return null;
        }
        String normalized = content.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty()) {
            return "";
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= MAX_PREVIEW_CODE_POINTS) {
            return normalized;
        }
        return normalized.substring(0, normalized.offsetByCodePoints(0, MAX_PREVIEW_CODE_POINTS));
    }

    private ConversationException validationError() {
        return new ConversationException(MvpErrorCode.VALIDATION_ERROR, "请求参数不合法");
    }

    private ConversationException resourceNotFound() {
        return new ConversationException(MvpErrorCode.RESOURCE_NOT_FOUND, "资源不存在");
    }
}

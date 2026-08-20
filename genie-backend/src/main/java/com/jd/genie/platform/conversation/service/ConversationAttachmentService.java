package com.jd.genie.platform.conversation.service;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.attachment.ChatAttachmentExtractor;
import com.jd.genie.platform.conversation.attachment.ChatAttachmentLimits;
import com.jd.genie.platform.conversation.attachment.ChatAttachmentPrompt;
import com.jd.genie.platform.conversation.dto.ConversationAttachmentResponse;
import com.jd.genie.platform.conversation.entity.ConversationAttachmentEntity;
import com.jd.genie.platform.conversation.entity.ConversationEntity;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.mapper.ConversationAttachmentMapper;
import com.jd.genie.platform.conversation.mapper.ConversationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationAttachmentService {
    private final ConversationMapper conversationMapper;
    private final ConversationAttachmentMapper attachmentMapper;
    private final Clock clock = Clock.systemUTC();

    @Transactional
    public ConversationAttachmentResponse upload(CurrentUser user, String conversationId, MultipartFile file) {
        requireOwnedConversation(user, conversationId);
        if (file == null || file.isEmpty()) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "file is required");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        if (originalName.isBlank() || originalName.length() > ChatAttachmentLimits.MAX_FILE_NAME_LENGTH) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "invalid file name");
        }
        if (originalName.contains("/") || originalName.contains("\\") || originalName.contains("..")) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "invalid file name");
        }
        if (file.getSize() > ChatAttachmentLimits.MAX_FILE_BYTES) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "file is too large");
        }
        String fileType = ChatAttachmentExtractor.fileTypeOf(originalName);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "failed to read upload", exception);
        }
        if (bytes.length == 0 || bytes.length > ChatAttachmentLimits.MAX_FILE_BYTES) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "file is too large");
        }
        ChatAttachmentExtractor.ExtractedText extracted;
        try {
            extracted = ChatAttachmentExtractor.extract(fileType, bytes);
        } catch (ConversationException exception) {
            throw exception;
        } catch (RuntimeException | Error exception) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "failed to read file", exception);
        }
        Instant now = Instant.now(clock);
        ConversationAttachmentEntity entity = new ConversationAttachmentEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setConversationId(conversationId);
        entity.setTenantId(user.tenantId());
        entity.setOwnerId(user.userId());
        entity.setFileName(originalName);
        entity.setFileType(fileType);
        entity.setMimeType(file.getContentType() == null || file.getContentType().isBlank()
            ? "application/octet-stream"
            : file.getContentType());
        entity.setSizeBytes((long) bytes.length);
        entity.setExtractedText(extracted.text());
        entity.setTruncated(extracted.truncated());
        entity.setCreatedAt(now);
        attachmentMapper.insert(entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(CurrentUser user, String conversationId, String attachmentId) {
        requireOwnedConversation(user, conversationId);
        ConversationAttachmentEntity existing = attachmentMapper.selectOwned(
            user.tenantId(), user.userId(), conversationId, attachmentId);
        if (existing == null) {
            throw new ConversationException(MvpErrorCode.RESOURCE_NOT_FOUND, "attachment not found");
        }
        attachmentMapper.deleteById(existing.getId());
    }

    @Transactional(readOnly = true)
    public String enrichQuery(CurrentUser user, String conversationId, List<String> attachmentIds, String query) {
        return preparePrompts(user, conversationId, attachmentIds, query).specialistQuery();
    }

    @Transactional(readOnly = true)
    public ChatAttachmentPrompt.Prompts preparePrompts(
            CurrentUser user,
            String conversationId,
            List<String> attachmentIds,
            String query
    ) {
        List<ConversationAttachmentEntity> attachments = loadOwnedInOrder(user, conversationId, attachmentIds);
        return ChatAttachmentPrompt.prompts(query, attachments);
    }

    private List<ConversationAttachmentEntity> loadOwnedInOrder(
        CurrentUser user,
        String conversationId,
        List<String> attachmentIds
    ) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return List.of();
        }
        if (attachmentIds.size() > ChatAttachmentLimits.MAX_FILES) {
            throw new ConversationException(MvpErrorCode.VALIDATION_ERROR, "too many attachments");
        }
        requireOwnedConversation(user, conversationId);
        List<ConversationAttachmentEntity> rows = attachmentMapper.selectOwnedByIds(
            user.tenantId(), user.userId(), conversationId, attachmentIds);
        Map<String, ConversationAttachmentEntity> byId = new LinkedHashMap<>();
        for (ConversationAttachmentEntity row : rows) {
            byId.put(row.getId(), row);
        }
        List<ConversationAttachmentEntity> ordered = new ArrayList<>();
        for (String id : attachmentIds) {
            ConversationAttachmentEntity row = byId.get(id);
            if (row == null) {
                throw new ConversationException(MvpErrorCode.RESOURCE_NOT_FOUND, "attachment not found");
            }
            ordered.add(row);
        }
        return ordered;
    }

    private ConversationEntity requireOwnedConversation(CurrentUser user, String conversationId) {
        ConversationEntity conversation = conversationMapper.selectOwnedConversation(
            user.tenantId(), user.userId(), conversationId);
        if (conversation == null) {
            throw new ConversationException(MvpErrorCode.RESOURCE_NOT_FOUND, "conversation not found");
        }
        return conversation;
    }

    private ConversationAttachmentResponse toResponse(ConversationAttachmentEntity entity) {
        String text = entity.getExtractedText() == null ? "" : entity.getExtractedText();
        return new ConversationAttachmentResponse(
            entity.getId(),
            entity.getFileName(),
            entity.getFileType(),
            entity.getSizeBytes() == null ? 0L : entity.getSizeBytes(),
            text.codePointCount(0, text.length()),
            Boolean.TRUE.equals(entity.getTruncated())
        );
    }
}

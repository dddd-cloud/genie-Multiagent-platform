package com.jd.genie.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.config.GenieConfig;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.conversation.exception.ConversationException;
import com.jd.genie.platform.conversation.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WorkspaceFileProxyService {
    static final String INTERNAL_FILE_TOKEN_HEADER = "X-Genie-Internal-File-Token";

    private final GenieConfig genieConfig;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final Environment environment;
    private final OkHttpClient httpClient;

    @Autowired
    public WorkspaceFileProxyService(
        GenieConfig genieConfig,
        ConversationService conversationService,
        ObjectMapper objectMapper,
        Environment environment
    ) {
        this(
            genieConfig,
            conversationService,
            objectMapper,
            environment,
            new OkHttpClient.Builder()
                .connectTimeout(60, TimeUnit.SECONDS)
                .readTimeout(300, TimeUnit.SECONDS)
                .writeTimeout(300, TimeUnit.SECONDS)
                .callTimeout(300, TimeUnit.SECONDS)
                .build()
        );
    }

    WorkspaceFileProxyService(
        GenieConfig genieConfig,
        ConversationService conversationService,
        ObjectMapper objectMapper,
        Environment environment,
        OkHttpClient httpClient
    ) {
        this.genieConfig = genieConfig;
        this.conversationService = conversationService;
        this.objectMapper = objectMapper;
        this.environment = environment;
        this.httpClient = httpClient;
    }

    public JsonNode listFiles(CurrentUser user, String conversationId, int page, int pageSize) {
        String requestId = authorizeAndRequestId(user, conversationId);
        HttpUrl url = filesUrl(conversationId)
            .newBuilder()
            .addQueryParameter("page", String.valueOf(Math.max(page, 1)))
            .addQueryParameter("pageSize", String.valueOf(Math.min(Math.max(pageSize, 1), 200)))
            .addQueryParameter("requestId", requestId)
            .build();
        return executeJson(new Request.Builder().url(url).get());
    }

    public JsonNode uploadFile(CurrentUser user, String conversationId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new WorkspaceException(MvpErrorCode.VALIDATION_ERROR, "file is required");
        }
        String requestId = authorizeAndRequestId(user, conversationId);
        String fileName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "failed to read upload", exception);
        }
        String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        MultipartBody body = new MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                fileName,
                RequestBody.create(bytes, MediaType.parse(contentType))
            )
            .addFormDataPart("requestId", requestId)
            .build();
        return executeJson(new Request.Builder().url(filesUrl(conversationId)).post(body));
    }

    public ResponseEntity<byte[]> download(CurrentUser user, String conversationId, String fileName) {
        return binary(user, conversationId, fileName, "download");
    }

    public ResponseEntity<byte[]> preview(CurrentUser user, String conversationId, String fileName) {
        return binary(user, conversationId, fileName, "preview");
    }

    private ResponseEntity<byte[]> binary(
        CurrentUser user,
        String conversationId,
        String fileName,
        String action
    ) {
        String requestId = authorizeAndRequestId(user, conversationId);
        HttpUrl url = filesUrl(conversationId)
            .newBuilder()
            .addPathSegment(fileName)
            .addPathSegment(action)
            .addQueryParameter("requestId", requestId)
            .build();
        Request request = applyToken(new Request.Builder().url(url).get()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                throw new WorkspaceException(MvpErrorCode.RESOURCE_NOT_FOUND, "file not found");
            }
            assertToolSuccess(response);
            ResponseBody body = response.body();
            byte[] bytes = body == null ? new byte[0] : body.bytes();
            HttpHeaders headers = new HttpHeaders();
            String contentType = response.header("Content-Type");
            if (contentType != null && !contentType.isBlank()) {
                headers.add(HttpHeaders.CONTENT_TYPE, contentType);
            }
            String disposition = response.header("Content-Disposition");
            if (disposition != null && !disposition.isBlank()) {
                headers.add(HttpHeaders.CONTENT_DISPOSITION, disposition);
            }
            return new ResponseEntity<>(bytes, headers, org.springframework.http.HttpStatus.OK);
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service unavailable", exception);
        }
    }

    private String authorizeAndRequestId(CurrentUser user, String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            throw new WorkspaceException(MvpErrorCode.VALIDATION_ERROR, "conversationId is required");
        }
        try {
            conversationService.getConversation(user, conversationId);
        } catch (ConversationException exception) {
            MvpErrorCode code = exception.code() == null ? MvpErrorCode.INTERNAL_ERROR : exception.code();
            throw new WorkspaceException(code, exception.getMessage(), exception);
        }
        return WorkspaceRequestIds.forConversation(user.tenantId(), user.userId(), conversationId);
    }

    private HttpUrl filesUrl(String conversationId) {
        String base = genieConfig.getCodeInterpreterUrl();
        if (base == null || base.isBlank()) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service is not configured");
        }
        HttpUrl parsed = HttpUrl.parse(base.endsWith("/") ? base.substring(0, base.length() - 1) : base);
        if (parsed == null) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service is not configured");
        }
        return parsed.newBuilder()
            .addPathSegments("v1/workspaces")
            .addPathSegment(conversationId)
            .addPathSegment("files")
            .build();
    }

    private JsonNode executeJson(Request.Builder builder) {
        Request request = applyToken(builder).build();
        try (Response response = httpClient.newCall(request).execute()) {
            assertToolSuccess(response);
            ResponseBody body = response.body();
            String raw = body == null ? "{}" : body.string();
            if (raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (WorkspaceException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service unavailable", exception);
        }
    }

    private Request.Builder applyToken(Request.Builder builder) {
        String token = firstNonBlank(
            environment.getProperty("GENIE_INTERNAL_FILE_TOKEN"),
            environment.getProperty("GENIE_INTERNAL_AGENT_TOKEN")
        );
        if (token != null) {
            builder.header(INTERNAL_FILE_TOKEN_HEADER, token);
        }
        return builder;
    }

    private void assertToolSuccess(Response response) {
        int code = response.code();
        if (code >= 200 && code < 300) {
            return;
        }
        if (code == 400) {
            throw new WorkspaceException(MvpErrorCode.VALIDATION_ERROR, toolMessage(response, "invalid workspace file request"));
        }
        if (code == 401 || code == 403) {
            throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service rejected the call");
        }
        if (code == 404) {
            throw new WorkspaceException(MvpErrorCode.RESOURCE_NOT_FOUND, "file not found");
        }
        if (code == 413) {
            throw new WorkspaceException(MvpErrorCode.SNAPSHOT_TOO_LARGE, "file exceeds the size limit");
        }
        throw new WorkspaceException(MvpErrorCode.INTERNAL_ERROR, "workspace file service unavailable");
    }

    private String toolMessage(Response response, String fallback) {
        try {
            ResponseBody body = response.body();
            if (body == null) {
                return fallback;
            }
            String raw = body.string();
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            JsonNode node = objectMapper.readTree(raw);
            if (node.hasNonNull("detail")) {
                return node.get("detail").asText(fallback);
            }
            if (node.hasNonNull("message")) {
                return node.get("message").asText(fallback);
            }
            return fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}

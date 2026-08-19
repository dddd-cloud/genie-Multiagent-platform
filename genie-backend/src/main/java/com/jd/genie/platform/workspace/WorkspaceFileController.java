package com.jd.genie.platform.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v2/workspaces")
public class WorkspaceFileController {
    private final CurrentUserProvider currentUserProvider;
    private final WorkspaceFileProxyService workspaceFiles;

    public WorkspaceFileController(
        CurrentUserProvider currentUserProvider,
        WorkspaceFileProxyService workspaceFiles
    ) {
        this.currentUserProvider = currentUserProvider;
        this.workspaceFiles = workspaceFiles;
    }

    @GetMapping("/{conversationId}/files")
    public ApiResponse<JsonNode> list(
        @PathVariable String conversationId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "200") int pageSize
    ) {
        return ok(workspaceFiles.listFiles(currentUserProvider.requireCurrentUser(), conversationId, page, pageSize));
    }

    @PostMapping("/{conversationId}/files")
    public ApiResponse<JsonNode> upload(
        @PathVariable String conversationId,
        @RequestParam("file") MultipartFile file
    ) {
        return ok(workspaceFiles.uploadFile(currentUserProvider.requireCurrentUser(), conversationId, file));
    }

    @GetMapping("/{conversationId}/files/{fileName}/download")
    public ResponseEntity<byte[]> download(
        @PathVariable String conversationId,
        @PathVariable String fileName
    ) {
        return workspaceFiles.download(currentUserProvider.requireCurrentUser(), conversationId, fileName);
    }

    @GetMapping("/{conversationId}/files/{fileName}/preview")
    public ResponseEntity<byte[]> preview(
        @PathVariable String conversationId,
        @PathVariable String fileName
    ) {
        return workspaceFiles.preview(currentUserProvider.requireCurrentUser(), conversationId, fileName);
    }

    private static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("OK", "success", data);
    }
}

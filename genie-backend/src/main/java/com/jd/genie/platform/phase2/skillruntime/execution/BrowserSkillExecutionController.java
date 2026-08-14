package com.jd.genie.platform.phase2.skillruntime.execution;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.phase2contract.dto.BrowserSkillExecutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/skill-executions")
@RequiredArgsConstructor
public class BrowserSkillExecutionController {
    private final CurrentUserProvider users;
    private final BrowserSkillExecutionCoordinator coordinator;
    private final BrowserSkillExecutionBundleService bundles;

    @GetMapping("/{executionId}/bundle")
    public ResponseEntity<byte[]> bundle(@PathVariable String executionId) {
        byte[] body = bundles.build(coordinator.lookupOwned(users.requireCurrentUser(), executionId));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/zip"))
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("skill-execution.zip").build().toString())
            .body(body);
    }
    @PostMapping("/{executionId}/result")
    public ApiResponse<Void> result(@PathVariable String executionId, @RequestBody BrowserSkillExecutionResult result) {
        coordinator.complete(users.requireCurrentUser(), executionId, result);
        return new ApiResponse<>("OK", "success", null);
    }
}

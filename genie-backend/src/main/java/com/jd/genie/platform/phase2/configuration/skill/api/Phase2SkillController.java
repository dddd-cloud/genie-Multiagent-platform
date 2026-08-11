package com.jd.genie.platform.phase2.configuration.skill.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
import com.jd.genie.platform.phase2contract.dto.VersionRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/skills")
@RequiredArgsConstructor
public class Phase2SkillController {
    private static final String OK = "OK";
    private static final String SUCCESS = "success";

    private final SkillDefinitionService skillService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<SkillApiAssembler.SkillView> create(@RequestBody(required = false) SkillCreateRequest request) {
        return success(assembler().skill(skillService.createSkill(currentUser(), request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<SkillApiAssembler.SkillView>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        PageResponse<SkillResponse> response = skillService.listSkills(currentUser(), page, pageSize);
        return success(new PageResponse<>(
            response.items().stream().map(assembler()::skill).toList(),
            response.page(),
            response.pageSize(),
            response.hasMore()
        ));
    }

    @GetMapping("/{id}")
    public ApiResponse<SkillApiAssembler.SkillView> detail(@PathVariable String id) {
        return success(assembler().skill(skillService.getSkill(currentUser(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<SkillApiAssembler.SkillView> update(@PathVariable String id,
                                                             @RequestBody(required = false) SkillUpdateRequest request) {
        return success(assembler().skill(skillService.updateSkill(currentUser(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        skillService.deleteSkill(currentUser(), id, version(request));
        return success(null);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<SkillApiAssembler.SkillView> enable(@PathVariable String id,
                                                             @RequestBody(required = false) VersionRequest request) {
        return success(assembler().skill(skillService.enableSkill(currentUser(), id, version(request))));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<SkillApiAssembler.SkillView> disable(@PathVariable String id,
                                                              @RequestBody(required = false) VersionRequest request) {
        return success(assembler().skill(skillService.disableSkill(currentUser(), id, version(request))));
    }

    private Long version(VersionRequest request) {
        return request == null ? null : request.version();
    }

    private CurrentUser currentUser() {
        return currentUserProvider.requireCurrentUser();
    }

    private SkillApiAssembler assembler() {
        return new SkillApiAssembler();
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, SUCCESS, data);
    }
}

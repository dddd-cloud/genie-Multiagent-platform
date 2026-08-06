package com.jd.genie.platform.phase2.configuration.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillCreateRequest;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillResponse;
import com.jd.genie.platform.phase2.configuration.skill.dto.SkillUpdateRequest;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillDefinitionService;
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
    private final ObjectMapper objectMapper;

    @PostMapping
    public ApiResponse<Phase2ApiAssembler.SkillView> create(@RequestBody(required = false) SkillCreateRequest request) {
        return success(assembler().skill(skillService.createSkill(currentUser(), request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<Phase2ApiAssembler.SkillView>> list(
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
    public ApiResponse<Phase2ApiAssembler.SkillView> detail(@PathVariable String id) {
        return success(assembler().skill(skillService.getSkill(currentUser(), id)));
    }

    @PutMapping("/{id}")
    public ApiResponse<Phase2ApiAssembler.SkillView> update(@PathVariable String id,
                                                             @RequestBody(required = false) SkillUpdateRequest request) {
        return success(assembler().skill(skillService.updateSkill(currentUser(), id, request)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @RequestBody(required = false) VersionRequest request) {
        skillService.deleteSkill(currentUser(), id, version(request));
        return success(null);
    }

    @PostMapping("/{id}/enable")
    public ApiResponse<Phase2ApiAssembler.SkillView> enable(@PathVariable String id,
                                                             @RequestBody(required = false) VersionRequest request) {
        return success(assembler().skill(skillService.enableSkill(currentUser(), id, version(request))));
    }

    @PostMapping("/{id}/disable")
    public ApiResponse<Phase2ApiAssembler.SkillView> disable(@PathVariable String id,
                                                              @RequestBody(required = false) VersionRequest request) {
        return success(assembler().skill(skillService.disableSkill(currentUser(), id, version(request))));
    }

    private Long version(VersionRequest request) {
        return request == null ? null : request.version();
    }

    private CurrentUser currentUser() {
        return currentUserProvider.requireCurrentUser();
    }

    private Phase2ApiAssembler assembler() {
        return new Phase2ApiAssembler(objectMapper);
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, SUCCESS, data);
    }
}

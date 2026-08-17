package com.jd.genie.platform.phase2.configuration.team.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.PageResponse;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamCreateRequest;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamResponse;
import com.jd.genie.platform.phase2.configuration.team.dto.TeamUpdateRequest;
import com.jd.genie.platform.phase2.configuration.team.service.AgentTeamService;
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
@RequestMapping("/api/v2/teams")
@RequiredArgsConstructor
public class Phase2TeamController {
    private static final String OK = "OK";
    private static final String SUCCESS = "success";

    private final AgentTeamService teamService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping
    public ApiResponse<TeamResponse> create(@RequestBody(required = false) TeamCreateRequest request) {
        return success(teamService.createTeam(currentUser(), request));
    }

    @GetMapping
    public ApiResponse<PageResponse<TeamResponse>> list(
        @RequestParam(defaultValue = "1") Integer page,
        @RequestParam(defaultValue = "20") Integer pageSize
    ) {
        return success(teamService.listTeams(currentUser(), page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<TeamResponse> detail(@PathVariable String id) {
        return success(teamService.getTeam(currentUser(), id));
    }

    @PutMapping("/{id}")
    public ApiResponse<TeamResponse> update(@PathVariable String id,
                                            @RequestBody(required = false) TeamUpdateRequest request) {
        return success(teamService.updateTeam(currentUser(), id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id,
                                    @RequestBody(required = false) VersionRequest request) {
        teamService.deleteTeam(currentUser(), id, request == null ? null : request.version());
        return success(null);
    }

    private CurrentUser currentUser() {
        return currentUserProvider.requireCurrentUser();
    }

    private <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(OK, SUCCESS, data);
    }
}

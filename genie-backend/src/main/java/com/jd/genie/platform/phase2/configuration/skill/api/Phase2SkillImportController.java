package com.jd.genie.platform.phase2.configuration.skill.api;

import com.jd.genie.platform.contract.ApiResponse;
import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.CurrentUserProvider;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.configuration.skill.service.SkillPackageImportService;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v2/skills")
@RequiredArgsConstructor
public class Phase2SkillImportController {
    private static final String OK = "OK";
    private static final String SUCCESS = "success";

    private final SkillPackageImportService importService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SkillApiAssembler.SkillView> importPackage(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "skillId", required = false) String skillId
    ) {
        if (file == null || file.isEmpty()) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "zip required");
        }
        if (file.getSize() > SkillPackageLimits.MAX_IMPORT_ZIP_BYTES) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "zip too large");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "zip cannot be read", e);
        }
        if (bytes.length > SkillPackageLimits.MAX_IMPORT_ZIP_BYTES) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "zip too large");
        }
        CurrentUser user = currentUserProvider.requireCurrentUser();
        return new ApiResponse<>(OK, SUCCESS, new SkillApiAssembler().skill(
            importService.importPackage(user, bytes, skillId)));
    }
}

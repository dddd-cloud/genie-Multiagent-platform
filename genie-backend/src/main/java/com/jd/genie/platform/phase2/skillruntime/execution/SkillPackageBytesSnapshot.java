package com.jd.genie.platform.phase2.skillruntime.execution;

import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import java.util.List;

public record SkillPackageBytesSnapshot(String packageHash, List<SkillPackageHasher.PackageFile> files) {
    public SkillPackageBytesSnapshot {
        files = files == null ? List.of() : List.copyOf(files);
    }
}

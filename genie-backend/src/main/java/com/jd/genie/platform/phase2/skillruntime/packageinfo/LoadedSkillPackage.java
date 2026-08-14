package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import com.jd.genie.platform.phase2contract.dto.SkillEntrypointView;
import java.util.List;

public record LoadedSkillPackage(String packageVersion, String name, String description,
                                 String instructionMarkdown, String packageHash,
                                 List<String> resourceManifest, List<SkillEntrypointView> entrypoints,
                                 List<SkillPackageHasher.PackageFile> files) {
    public LoadedSkillPackage {
        resourceManifest = resourceManifest == null ? List.of() : List.copyOf(resourceManifest);
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
        files = files == null ? List.of() : List.copyOf(files);
    }
}

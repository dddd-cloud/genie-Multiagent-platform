package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.contract.CurrentUser;
import com.jd.genie.platform.contract.UserRole;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.*;
import com.jd.genie.platform.phase2contract.enums.SkillEntrypointRuntime;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class BuiltinSkillSampleTest {
    @Test
    void repositoryBuiltinSampleIsAValidTemplatePackage() throws Exception {
        Path repositoryRoot = Path.of("..").toAbsolutePath().normalize();
        Path sample = repositoryRoot.resolve("skills/builtin/example");
        assertTrue(Files.isRegularFile(sample.resolve("SKILL.md")));
        assertTrue(Files.isRegularFile(sample.resolve("scripts/run.py")));
        SkillManifest manifest = new SkillManifestParser().parse(Files.readAllBytes(sample.resolve("SKILL.md")));
        assertEquals("example", manifest.name());
        assertEquals(1, manifest.entrypoints().size());
        assertEquals(SkillEntrypointRuntime.pyodide, manifest.entrypoints().get(0).runtime());
        assertEquals("scripts/run.py", manifest.entrypoints().get(0).script());

        // Builtin is deliberately outside users/{tenant}/{owner}/{skill}; it is not auto-imported.
        SkillPackageLoader loader = new SkillPackageLoader(repositoryRoot.resolve("skills").toString(),
            new SkillManifestParser(), new SkillPackageValidator(), new SkillPackageHasher());
        CurrentUser user = new CurrentUser("tenant", "owner", "owner", "Owner", UserRole.USER);
        assertTrue(loader.load(user, "example").isEmpty());
    }
}

package com.jd.genie.platform.phase2.skillruntime;

import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageHasher;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SkillPackageHasherTest {
    @Test
    void legacyHashIsCanonicalSortedAndSensitiveToContent() {
        SkillPackageHasher hasher = new SkillPackageHasher();
        String first = hasher.legacyHash("skill", 7, "name", "description", "instruction", "output", List.of("b", "a"));
        String reordered = hasher.legacyHash("skill", 7, "name", "description", "instruction", "output", List.of("a", "b"));
        String changed = hasher.legacyHash("skill", 7, "name", "description", "changed", "output", List.of("a", "b"));
        assertEquals(first, reordered);
        assertTrue(first.matches("[0-9a-f]{64}"));
        assertNotEquals(first, changed);
    }
}

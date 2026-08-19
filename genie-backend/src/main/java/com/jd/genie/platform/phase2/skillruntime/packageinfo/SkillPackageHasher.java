package com.jd.genie.platform.phase2.skillruntime.packageinfo;

import org.springframework.stereotype.Component;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;

@Component
public class SkillPackageHasher {
    public String filesystemHash(List<PackageFile> files) {
        MessageDigest digest = sha256();
        files.stream().sorted(Comparator.comparing(PackageFile::relativePath)).forEach(file -> {
            updateField(digest, file.relativePath().getBytes(StandardCharsets.UTF_8));
            updateField(digest, file.content());
        });
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public String legacyHash(String skillId, long skillVersion, String name, String description,
                             String instruction, String outputRequirement, List<String> capabilityKeys) {
        MessageDigest digest = sha256();
        for (String value : List.of(safe(skillId), Long.toString(skillVersion), safe(name), safe(description),
            safe(instruction), safe(outputRequirement))) updateField(digest, value.getBytes(StandardCharsets.UTF_8));
        (capabilityKeys == null ? List.<String>of() : capabilityKeys.stream().sorted().toList())
            .forEach(value -> updateField(digest, value.getBytes(StandardCharsets.UTF_8)));
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    public String runtimeToolName(String skillId, String entrypointName) {
        String digest = legacyHash(skillId + "\u0000" + entrypointName, 0, "", "", "", "", List.of());
        return "skill_" + digest.substring(0, 24);
    }

    private String safe(String value) { return value == null ? "" : value; }
    private void updateField(MessageDigest digest, byte[] bytes) {
        digest.update(ByteBuffer.allocate(Long.BYTES).putLong(bytes.length).array());
        digest.update(bytes);
    }
    private MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable", e); }
    }

    public record PackageFile(String relativePath, byte[] content) {
        public PackageFile { content = content.clone(); }
        public byte[] content() { return content.clone(); }
    }
}

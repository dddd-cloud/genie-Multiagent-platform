package com.jd.genie.platform.phase2.skillruntime.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2.skillruntime.packageinfo.SkillPackageLimits;
import com.jd.genie.platform.phase2contract.BrowserSkillExecutionContract;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@RequiredArgsConstructor
public class BrowserSkillExecutionBundleService {
    private final ObjectMapper mapper;

    public byte[] build(BrowserSkillExecutionCoordinator.Execution execution) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            HashSet<String> paths = new HashSet<>();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                for (var file : execution.snapshot().files()) {
                    safe(file.relativePath(), paths);
                    put(zip, file.relativePath(), file.content());
                }
                String manifest = BrowserSkillExecutionContract.EXECUTION_MANIFEST_PATH;
                safe(manifest, paths);
                put(zip, manifest, mapper.writeValueAsBytes(execution.manifest()));
            }
            if (bytes.size() > SkillPackageLimits.MAX_BUNDLE_BYTES)
                throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "execution bundle too large");
            return bytes.toByteArray();
        } catch (Phase2ContractException e) { throw e; }
        catch (IOException e) { throw new Phase2ContractException(MvpErrorCode.INTERNAL_ERROR, "bundle creation failed", e); }
    }
    private void safe(String path, HashSet<String> paths) {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\")
            || path.matches("^[A-Za-z]:.*") || java.util.Arrays.asList(path.split("/", -1)).contains("..")
            || !paths.add(path)) throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, "unsafe bundle path");
    }
    private void put(ZipOutputStream zip, String path, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(path); entry.setTime(0); zip.putNextEntry(entry); zip.write(content); zip.closeEntry();
    }
}

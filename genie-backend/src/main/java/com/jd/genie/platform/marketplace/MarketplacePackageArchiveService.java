package com.jd.genie.platform.marketplace;

import com.fasterxml.jackson.databind.JsonNode;
import com.jd.genie.platform.contract.MvpErrorCode;
import com.jd.genie.platform.phase2contract.error.Phase2ContractException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Builds a normal Skill import archive from reviewed classpath resources. */
@Service
public class MarketplacePackageArchiveService {
    public byte[] archive(JsonNode delivery) {
        if (delivery == null || !"EMBEDDED_SKILL_PACKAGE".equals(delivery.path("mode").asText())) {
            throw invalid("marketplace entry has no installable skill package");
        }
        String basePath = delivery.path("basePath").asText();
        JsonNode files = delivery.path("files");
        if (!safePath(basePath) || !files.isArray() || files.isEmpty()) {
            throw invalid("marketplace skill package metadata is invalid");
        }
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (JsonNode fileNode : files) {
                String file = fileNode.asText();
                if (!safePath(file)) throw invalid("marketplace skill package path is invalid");
                ClassPathResource resource = new ClassPathResource(basePath + "/" + file);
                if (!resource.exists()) throw invalid("marketplace skill package resource is missing");
                zip.putNextEntry(new ZipEntry(file));
                try (var input = resource.getInputStream()) {
                    zip.write(input.readAllBytes());
                }
                zip.closeEntry();
            }
            zip.finish();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID,
                "marketplace skill package cannot be read", exception);
        }
    }

    private boolean safePath(String value) {
        return value != null && !value.isBlank() && !value.startsWith("/") && !value.contains("\\")
            && !value.contains("..") && !value.contains(":");
    }

    private Phase2ContractException invalid(String message) {
        return new Phase2ContractException(MvpErrorCode.SKILL_PACKAGE_INVALID, message);
    }
}

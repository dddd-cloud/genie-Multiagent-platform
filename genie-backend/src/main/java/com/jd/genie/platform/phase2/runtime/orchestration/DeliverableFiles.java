package com.jd.genie.platform.phase2.runtime.orchestration;

import com.jd.genie.agent.dto.File;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DeliverableFiles {
    private DeliverableFiles() {
    }

    /**
     * Parallel subtasks call this concurrently on a shared sink, so the dedupe scan and the append
     * must happen under one lock on the sink itself.
     */
    static void collect(List<File> sink, List<File> produced) {
        if (sink == null || produced == null) {
            return;
        }
        synchronized (sink) {
            for (File file : produced) {
                if (file == null || Boolean.TRUE.equals(file.getIsInternalFile())) {
                    continue;
                }
                String url = firstNonBlank(file.getOssUrl(), file.getDomainUrl());
                String name = file.getFileName();
                if (url == null || name == null || name.isBlank()) {
                    continue;
                }
                if (alreadyCollected(sink, url)) {
                    continue;
                }
                sink.add(file);
            }
        }
    }

    private static boolean alreadyCollected(List<File> sink, String url) {
        for (File existing : sink) {
            if (existing != null && url.equals(firstNonBlank(existing.getOssUrl(), existing.getDomainUrl()))) {
                return true;
            }
        }
        return false;
    }

    static List<Map<String, Object>> toFileList(List<File> files) {
        if (files == null || files.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        List<File> snapshot;
        synchronized (files) {
            snapshot = new ArrayList<>(files);
        }
        for (File file : snapshot) {
            if (file == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fileName", file.getFileName());
            item.put("ossUrl", file.getOssUrl());
            item.put("domainUrl", file.getDomainUrl());
            item.put("fileSize", file.getFileSize() == null ? 0 : file.getFileSize());
            result.add(item);
        }
        return List.copyOf(result);
    }

    static String appendDownloadLinks(String answer, List<File> files) {
        String body = answer == null ? "" : answer.trim();
        if (files == null || files.isEmpty()) {
            return body;
        }
        StringBuilder links = new StringBuilder();
        for (File file : files) {
            if (file == null || file.getFileName() == null || file.getFileName().isBlank()) {
                continue;
            }
            String url = firstNonBlank(file.getOssUrl(), file.getDomainUrl());
            if (url == null) {
                continue;
            }
            if (body.contains(url)) {
                continue;
            }
            links.append("- [").append(file.getFileName()).append("](").append(url).append(")\n");
        }
        if (links.isEmpty()) {
            return body;
        }
        String section = "\n\n下载文件：\n" + links;
        return body.isEmpty() ? section.trim() : body + section;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}

package com.jd.genie.platform.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jd.genie.platform.marketplace.MarketplaceCatalogEntry;
import com.jd.genie.platform.marketplace.MarketplaceResourceService;
import com.jd.genie.platform.marketplace.MarketplaceResourceType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DraftGenerationService {
    private static final int MAX_PROMPT_LENGTH = 2_000;
    private final ObjectMapper objectMapper;
    private final MarketplaceResourceService marketplace;

    public DraftGenerationService(ObjectMapper objectMapper, MarketplaceResourceService marketplace) {
        this.objectMapper = objectMapper;
        this.marketplace = marketplace;
    }

    public GenerationDraftResponse generate(GenerationDraftRequest request) {
        String prompt = request == null || request.prompt() == null ? "" : request.prompt().trim();
        if (prompt.isBlank()) throw new GenerationValidationException("prompt must not be blank");
        if (prompt.length() > MAX_PROMPT_LENGTH) throw new GenerationValidationException("prompt must be at most " + MAX_PROMPT_LENGTH + " characters");
        String normalized = prompt.toLowerCase(Locale.ROOT);
        GenerationTarget requestedTarget = request == null ? null : request.target();
        GenerationTarget target = requestedTarget == null ? inferTarget(normalized) : requestedTarget;
        List<MarketplaceCatalogEntry> matches = matchingTemplates(normalized, target);
        String name = matches.isEmpty() ? (target == GenerationTarget.TEAM ? "自定义协作小组" : "自定义智能 Agent") : matches.get(0).name();
        ObjectNode draft = target == GenerationTarget.TEAM ? teamDraft(name, prompt, matches) : agentDraft(name, prompt, matches);
        List<String> suggestions = new ArrayList<>();
        suggestions.add("这是 Draft，不会直接写入 Agent/Team 数据表。");
        suggestions.add("确认模型、Skill、Tool 和 MCP 权限后，再通过现有管理页面保存。");
        List<String> missingFields = new ArrayList<>();
        if (target == GenerationTarget.TEAM) {
            suggestions.add("请在现有 Team 编辑页选择主 Agent 和成员 Agent。");
            missingFields.add("masterAgentId");
            missingFields.add("memberAgentIds");
        }
        String status = missingFields.isEmpty() ? "READY" : "NEEDS_CONFIGURATION";
        List<String> reasons = matches.isEmpty() ? List.of("没有匹配到现有模板，使用安全默认草稿") :
            matches.stream().map(MarketplaceCatalogEntry::tagline).toList();
        return new GenerationDraftResponse(target, name,
            target == GenerationTarget.TEAM ? "已生成 Team 草稿，可继续选择成员 Agent。" : "已生成 Agent 草稿，可继续确认配置。",
            matches.isEmpty() ? 0.62 : 0.88, draft,
            matches.stream().map(MarketplaceCatalogEntry::id).toList(),
            matches.stream().map(MarketplaceCatalogEntry::id).toList(),
            List.copyOf(suggestions),
            status, List.copyOf(missingFields), reasons);
    }

    private GenerationTarget inferTarget(String prompt) {
        return prompt.matches(".*(team|团队|小组|协同|多人|成员|主 agent).*") ? GenerationTarget.TEAM : GenerationTarget.AGENT;
    }

    private List<MarketplaceCatalogEntry> matchingTemplates(String prompt, GenerationTarget target) {
        MarketplaceResourceType type = target == GenerationTarget.TEAM ? MarketplaceResourceType.TEAM : MarketplaceResourceType.AGENT;
        return marketplace.entries().stream().filter(entry -> entry.type() == type).filter(entry ->
            (prompt.matches(".*(数据|csv|excel|表格|统计|pandas).*") && entry.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase("CSV")))
                || (prompt.matches(".*(研究|调研|财报|新闻|竞品).*") && entry.tags().stream().anyMatch(tag -> tag.equals("研究")))
                || (prompt.matches(".*(报告|pdf|report).*") && entry.tags().stream().anyMatch(tag -> tag.equals("报告")))
        ).limit(3).toList();
    }

    private ObjectNode agentDraft(String name, String prompt, List<MarketplaceCatalogEntry> matches) {
        ObjectNode draft = matches.isEmpty() || matches.get(0).draft() == null
            ? objectMapper.createObjectNode() : (ObjectNode) matches.get(0).draft().deepCopy();
        draft.put("name", name); draft.put("description", prompt);
        draft.put("promptMode", "RAW");
        if (!draft.hasNonNull("systemPrompt") || draft.get("systemPrompt").asText().isBlank()) {
            draft.put("systemPrompt", "你是一个可靠的任务助手。请根据用户目标分解任务，说明依据，并在执行前确认需要的资源权限。");
        }
        if (!draft.has("promptConfig")) draft.putNull("promptConfig");
        if (!draft.has("modelName")) draft.put("modelName", "default");
        if (!draft.has("skillIds")) {
            if (draft.has("skills")) {
                draft.set("skillIds", draft.get("skills").deepCopy());
                draft.remove("skills");
            } else {
                draft.set("skillIds", objectMapper.createArrayNode());
            }
        }
        if (!draft.has("capabilityKeys")) draft.set("capabilityKeys", objectMapper.createArrayNode());
        draft.remove("recommendedMarketplaceResources");
        return draft;
    }

    private ObjectNode teamDraft(String name, String prompt, List<MarketplaceCatalogEntry> matches) {
        ObjectNode draft = objectMapper.createObjectNode();
        draft.put("name", name); draft.put("description", prompt); draft.putNull("masterAgentId");
        draft.set("memberAgentIds", objectMapper.createArrayNode());
        return draft;
    }

    public static class GenerationValidationException extends RuntimeException {
        public GenerationValidationException(String message) { super(message); }
    }
}

package com.jd.genie.platform.phase2.configuration.prompt;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.jd.genie.agent.prompt.PlanningPrompt;
import com.jd.genie.agent.prompt.ToolCallPrompt;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;

final class V1PromptTestSupport {
    static final String PLANNER_SYSTEM = "autobots.autoagent.planner.system_prompt";
    static final String PLANNER_NEXT = "autobots.autoagent.planner.next_step_prompt";
    static final String EXECUTOR_SYSTEM = "autobots.autoagent.executor.system_prompt";
    static final String EXECUTOR_NEXT = "autobots.autoagent.executor.next_step_prompt";
    static final String REACT_SYSTEM = "autobots.autoagent.react.system_prompt";
    static final String REACT_NEXT = "autobots.autoagent.react.next_step_prompt";
    static final String STRUCT_PRE_POST = "autobots.autoagent.struct_pre_post_prompt_config";
    static final String GENIE_BASE = "autobots.autoagent.genie_base_prompt";

    private V1PromptTestSupport() {
    }

    static Properties loadApplicationProperties() {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new FileSystemResource("src/main/resources/application.yml"));
        Properties properties = factory.getObject();
        assertNotNull(properties, "application.yml should load as Spring YAML properties");
        return properties;
    }

    static Map<String, String> loadPromptMap(Properties properties, String key) {
        String json = properties.getProperty(key);
        assertNotNull(json, key + " should exist");
        return JSON.parseObject(json, new TypeReference<Map<String, String>>() {
        });
    }

    static String defaultPrompt(Properties properties, String key) {
        String prompt = loadPromptMap(properties, key).get("default");
        assertNotNull(prompt, key + ".default should exist");
        return prompt;
    }

    static Map<String, String> productionPrompts() {
        Properties properties = loadApplicationProperties();
        Map<String, String> prompts = new LinkedHashMap<>();
        prompts.put("application.planner.system", defaultPrompt(properties, PLANNER_SYSTEM));
        prompts.put("application.planner.next", defaultPrompt(properties, PLANNER_NEXT));
        prompts.put("application.executor.system", defaultPrompt(properties, EXECUTOR_SYSTEM));
        prompts.put("application.executor.next", defaultPrompt(properties, EXECUTOR_NEXT));
        prompts.put("application.react.system", defaultPrompt(properties, REACT_SYSTEM));
        prompts.put("application.react.next", defaultPrompt(properties, REACT_NEXT));
        prompts.put("application.genie.base", properties.getProperty(GENIE_BASE));
        prompts.put("fallback.planner.system", PlanningPrompt.SYSTEM_PROMPT);
        prompts.put("fallback.planner.next", PlanningPrompt.NEXT_STEP_PROMPT);
        prompts.put("fallback.tool.system", ToolCallPrompt.SYSTEM_PROMPT);
        prompts.put("fallback.tool.next", ToolCallPrompt.NEXT_STEP_PROMPT);
        flattenStructPrompts(properties, prompts);
        return prompts;
    }

    private static void flattenStructPrompts(Properties properties, Map<String, String> prompts) {
        String json = properties.getProperty(STRUCT_PRE_POST);
        assertNotNull(json, STRUCT_PRE_POST + " should exist");
        Map<String, Map<String, String>> sections = JSON.parseObject(json,
            new TypeReference<Map<String, Map<String, String>>>() {
            });
        sections.forEach((section, values) -> values.forEach((slot, text) -> {
            if (text != null && !text.isBlank()) {
                prompts.put("application.struct." + section + "." + slot, text);
            }
        }));
    }

    static String allProductionPromptText() {
        return String.join("\n---\n", productionPrompts().values());
    }

    static List<String> unresolvedPlaceholders(String prompt) {
        List<String> placeholders = new ArrayList<>();
        int index = 0;
        while (index >= 0 && index < prompt.length()) {
            int start = prompt.indexOf("{{", index);
            if (start < 0) {
                break;
            }
            int end = prompt.indexOf("}}", start + 2);
            if (end < 0) {
                placeholders.add(prompt.substring(start));
                break;
            }
            placeholders.add(prompt.substring(start, end + 2));
            index = end + 2;
        }
        return placeholders;
    }
}

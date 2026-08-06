package com.jd.genie.platform.phase2.configuration.prompt;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PromptForbiddenDefaultScanTest {
    private static final Pattern[] FORBIDDEN_DEFAULT_BIASES = new Pattern[] {
        Pattern.compile("(?is)at least.{0,40}(search|function call).{0,20}2"),
        Pattern.compile("(?is)(search|tool).{0,20}3\\s*-\\s*5"),
        Pattern.compile("(?is)must use.{0,40}search"),
        Pattern.compile("(?is)must use tools"),
        Pattern.compile("(?is)default.{0,30}HTML"),
        Pattern.compile("(?is)must.{0,20}HTML"),
        Pattern.compile("(?is)all tasks.{0,40}HTML"),
        Pattern.compile("(?is)forced Chinese"),
        Pattern.compile("(?is)must use Chinese"),
        Pattern.compile("(?is)show.{0,30}(reasoning|chain of thought|hidden analysis)"),
        Pattern.compile("(?is)output.{0,30}chain of thought"),
        Pattern.compile("(?is)finance report by default"),
        Pattern.compile("(?is)valuation analysis by default"),
        Pattern.compile("(?is)investor sentiment by default")
    };

    @Test
    void productionPromptsDoNotContainForbiddenDefaultBiases() {
        V1PromptTestSupport.productionPrompts().forEach((name, prompt) -> {
            for (Pattern forbidden : FORBIDDEN_DEFAULT_BIASES) {
                assertFalse(forbidden.matcher(prompt).find(), () -> name + " contains forbidden default bias: " + forbidden);
            }
        });
    }
}

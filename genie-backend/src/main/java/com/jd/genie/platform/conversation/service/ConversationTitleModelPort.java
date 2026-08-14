package com.jd.genie.platform.conversation.service;

/**
 * Cheap-model summarizer for the first-turn conversation title.
 */
public interface ConversationTitleModelPort {
    /**
     * @return a short title, or blank when the model is unavailable
     */
    String summarizeFirstQuery(String query);
}

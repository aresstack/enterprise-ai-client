package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The conversation before the current message, as stored (without any analysis of earlier turns). */
public final class ConversationContext {

    private final List<ChatMessage> history;

    private ConversationContext(List<ChatMessage> history) {
        this.history = Collections.unmodifiableList(new ArrayList<ChatMessage>(history));
    }

    public static ConversationContext of(List<ChatMessage> history) {
        return new ConversationContext(history == null ? Collections.<ChatMessage>emptyList() : history);
    }

    public List<ChatMessage> history() {
        return history;
    }
}

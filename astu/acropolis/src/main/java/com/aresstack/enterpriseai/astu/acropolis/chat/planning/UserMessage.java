package com.aresstack.enterpriseai.astu.acropolis.chat.planning;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;

/** The current message exactly as the user typed or dictated it. */
public final class UserMessage {

    private final String text;
    private final MessageOrigin origin;

    public UserMessage(String text, MessageOrigin origin) {
        this.text = text == null ? "" : text;
        this.origin = origin == null ? MessageOrigin.TYPED : origin;
    }

    public String text() {
        return text;
    }

    public MessageOrigin origin() {
        return origin;
    }
}

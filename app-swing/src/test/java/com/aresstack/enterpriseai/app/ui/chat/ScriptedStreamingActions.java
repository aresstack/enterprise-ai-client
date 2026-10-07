package com.aresstack.enterpriseai.app.ui.chat;

import java.util.Arrays;
import java.util.List;

/**
 * Ein synchroner Fake-Backend für die Shell: beantwortet jede Nachricht mit vorgegebenen Deltas. Mit
 * {@code holdOpen} bleibt die Antwort nach den Deltas offen, damit Stop getestet werden kann.
 */
final class ScriptedStreamingActions implements ChatShellActions {

    private final ChatShellModel model;
    private final List<String> deltas;
    private final boolean holdOpen;

    ScriptedStreamingActions(ChatShellModel model, boolean holdOpen, String... deltas) {
        this.model = model;
        this.holdOpen = holdOpen;
        this.deltas = Arrays.asList(deltas);
    }

    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        model.addUserMessage(text);
        model.beginAssistantMessage();
        for (String delta : deltas) {
            model.appendAssistantDelta(delta);
        }
        if (!holdOpen) {
            model.completeAssistantMessage();
        }
    }

    @Override
    public void stopRequested() {
        model.cancelAssistantMessage();
    }
}

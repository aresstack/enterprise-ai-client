package com.aresstack.enterpriseai.app.ui.chat;

import java.util.ArrayList;
import java.util.List;

/** Zeichnet Bedienabsichten auf, ohne etwas zu tun. */
final class RecordingActions implements ChatShellActions {

    final List<String> sent = new ArrayList<String>();
    final List<Boolean> ragFlags = new ArrayList<Boolean>();
    int stops;

    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        sent.add(text);
        ragFlags.add(ragEnabled);
    }

    @Override
    public void stopRequested() {
        stops++;
    }
}

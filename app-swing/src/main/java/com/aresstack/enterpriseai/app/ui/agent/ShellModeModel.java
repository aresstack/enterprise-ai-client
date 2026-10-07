package com.aresstack.enterpriseai.app.ui.agent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Presentation-Model der Modus-Umschaltung: welcher Modus sichtbar ist und ob der Agent-Modus überhaupt
 * angeboten wird. Ohne Swing; nicht threadsicher, in der Anwendung nur auf dem EDT verwenden.
 *
 * <p>Ist kein Agent konfiguriert, bleibt die Shell im Chat-Modus; der Wunsch nach {@link ShellMode#AGENT} wird
 * dann ignoriert. Der Chat braucht den Agent-Modus nicht.
 */
public final class ShellModeModel {

    /** Beobachtet Moduswechsel; wird auf dem Thread des Models gerufen. */
    public interface Listener {
        void modeChanged(ShellMode mode);
    }

    private final boolean agentAvailable;
    private final List<Listener> listeners = new CopyOnWriteArrayList<Listener>();
    private ShellMode mode = ShellMode.CHAT;

    public ShellModeModel(boolean agentAvailable) {
        this.agentAvailable = agentAvailable;
    }

    public boolean isAgentAvailable() {
        return agentAvailable;
    }

    public ShellMode getMode() {
        return mode;
    }

    /** @return ob der Modus danach {@code requested} ist */
    public boolean select(ShellMode requested) {
        if (requested == null || (requested == ShellMode.AGENT && !agentAvailable)) {
            return false;
        }
        if (requested != mode) {
            mode = requested;
            for (Listener listener : listeners) {
                listener.modeChanged(mode);
            }
        }
        return true;
    }

    public void addListener(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}

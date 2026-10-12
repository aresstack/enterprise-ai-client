package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;

/**
 * Die Stimmen der Übergabe am Ende der ersten Einrichtung: Merlins eigene ({@code setup.merlin.ttsModel}) und die des
 * Chat-Assistenten ({@code model.tts}) mit seinem Anzeigenamen ({@code chat.assistant.displayName}), gebunden an die
 * gerade gespeicherte Einrichtung. Eine Stimme darf fehlen ({@code null} oder nicht verfügbar): dann steht der Satz
 * nur in der Sprechblase. {@link #close()} gibt beide frei, sobald die Übergabe vorbei ist.
 */
public final class SetupVoices {

    private final ReadAloudControl merlin;
    private final ReadAloudControl assistant;
    private final String assistantName;
    private final Runnable release;

    /**
     * @param assistantName Anzeigename des Chat-Assistenten; leer = {@link WizardGuide#ASSISTANT_FALLBACK}
     * @param release       gibt die Stimmen frei oder {@code null}
     */
    public SetupVoices(ReadAloudControl merlin, ReadAloudControl assistant, String assistantName, Runnable release) {
        this.merlin = merlin;
        this.assistant = assistant;
        this.assistantName = assistantName == null || assistantName.trim().isEmpty() ? WizardGuide.ASSISTANT_FALLBACK
                : assistantName.trim();
        this.release = release;
    }

    /** Ohne Stimmen: die Übergabe läuft nur in Sprechblasen. */
    public static SetupVoices silent(String assistantName) {
        return new SetupVoices(null, null, assistantName, null);
    }

    ReadAloudControl merlin() {
        return merlin;
    }

    ReadAloudControl assistant() {
        return assistant;
    }

    String assistantName() {
        return assistantName;
    }

    /** Stoppt und gibt beide Stimmen frei; idempotent genug für ein zweites Schließen. */
    void close() {
        if (merlin != null) {
            merlin.stop();
        }
        if (assistant != null) {
            assistant.stop();
        }
        if (release != null) {
            release.run();
        }
    }
}

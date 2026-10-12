package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.chat.MessageOrigin;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.LinguisticFrame;
import com.aresstack.enterpriseai.astu.acropolis.chat.linguistics.TurnAnalysisObserver;
import com.aresstack.enterpriseai.astu.acropolis.chat.planning.AnswerPlan;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolActivityListener;

import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

/**
 * Diagnostic log of the tool choice in the chat ({@code chat.diagnostics.turnAnalysis}): per message the user text,
 * its source, the linguistic frame, the reading of the answer planning step and the block sent to the model, then each
 * tool call the model makes. Read per turn,
 * so switching it in the settings applies without restart. Off by default because it writes user text; never stored
 * as chat messages.
 */
public final class TurnDiagnosticsLog implements TurnAnalysisObserver, ToolActivityListener {

    private static final Logger LOG = Logger.getLogger(TurnDiagnosticsLog.class.getName());

    private final BooleanSupplier enabled;

    public TurnDiagnosticsLog(BooleanSupplier enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("enabled must not be null");
        }
        this.enabled = enabled;
    }

    @Override
    public void analyzed(String userText, MessageOrigin origin, LinguisticFrame frame, String block) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        LOG.info("Turn-Analyse: Nutzertext=\"" + userText + "\", Quelle="
                + (origin == MessageOrigin.SPEECH_TRANSCRIPT ? "SPEECH" : "TEXT") + "\n" + frame + "\nAn das Modell:\n"
                + block);
    }

    @Override
    public void planned(String userText, AnswerPlan turn, String block) {
        if (!enabled.getAsBoolean()) {
            return;
        }
        LOG.info("Antwortplan: " + turn + "\nAntwort des Modells: " + turn.raw() + "\nAn die Antwort:\n"
                + (block == null ? "(kein Plan, es gilt die Analyse)" : block));
    }

    @Override
    public void onToolCall(String toolName) {
        onToolCall(toolName, null);
    }

    @Override
    public void onToolCall(String toolName, String arguments) {
        if (enabled.getAsBoolean()) {
            LOG.info("Turn-Analyse: Werkzeugaufruf " + toolName + (arguments == null ? "" : " " + arguments));
        }
    }

    @Override
    public void onToolResult(String toolName, boolean succeeded) {
        if (enabled.getAsBoolean()) {
            LOG.info("Turn-Analyse: Werkzeug " + toolName + (succeeded ? " fertig" : " fehlgeschlagen"));
        }
    }
}

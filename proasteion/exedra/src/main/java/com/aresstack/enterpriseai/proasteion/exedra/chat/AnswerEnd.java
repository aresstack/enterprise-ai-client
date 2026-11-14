package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.chat.ChatFinishReason;
import com.aresstack.enterpriseai.astu.chat.ChatResponse;
import com.aresstack.enterpriseai.astu.chat.ChatUsage;

/**
 * Übersetzt den Abschlussgrund einer Modellantwort in den Hinweis des Chats (Issue #223): Endet eine Antwort am
 * Ausgabelimit ({@code finish_reason = length}) oder am Inhaltsfilter des Dienstes, ist sie unvollständig, und der
 * Verlauf sagt das, statt sie wie eine fertige Antwort zu zeigen. {@code stop}, {@code tool_calls} und ein nicht
 * gemeldeter Grund gelten als regulär: ohne Angabe des Dienstes wird keine Antwort verdächtigt.
 */
final class AnswerEnd {

    static final String LENGTH_NOTICE = "Die Antwort ist unvollständig: Das Ausgabelimit wurde erreicht";
    static final String LENGTH_ADVICE = ". Mit „Bitte fortsetzen“ schreibt das Modell weiter; ein höheres Limit "
            + "steht unter Einstellungen → „Netzwerk & Agent“ → „Chat: Ausgabelimit (Tokens)“.";
    static final String FILTER_NOTICE = "Die Antwort ist unvollständig: Der Inhaltsfilter des KI-Dienstes hat sie "
            + "beendet.";

    private AnswerEnd() {
    }

    /** Der Hinweis zu einer unvollständigen Antwort, oder {@code null}, wenn sie regulär endete. */
    static String incompleteNotice(ChatResponse response) {
        if (response == null) {
            return null;
        }
        if (response.finishReason() == ChatFinishReason.LENGTH) {
            ChatUsage usage = response.usage();
            String tokens = usage != null && usage.isReported() && usage.completionTokens() > 0
                    ? " (" + usage.completionTokens() + " Tokens)" : "";
            return LENGTH_NOTICE + tokens + LENGTH_ADVICE;
        }
        if (response.finishReason() == ChatFinishReason.CONTENT_FILTER) {
            return FILTER_NOTICE;
        }
        return null;
    }
}

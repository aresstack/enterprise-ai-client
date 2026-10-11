package com.aresstack.enterpriseai.research.runtime.team;

import java.util.regex.Pattern;

/**
 * Ob eine Chat-Nachricht in der Phase Gliederung ein Änderungswunsch an der Gliederung ist oder eine Frage, die der
 * Agent aus dem Projektkorpus beantwortet. Host (Übergang REQUEST_OUTLINE_CHANGES) und Agent (Überarbeiten statt
 * Antworten) entscheiden mit derselben Regel.
 *
 * <p>Heuristik (eigene Festlegung, kippbar): ein Änderungsverb (ergänzen, streichen, umbenennen, verschieben, …)
 * zusammen mit einem Gliederungsbegriff (Kapitel, Abschnitt, Gliederung, Leitfrage, …), oder ein Änderungsverb im
 * Imperativ am Satzanfang („Ergänze …“, „Streiche …“). Eine W-Frage („Welche Änderungen enthält Kapitel 2?“) ist
 * nie ein Änderungswunsch. Alles andere ist eine Frage.
 */
public final class OutlineChangeRequest {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private static final Pattern CHANGE_VERB = Pattern.compile("(?<![\\p{L}])(ergänz\\w*|hinzufüg\\w*|füg\\w*|"
            + "streich\\w*|entfern\\w*|lösch\\w*|umbenenn\\w*|benenn\\w*|änder\\w*|verschieb\\w*|vertausch\\w*|"
            + "tausch\\w*|zusammenfass\\w*|zusammenleg\\w*|aufteil\\w*|teil\\w* \\S+ auf|kürz\\w*|erweiter\\w*|"
            + "ersetz\\w*|nimm|nehm\\w*|überarbeit\\w*|umstell\\w*|stell\\w* \\S+ um|sortier\\w*|add|remove|rename|"
            + "move|delete|change)(?![\\p{L}])", FLAGS);

    private static final Pattern OUTLINE_NOUN = Pattern.compile("(?<![\\p{L}])(kapitel\\w*|gliederung\\w*|"
            + "abschnitt\\w*|unterkapitel\\w*|unterpunkt\\w*|leitfrage\\w*|überschrift\\w*|titel|reihenfolge|"
            + "chapter\\w*|section\\w*|outline)(?![\\p{L}])", FLAGS);

    private static final Pattern IMPERATIVE_START = Pattern.compile("^\\s*(bitte\\s+)?(ergänze|füge|streiche|"
            + "entferne|lösche|benenne|ändere|verschiebe|vertausche|tausche|fasse|lege|teile|kürze|erweitere|"
            + "ersetze|nimm|überarbeite|stelle|sortiere)(?![\\p{L}])", FLAGS);

    private static final Pattern W_QUESTION = Pattern.compile("^\\s*(was|wer|wem|wen|wessen|wie|welche\\w*|wann|"
            + "wo|woher|wohin|warum|wieso|weshalb|what|who|which|when|where|why|how)(?![\\p{L}])", FLAGS);

    private OutlineChangeRequest() {
    }

    /** {@code true}, wenn die Nachricht die Gliederung ändern will; leere Nachrichten nie. */
    public static boolean isChangeRequest(String message) {
        String text = message == null ? "" : message.trim();
        if (text.isEmpty()) {
            return false;
        }
        if (IMPERATIVE_START.matcher(text).find()) {
            return true;
        }
        if (W_QUESTION.matcher(text).find()) {
            return false;
        }
        return CHANGE_VERB.matcher(text).find() && OUTLINE_NOUN.matcher(text).find();
    }
}

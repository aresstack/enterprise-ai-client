package com.aresstack.enterpriseai.astu.acropolis.rag;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Erkennt im normalen Chat eine Frage nach dem Assistenten selbst („Welche Tools hast du?“, „Kannst du recherchieren?“,
 * „Welches Modell verwendest du?“). Die Antwort darauf kommt aus dem Laufzeitzustand (angebotene Werkzeuge), nicht aus
 * dem Modell oder der Wissensbasis. Ob eine Nachricht in den Quellen sucht, entscheidet nicht diese Klasse, sondern die
 * Vorprüfung durch das Modell ({@link RetrievalTriage}).
 *
 * <p>Die Wortliste ist eine Heuristik (kippbar), keine Sprachanalyse. Rein und zustandslos.
 */
public final class KnowledgeQuestion {

    /**
     * Wörter, die den Assistenten ansprechen; eine Frage nach seinen Fähigkeiten enthält mindestens eines. Die
     * Sie-Form fehlt bewusst: „sie“ ist ebenso oft ein Objekt („Kannst du sie lesen?“).
     */
    private static final Set<String> ADDRESSEE = setOf(
            "du", "dich", "dir", "dein", "deine", "deinen", "deinem", "deiner", "deines", "you", "your", "yours",
            "yourself");

    /**
     * Was in einer Frage nach den Fähigkeiten des Assistenten stehen darf: Fähigkeiten (Werkzeuge, Recherche, Lesen,
     * Modell, Quellenarten) und Füllwörter. Ein Sachbegriff außerhalb dieser Liste („über IAR“, „zum Deployment“)
     * macht daraus eine Wissensfrage. Präpositionen, die ein Thema einleiten (über, zu, nach, about), fehlen bewusst.
     */
    private static final Set<String> CAPABILITY_WORDS = setOf(
            // Fähigkeiten
            "tool", "tools", "werkzeug", "werkzeuge", "werkzeugen", "mcp", "funktion", "funktionen", "fähigkeit",
            "fähigkeiten", "faehigkeiten", "feature", "features", "capability", "capabilities", "function",
            "functions", "plugin", "plugins", "modell", "modelle", "model", "models", "llm", "ki", "agent",
            "agenten", "agentenmodus", "recherchieren", "recherche", "research", "suchen", "durchsuchen", "search",
            "websuche", "internet", "web", "lesen", "read", "öffnen", "oeffnen", "open", "zugreifen", "zugriff",
            "access", "datei", "dateien", "file", "files", "anhang", "anhänge", "anhaenge", "attachment",
            "attachments", "bild", "bilder", "pdf", "pdfs", "dokument", "dokumente", "wissen", "wissensquelle",
            "wissensquellen", "wissensbasis", "quelle", "quellen", "knowledge", "source", "sources", "confluence",
            "mediawiki", "wiki", "sharepoint", "outlook",
            // Verben und Füllwörter einer solchen Frage
            "kannst", "könntest", "koenntest", "kann", "können", "koennen", "hast", "habt", "haben", "hat", "bist",
            "verwendest", "verwenden", "benutzt", "benutzen", "nutzt", "nutzen", "machen", "tun", "use", "using",
            "uses", "have", "has", "can", "could", "do", "does", "are", "is", "was", "welche", "welcher", "welches",
            "welchen", "welchem", "wie", "viele", "what", "which", "how", "many", "any", "es", "gibt", "gibts",
            "zur", "verfügung", "verfuegung", "stehen", "steht", "available", "auch", "noch", "denn", "eigentlich",
            "mal", "schon", "alles", "alle", "überhaupt", "ueberhaupt", "irgendwelche", "irgendwelchen", "aktuell",
            "gerade", "jetzt", "momentan", "selbst", "also", "too", "all", "und", "oder", "and", "or", "ein", "eine",
            "einen", "einer", "einem", "der", "die", "das", "den", "dem", "des", "a", "an", "the", "mir", "uns",
            "me", "us", "mit", "im", "in", "auf", "für", "fuer", "to", "on", "for", "ja", "bitte", "please", "sag", "sage", "nenne", "nenn", "zeig", "zeige",
            "liste", "list", "tell", "show", "name");

    private KnowledgeQuestion() {
    }

    /**
     * Fragt die Nachricht nach dem Assistenten selbst („Hast du MCP-Tools?“, „Kannst du Dateien lesen?“)? Sie spricht
     * den Assistenten an und enthält nur Wörter über Fähigkeiten und Füllwörter, keinen Sachbegriff.
     */
    public static boolean isAssistantCapability(String message) {
        String[] words = words(message);
        boolean addressed = false;
        for (String word : words) {
            if (ADDRESSEE.contains(word)) {
                addressed = true;
            } else if (!CAPABILITY_WORDS.contains(word)) {
                return false;
            }
        }
        return addressed;
    }

    private static String[] words(String message) {
        if (message == null) {
            return new String[0];
        }
        String normalized = message.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
        return normalized.isEmpty() ? new String[0] : normalized.split(" ");
    }

    private static Set<String> setOf(String... values) {
        return Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(values)));
    }
}

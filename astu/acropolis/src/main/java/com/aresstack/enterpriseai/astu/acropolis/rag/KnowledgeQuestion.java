package com.aresstack.enterpriseai.astu.acropolis.rag;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Entscheidet für den normalen Chat, ob eine Nachricht vor der Antwort eine automatische Wissenssuche auslöst.
 * Angehakte Wissensquellen heißen „verfügbar“, nicht „bei jeder Nachricht suchen“ (wie askai arch: „a greeting
 * deserves a greeting … never manufacture a search just because the field exists“; MainframeMate sucht im Chat nur
 * in ausdrücklich angehängten Dokumenten).
 *
 * <ul>
 *   <li><b>Small Talk</b> ({@link #isSmallTalk}: Gruß, Dank, Zustimmung, Abschied; nur Wörter aus einer kurzen
 *       Liste, höchstens {@value #MAX_SMALL_TALK_WORDS} Wörter): nie suchen.</li>
 *   <li><b>Frage nach dem Assistenten selbst</b> ({@link #isAssistantCapability}: „Welche Tools hast du?“, „Kannst
 *       du recherchieren?“, „Welches Modell verwendest du?“): nie suchen. Die Antwort kommt aus dem Laufzeitzustand
 *       (angebotene Werkzeuge), nicht aus der Wissensbasis; deren Treffer zu „Recherche“ oder „Tool“ wären fremder
 *       Fachinhalt.</li>
 *   <li><b>Mit Wissens-Werkzeugen</b> ({@code knowledge_search}/{@code knowledge_read}): das Modell entscheidet
 *       selbst; vorab gesucht wird nur bei einer erkennbaren Wissensfrage ({@link #isRecognizableQuestion}:
 *       Fragezeichen, Fragewort oder Such-/Erklär-Auftrag am Anfang).</li>
 *   <li><b>Ohne Wissens-Werkzeuge</b>: die Vorabsuche ist der einzige Weg zum Wissen; gesucht wird bei allem außer
 *       Small Talk.</li>
 * </ul>
 *
 * <p>Die Wortlisten sind eine Heuristik (kippbar), keine Sprachanalyse. Rein und zustandslos.
 */
public final class KnowledgeQuestion {

    /** Mehr Wörter sind kein bloßer Gruß mehr, auch wenn jedes einzelne in der Liste steht. */
    static final int MAX_SMALL_TALK_WORDS = 6;

    private static final Set<String> SMALL_TALK = setOf(
            "hi", "hallo", "halo", "hey", "hej", "moin", "servus", "grüß", "gruß", "grüezi", "gott", "guten",
            "morgen", "tag", "abend", "nacht", "mahlzeit", "danke", "dank", "vielen", "herzlichen", "besten",
            "dankeschön", "merci", "thx", "thanks", "thank", "you", "ok", "okay", "oki", "okey", "alles", "klar",
            "super", "prima", "toll", "cool", "gut", "sehr", "passt", "perfekt", "genau", "ja", "nein", "jo", "jep",
            "nö", "ne", "tschüss", "tschüs", "ciao", "bye", "bis", "später", "bald", "dann", "wie", "geht", "gehts",
            "s", "es", "dir", "ihnen", "euch", "und", "selbst", "mir", "auch", "hello", "good", "morning", "evening",
            "night", "yes", "no", "fine", "great", "nice", "bitte", "gerne", "gern", "lol", "haha");

    private static final Set<String> QUESTION_STARTS = setOf(
            "was", "wer", "wen", "wem", "wessen", "wie", "wo", "wann", "warum", "wieso", "weshalb", "welche",
            "welcher", "welches", "welchen", "welchem", "woher", "wohin", "womit", "wozu", "worum", "wofür",
            "worauf", "gibt", "gibts", "kannst", "könnt", "können", "koennen", "kann", "hast", "habt", "haben",
            "ist", "sind", "erkläre", "erklär", "erklaere", "beschreibe", "beschreib", "zeig", "zeige", "nenne",
            "nenn", "liste", "fasse", "fass", "suche", "such", "finde", "find", "recherchiere", "what", "how",
            "who", "where", "when", "why", "which", "explain", "describe", "show", "list", "find", "search",
            "tell", "is", "are", "can", "could", "does", "do", "summarize", "summarise");

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

    /** Wovon eine Nachricht im normalen Chat handelt. */
    public enum Kind {
        /** Gruß, Dank, Zustimmung, Abschied: nie suchen. */
        SMALL_TALK,
        /** Frage nach den Fähigkeiten des Assistenten (Werkzeuge, Modell, Quellen): nie suchen. */
        ASSISTANT_CAPABILITY,
        /** Erkennbare Wissensfrage: vorab suchen. */
        KNOWLEDGE_QUESTION,
        /** Alles andere: mit Wissens-Werkzeugen entscheidet das Modell, ohne wird vorab gesucht. */
        OTHER
    }

    /** Ordnet eine Nachricht ein; Small Talk vor Fähigkeitsfrage vor Wissensfrage. */
    public static Kind classify(String message) {
        if (isSmallTalk(message)) {
            return Kind.SMALL_TALK;
        }
        if (isAssistantCapability(message)) {
            return Kind.ASSISTANT_CAPABILITY;
        }
        return isRecognizableQuestion(message) ? Kind.KNOWLEDGE_QUESTION : Kind.OTHER;
    }

    /**
     * @param knowledgeTools stehen dem Modell in diesem Turn {@code knowledge_search}/{@code knowledge_read} zur
     *                       Verfügung?
     * @return {@code true}, wenn vor der Antwort automatisch in den angehakten Quellen gesucht werden soll
     */
    public static boolean wantsRetrieval(String message, boolean knowledgeTools) {
        switch (classify(message)) {
            case SMALL_TALK:
            case ASSISTANT_CAPABILITY:
                return false;
            case KNOWLEDGE_QUESTION:
                return true;
            default:
                return !knowledgeTools;
        }
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

    /** Gruß, Dank, Zustimmung oder Abschied ohne Sachanliegen („Hi“, „Hallo!“, „Danke dir“, „ok“). */
    public static boolean isSmallTalk(String message) {
        String[] words = words(message);
        if (words.length == 0) {
            return true; // nur Satzzeichen oder Emoji
        }
        if (words.length > MAX_SMALL_TALK_WORDS) {
            return false;
        }
        for (String word : words) {
            if (!SMALL_TALK.contains(word)) {
                return false;
            }
        }
        return true;
    }

    /** Fragezeichen, Fragewort oder Such-/Erklär-Auftrag am Anfang („Wie lange …“, „Suche Seiten zu …“). */
    public static boolean isRecognizableQuestion(String message) {
        if (message == null) {
            return false;
        }
        if (message.indexOf('?') >= 0) {
            return true;
        }
        String[] words = words(message);
        return words.length > 0 && QUESTION_STARTS.contains(words[0]);
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

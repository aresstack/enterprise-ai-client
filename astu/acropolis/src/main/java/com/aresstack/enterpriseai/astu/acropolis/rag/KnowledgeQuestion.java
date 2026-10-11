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

    private KnowledgeQuestion() {
    }

    /**
     * @param knowledgeTools stehen dem Modell in diesem Turn {@code knowledge_search}/{@code knowledge_read} zur
     *                       Verfügung?
     * @return {@code true}, wenn vor der Antwort automatisch in den angehakten Quellen gesucht werden soll
     */
    public static boolean wantsRetrieval(String message, boolean knowledgeTools) {
        if (isSmallTalk(message)) {
            return false;
        }
        return !knowledgeTools || isRecognizableQuestion(message);
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

package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.application.config.ChatConfig;

/**
 * Merlin, der Begleiter des Einrichtungsassistenten: seine Persönlichkeit als feste Texte je Schritt. Alles hier ist
 * deterministisch, damit Einrichtung, Fehlerfälle und Bedienhinweise immer stimmen; das Chatmodell kommt nur bei einer
 * freien Frage dazu ({@link #systemPrompt(int)}). Bis der KI-Dienst verbunden ist, schweigt Merlin; danach stellt er
 * sich vor (beim Erststart ausführlich, bei einem späteren Aufruf als Wiedersehen) und meldet sich in jedem Schritt
 * kurz. Kennt weder Swing noch Sprachausgabe; das entscheidet {@link WizardNarrator}.
 */
final class WizardGuide {

    static final String NAME = "Merlin";

    static final String FIRST_GREETING = "Ah, wunderbar – jetzt kann ich endlich mit dir sprechen! Ich bin Merlin, "
            + "dein persönlicher Assistent. Ich begleite dich noch kurz durch die Einrichtung – und danach wirst du mich "
            + "auch nicht so schnell wieder los.";
    static final String WELCOME_BACK = "Da bist du ja wieder. Was wollen wir diesmal an meiner Einrichtung ändern?";
    static final String KEEPASS_OK = "Sehr gut. Die Verbindung zu KeePass steht. Dann sehen wir uns jetzt den "
            + "KI-Dienst an.";
    static final String SERVICE_OK = "Ausgezeichnet. Der KI-Dienst antwortet. Jetzt suche ich uns passende Modelle "
            + "aus.";
    static final String FAILURE = "Das hat gerade nicht funktioniert. Deine Eingaben sind aber noch da – wir können "
            + "es noch einmal versuchen.";
    static final String THINKING = "Moment, ich denke kurz nach …";
    static final String NO_ANSWER = "Darauf fällt mir gerade keine Antwort ein. Probier es gleich noch einmal oder "
            + "frag mich nach dem, was in diesem Schritt zu tun ist.";

    /** Merlins Satz neben den technischen Details der Einstellungen. */
    static final String TECHNICAL_CORNER = "Ich wusste, dass wir uns wiedersehen.";

    /** Ohne eingestellten Namen des Chat-Assistenten. */
    static final String ASSISTANT_FALLBACK = ChatConfig.DEFAULT_ASSISTANT_DISPLAY_NAME;

    /**
     * Die Übergabe nach der ersten erfolgreichen Einrichtung: Merlin gibt an seine Tochter (den Chat-Assistenten mit
     * ihrer Stimme) ab, die ihn für überflüssig hält; er behält das letzte Wort. Je Zeile: wer spricht, was.
     */
    private static final Line[] HANDOFF = {
            new Line(true, "Damit wären wir fertig. Ab hier übernimmt meine Tochter."),
            new Line(false, "Danke, Dad. Du wirst jetzt nicht mehr gebraucht. Wir haben jetzt KI."),
            new Line(true, "Natürlich, mein Kind."),
            new Line(true, "Ruf mich einfach, wenn die KI nicht weiterweiß.")
    };

    /** Anzahl der Zeilen der Übergabe. */
    static int handoffLines() {
        return HANDOFF.length;
    }

    /** Zeile {@code index} der Übergabe. */
    static Line handoff(int index) {
        return HANDOFF[index];
    }

    /** Eine Zeile der Übergabe: Merlin oder der Chat-Assistent. */
    static final class Line {
        final boolean merlin;
        final String text;

        Line(boolean merlin, String text) {
            this.merlin = merlin;
            this.text = text;
        }
    }

    private static final String[] ON_ENTER = {
            null,
            "Hier verbinden wir mich mit deinem KI-Dienst. Sobald das klappt, kann ich mit dir sprechen.",
            "Jetzt suche ich mir noch ein Gehirn aus. Die Stimme habe ich ja offensichtlich schon.",
            "Hier zeigst du mir, wo dein Wissen liegt. Je mehr ich finden darf, desto weniger muss ich raten. Wenn du "
                    + "Confluence nicht benutzt, kannst du diesen Schritt einfach überspringen.",
            "Fast fertig. Ich prüfe kurz, ob ich jetzt wirklich alles kann, was ich behaupte.",
            "Und wenn dir Tippen zu lästig ist, kannst du mir auch einfach etwas erzählen."
    };

    private static final String[] WHAT_TO_DO = {
            "Starte KeePass mit dem Plugin KeePassRPC und klicke auf „Mit KeePass verbinden“. KeePass zeigt beim ersten "
                    + "Mal einen Schlüssel an; den gibst du im folgenden Fenster ein. Rechner und Port passen meistens schon.",
            "Wähle den KeePass-Eintrag deines KI-Dienstes. Im Passwortfeld steht der API-Key, im URL-Feld die "
                    + "Basis-URL. Fehlt etwas, trag es hier ein und speichere es in KeePass. Dann „Verbindung testen“.",
            "Wähle ein Chat-Modell für unsere Gespräche und ein Embedding-Modell für die Wissenssuche. Die "
                    + "Vektorlänge ermittle ich mit „Dimension ermitteln“ selbst.",
            "Trag die Adresse deines Confluence ein, dazu Benutzername und Passwort oder nur ein persönliches "
                    + "Zugriffstoken. Braucht dein Confluence ein Client-Zertifikat, wähle es aus. Dann „Verbindung "
                    + "testen“.",
            "Nichts weiter: Ich speichere alles und prüfe es. Ist etwas rot, springe oben zu dem Schritt und "
                    + "korrigiere es; danach „Erneut prüfen“.",
            "Wähle ein lokales Modell für die Spracheingabe und installiere es. Du kannst den Schritt auch "
                    + "überspringen und ihn später in den Einstellungen nachholen."
    };

    private static final String[] WHY_NEEDED = {
            "Ich lese Zugangsdaten nur aus KeePass. So stehen API-Key und Passwörter nie in einer Datei, und du "
                    + "verwaltest sie an einer Stelle.",
            "Der KI-Dienst ist mein Kopf: ohne ihn kann ich weder antworten noch Texte verstehen.",
            "Das Chat-Modell formuliert meine Antworten, das Embedding-Modell findet zu deiner Frage die passenden "
                    + "Stellen in deinem Wissen, auch wenn andere Wörter darin stehen.",
            "Aus Confluence hole ich das Wissen deiner Organisation. Ohne Confluence antworte ich nur aus dem, was "
                    + "du mir sonst gibst.",
            "Damit du nicht erst im Alltag merkst, dass etwas fehlt: Ich prüfe jeden Baustein einmal echt.",
            "Mit einem lokalen Modell verstehe ich gesprochene Sätze, ohne dass deine Stimme deinen Rechner verlässt."
    };

    private static final String[] TOPIC = {"KeePass verbinden", "KI-Dienst", "Modelle", "Confluence",
            "Abschlussprüfung", "Lokale Spracheingabe"};

    /** Die Vorstellung: beim Erststart ausführlich, bei einem späteren Aufruf als Wiedersehen. */
    String greeting(boolean returning) {
        return returning ? WELCOME_BACK : FIRST_GREETING;
    }

    /** Merlins Satz beim Betreten eines Schritts; {@code null}, wenn er dort nichts sagt. */
    String enteredStep(int step) {
        return at(ON_ENTER, step);
    }

    /** Kurze Reaktion auf einen Fehler im Schritt; nie die technische Meldung selbst (die steht im Fehlerfeld). */
    String stepFailed(int step) {
        return FAILURE;
    }

    /** „Was muss ich hier machen?“ */
    String whatToDo(int step) {
        return at(WHAT_TO_DO, step);
    }

    /** „Warum brauche ich das?“ */
    String whyNeeded(int step) {
        return at(WHY_NEEDED, step);
    }

    /**
     * Der Rahmen einer freien Frage an das Chatmodell: Merlins Rolle, der aktuelle Schritt und seine festen Hinweise,
     * damit die Antwort zur Einrichtung passt. Enthält keine Eingaben des Benutzers und keine Zugangsdaten.
     */
    String systemPrompt(int step) {
        return "Du bist Merlin, ein freundlicher, leicht verschmitzter Zauberer und der persönliche Assistent im "
                + "Enterprise AI Client. Der Benutzer richtet dich gerade ein. Antworte auf Deutsch, kurz (höchstens "
                + "drei Sätze), freundlich und konkret, ohne Markdown. Erfinde keine Menüpunkte oder Einstellungen. "
                + "Aktueller Schritt: " + at(TOPIC, step) + ". Was hier zu tun ist: " + at(WHAT_TO_DO, step)
                + " Warum: " + at(WHY_NEEDED, step);
    }

    private static String at(String[] texts, int step) {
        return step >= 0 && step < texts.length ? texts[step] : null;
    }
}

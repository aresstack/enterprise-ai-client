package com.aresstack.enterpriseai.astu.acropolis.chat;

/**
 * Tell how a user message came about. The model learns it for exactly this turn as an additional system part
 * ({@link #modelNote()}); the visible message and the history stay unchanged. A speech transcript is not verbatim: an
 * unknown word there is more likely a phonetic recognition error than a proper name worth searching for.
 */
public enum MessageOrigin {

    /** Typed or pasted: the text reads as the user means it. */
    TYPED(null),

    /** Produced by speech recognition (composer microphone), also when the user edited the transcript by hand. */
    SPEECH_TRANSCRIPT("Die aktuelle Nutzernachricht stammt aus der automatischen Spracherkennung (Diktat). Die "
            + "Abschrift kann phonetische Erkennungsfehler enthalten: ähnlich klingende, aber falsche Wörter, "
            + "zusammengezogene oder getrennte Wörter, fehlende Satzzeichen. Rekonstruiere zuerst aus dem Satzbau und "
            + "dem Gesprächsverlauf, was der Nutzer wahrscheinlich gesagt hat, und arbeite mit dieser Fassung. Ergibt "
            + "ein unbekanntes Wort zusammen mit einem ähnlich klingenden gebräuchlichen Wort einen deutlich "
            + "plausibleren Satz, nimm diese Lesart; behandle es nicht als Eigennamen und suche nicht danach. Bleibt "
            + "die Bedeutung wirklich unklar, frage kurz nach („Meinst du …?“), statt zu suchen.");

    private final String note;

    MessageOrigin(String note) {
        this.note = note;
    }

    /** Return the note for the model in this turn, or {@code null} when the origin has nothing to say. */
    public String modelNote() {
        return note;
    }
}

package com.aresstack.enterpriseai.source.api;

import java.util.Arrays;

/**
 * Fragt den Benutzer nach einem Einmalcode (OTP), wenn eine Quelle für einen Abruf eine zweite Anmeldestufe verlangt,
 * etwa ein Zwei-Faktor-Plugin vor den Dateien einer Confluence. Die Quelle beschreibt die Abfrage neutral
 * ({@link Challenge}); die Oberfläche zeigt sie und liefert die Antwort. Der Aufrufer ist ein Arbeits-Thread und
 * wartet, bis der Benutzer antwortet.
 *
 * <p>Der Code lebt nur in der {@link Answer} und wird nach dem Absenden mit {@link Answer#clear()} überschrieben; weder
 * Quelle noch Oberfläche speichern oder protokollieren ihn.
 */
public interface OneTimeCodePrompt {

    /** Zeigt die Abfrage und liefert die Antwort; nie {@code null}. */
    Answer ask(Challenge challenge);

    /** Ohne Oberfläche (Tests, Werkzeuge): jede Abfrage gilt als abgebrochen. */
    static OneTimeCodePrompt refusing() {
        return challenge -> Answer.cancel();
    }

    /** Was die Oberfläche zeigt; Texte stammen von der Seite der Quelle. */
    final class Challenge {

        private final String service;
        private final String title;
        private final String message;
        private final String error;
        private final boolean resendAvailable;

        /**
         * @param service         wofür der Code gilt (z. B. der Host der Quelle)
         * @param title           Überschrift der Abfrage
         * @param message         Hinweis der Quelle (etwa „Code per E-Mail gesendet“), darf leer sein
         * @param error           Fehlermeldung des letzten Versuchs (etwa „Code ungültig“), darf leer sein
         * @param resendAvailable ob die Quelle einen neuen Code senden kann
         */
        public Challenge(String service, String title, String message, String error, boolean resendAvailable) {
            this.service = service == null ? "" : service;
            this.title = title == null ? "" : title;
            this.message = message == null ? "" : message;
            this.error = error == null ? "" : error;
            this.resendAvailable = resendAvailable;
        }

        public String service() {
            return service;
        }

        public String title() {
            return title;
        }

        public String message() {
            return message;
        }

        public String error() {
            return error;
        }

        public boolean resendAvailable() {
            return resendAvailable;
        }

        @Override
        public String toString() {
            return "Challenge[" + service + ", " + title + "]";
        }
    }

    /** Antwort des Benutzers: Code absenden, neuen Code anfordern oder abbrechen. */
    final class Answer {

        /** Was der Benutzer gewählt hat. */
        public enum Action {
            SUBMIT, RESEND, CANCEL
        }

        private final Action action;
        private final char[] code;

        private Answer(Action action, char[] code) {
            this.action = action;
            this.code = code;
        }

        /** @param code der eingegebene Code; die Antwort übernimmt das Feld (nicht kopiert) */
        public static Answer submit(char[] code) {
            if (code == null) {
                throw new IllegalArgumentException("code must not be null");
            }
            return new Answer(Action.SUBMIT, code);
        }

        public static Answer resend() {
            return new Answer(Action.RESEND, new char[0]);
        }

        public static Answer cancel() {
            return new Answer(Action.CANCEL, new char[0]);
        }

        public Action action() {
            return action;
        }

        /** Kopie des Codes; leer außer bei {@link Action#SUBMIT}. */
        public char[] code() {
            return Arrays.copyOf(code, code.length);
        }

        /** Überschreibt den Code. */
        public void clear() {
            Arrays.fill(code, '\0');
        }

        /** Ohne Code. */
        @Override
        public String toString() {
            return "Answer[" + action + "]";
        }
    }
}

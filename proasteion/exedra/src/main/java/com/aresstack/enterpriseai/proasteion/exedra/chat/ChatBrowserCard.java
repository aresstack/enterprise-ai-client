package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.application.browser.WebExplorationProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;

/**
 * The browser card of a chat turn (#141): one activity bubble that shows the turn's web exploration in place and
 * bursts into its balance, e.g. "Browserrecherche abgeschlossen · 14 Seiten besucht · 37 Links geprüft · 6 relevante
 * Seiten verwendet". It only renders {@link WebExplorationProgress}; the counting happens in the exploration run.
 *
 * <p>UI thread only.
 */
final class ChatBrowserCard {

    static final String KIND = "search";
    static final String TITLE = "Browserrecherche";
    static final String WORKING = "Browser arbeitet …";

    private final ChatShellModel model;
    private final String key;
    private WebExplorationProgress progress = WebExplorationProgress.none();
    private boolean open;
    private boolean finished;

    ChatBrowserCard(ChatShellModel model, String key) {
        this.model = model;
        this.key = key;
    }

    String key() {
        return key;
    }

    /** Shows the new state; the card appears with the first browser call of the turn. */
    void update(WebExplorationProgress changed) {
        if (finished || changed == null) {
            return;
        }
        progress = changed;
        if (!progress.isActive()) {
            return;
        }
        if (!open || model.isActivityRunning(key)) {
            open = true;
            model.startActivity(key, TITLE, KIND, body(progress));
        }
    }

    /** Bursts with the balance of {@code last}; a turn that never used the browser shows no card. */
    void finish(WebExplorationProgress last, boolean cancelled) {
        if (finished) {
            return;
        }
        finished = true;
        if (last != null) {
            progress = last;
        }
        if (!open) {
            return;
        }
        model.finishActivity(key, true, summary(progress, cancelled));
    }

    /** "Browserrecherche abgeschlossen · 14 Seiten besucht · 37 Links geprüft · 6 relevante Seiten verwendet". */
    static String summary(WebExplorationProgress progress, boolean cancelled) {
        StringBuilder text = new StringBuilder(cancelled ? "Browserrecherche abgebrochen" : "Browserrecherche abgeschlossen")
                .append(" · ").append(pages(progress.visited())).append(" besucht");
        if (progress.examined() > 0) {
            text.append(" · ").append(progress.examined()).append(progress.examined() == 1 ? " Link" : " Links")
                    .append(" geprüft");
        }
        text.append(" · ").append(progress.relevant())
                .append(progress.relevant() == 1 ? " relevante Seite verwendet" : " relevante Seiten verwendet");
        if (!progress.challenge().isEmpty()) {
            text.append(" · Browser bleibt offen (")
                    .append("login".equals(progress.challenge()) ? "Anmeldung" : "Prüfseite").append(')');
        }
        return text.toString();
    }

    static String body(WebExplorationProgress progress) {
        StringBuilder text = new StringBuilder(countLine(progress));
        for (WebExplorationProgress.Step step : progress.steps()) {
            text.append('\n').append(line(step));
        }
        if (progress.isRunning()) {
            text.append('\n').append(WORKING);
        }
        return text.toString();
    }

    /** "3 Seiten besucht · 37 Links geprüft · 2 relevant", plus skipped and failed when there are any. */
    static String countLine(WebExplorationProgress progress) {
        StringBuilder text = new StringBuilder(pages(progress.visited())).append(" besucht · ")
                .append(progress.examined()).append(progress.examined() == 1 ? " Link" : " Links").append(" geprüft · ")
                .append(progress.relevant()).append(" relevant");
        if (progress.skipped() > 0) {
            text.append(" · ").append(progress.skipped()).append(" übersprungen");
        }
        if (progress.failed() > 0) {
            text.append(" · ").append(progress.failed()).append(" fehlgeschlagen");
        }
        return text.toString();
    }

    static String line(WebExplorationProgress.Step step) {
        String label = oneLine(step.label());
        switch (step.kind()) {
            case OPENED:
                return "„" + label + "“ geöffnet";
            case BACK:
                return "Zurück zu „" + label + "“";
            case ACTED:
                return label;
            case CHALLENGE:
                return "login".equals(label) ? "Anmeldung nötig: bitte im Firefox anmelden"
                        : "Prüfseite: bitte im Firefox lösen";
            case LINKS:
                return step.count() + (step.count() == 1 ? " Link" : " Links")
                        + (label.isEmpty() ? " erkannt" : " passend zu „" + label + "“");
            case SKIPPED:
                return "„" + label + "“ übersprungen (nicht freigegeben)";
            case FAILED:
                return "Fehlgeschlagen: " + label;
            case MARKED_RELEVANT:
                return "„" + label + "“ ist relevant";
            case MARKED_IRRELEVANT:
                return "„" + label + "“ nicht relevant";
            case CLOSED:
                return "Browser geschlossen";
            default:
                return label;
        }
    }

    private static String pages(int count) {
        return count + (count == 1 ? " Seite" : " Seiten");
    }

    private static String oneLine(String value) {
        String text = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ').trim();
        return text.length() <= 100 ? text : text.substring(0, 100) + " …";
    }
}

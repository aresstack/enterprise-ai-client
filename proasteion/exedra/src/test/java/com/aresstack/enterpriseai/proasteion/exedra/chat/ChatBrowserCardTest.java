package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.application.browser.WebExplorationProgress;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** #141: the browser card renders the exploration state; it counts nothing itself. */
public class ChatBrowserCardTest {

    private static WebExplorationProgress progress(boolean running) {
        return new WebExplorationProgress(true, running, 40, 37, 14, 1, 2, 6, Arrays.asList(
                new WebExplorationProgress.Step(WebExplorationProgress.StepKind.OPENED, "Dienstreisen", 0),
                new WebExplorationProgress.Step(WebExplorationProgress.StepKind.LINKS, "reise", 9),
                new WebExplorationProgress.Step(WebExplorationProgress.StepKind.SKIPPED, "extern.example", 0)));
    }

    @Test
    public void summaryNamesVisitedExaminedAndUsedPages() {
        assertEquals("Browserrecherche abgeschlossen · 14 Seiten besucht · 37 Links geprüft · 6 relevante Seiten "
                + "verwendet", ChatBrowserCard.summary(progress(false), false));
        assertTrue(ChatBrowserCard.summary(progress(false), true).startsWith("Browserrecherche abgebrochen"));
    }

    @Test
    public void bodyShowsCountsStepsAndTheRunningCall() {
        String body = ChatBrowserCard.body(progress(true));

        assertEquals("14 Seiten besucht · 37 Links geprüft · 6 relevant · 1 übersprungen · 2 fehlgeschlagen\n"
                + "„Dienstreisen“ geöffnet\n"
                + "9 Links passend zu „reise“\n"
                + "„extern.example“ übersprungen (nicht freigegeben)\n"
                + ChatBrowserCard.WORKING, body);
    }

    @Test
    public void pendingChallengeIsShownAndNamedInTheSummary() {
        WebExplorationProgress login = new WebExplorationProgress(true, false, 0, 0, 1, 0, 0, 0, Arrays.asList(
                new WebExplorationProgress.Step(WebExplorationProgress.StepKind.CHALLENGE, "login", 0)), "login");

        assertTrue(ChatBrowserCard.body(login), ChatBrowserCard.body(login)
                .contains("Anmeldung nötig: bitte im Firefox anmelden"));
        assertTrue(ChatBrowserCard.summary(login, false).endsWith(" · Browser bleibt offen (Anmeldung)"));
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.searchrun.api.FunnelPhase;
import com.aresstack.enterpriseai.searchrun.api.FunnelResource;
import com.aresstack.enterpriseai.searchrun.api.KnowledgeFunnel;
import com.aresstack.enterpriseai.searchrun.api.SearchRunId;
import org.junit.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The research card shows the funnel of #155 live and as its final balance (#156). */
public class ChatResearchCardFunnelTest {

    private static final SearchRunId ID = SearchRunId.random();

    private final ChatShellModel model = new ChatShellModel(() -> 0L);
    private final ChatResearchCard card = new ChatResearchCard(model, "turn-1");

    private static List<FunnelResource> pages(int count) {
        List<FunnelResource> pages = new ArrayList<FunnelResource>();
        for (int i = 0; i < count; i++) {
            pages.add(new FunnelResource("wiki", "wiki:" + i));
        }
        return pages;
    }

    private static KnowledgeFunnel funnel(int discovered, int examined, int relevant, int used, FunnelPhase phase,
                                          boolean complete) {
        return new KnowledgeFunnel(ID, Instant.EPOCH, Collections.<SearchRunId>emptyList(), discovered,
                pages(examined), pages(relevant), pages(used), phase, complete);
    }

    @Test
    public void countsGrowLiveInOneLine() {
        card.open();
        card.searchStarted("Vault", 1, 1);

        card.funnel(funnel(60, 12, 2, 0, FunnelPhase.EVIDENCE, false));
        assertTrue(card.body(), card.body().startsWith("12 Seiten geprüft · 2 relevant\n"));
        card.funnel(funnel(60, 57, 8, 0, FunnelPhase.EVIDENCE, false));

        assertTrue(card.body(), card.body().startsWith("57 Seiten geprüft · 8 relevant\n"));
        assertFalse("the provider total (discovered) is never shown as examined", card.body().contains("60"));
    }

    @Test
    public void finishedRunShowsTheCompactBalance() {
        card.open();
        card.searchStarted("Vault", 1, 1);
        card.funnel(funnel(60, 57, 8, 3, FunnelPhase.DONE, true));

        assertEquals("Recherche abgeschlossen · 57 Seiten geprüft · 8 relevante Quellen · 3 verwendet", card.summary());
    }

    @Test
    public void cancelledAndPartialRunsKeepTheirHonestCounts() {
        card.open();
        card.searchStarted("Vault", 1, 1);
        card.funnel(funnel(40, 31, 5, 0, FunnelPhase.EVIDENCE, false));
        card.cancelling();
        assertEquals("Recherche abgebrochen · 31 Seiten geprüft · 5 relevante Quellen · 0 verwendet", card.summary());

        ChatResearchCard partial = new ChatResearchCard(model, "turn-2");
        partial.open();
        partial.searchStarted("Vault", 1, 1);
        partial.funnel(funnel(50, 44, 6, 2, FunnelPhase.DONE, false));
        assertEquals("Recherche teilweise abgeschlossen · 44 Seiten geprüft · 6 relevante Quellen · 2 verwendet",
                partial.summary());
    }

    @Test
    public void barIsWorkProgressNotSelectivity() {
        card.open();
        card.searchStarted("Vault", 1, 2);
        card.funnel(funnel(10, 10, 1, 0, FunnelPhase.RETRIEVAL, false));
        String early = bar(card.body());
        card.funnel(funnel(10, 10, 9, 0, FunnelPhase.RETRIEVAL, false));

        assertEquals("more relevant pages are no progress", early, bar(card.body()));
        card.funnel(funnel(10, 10, 9, 0, FunnelPhase.EVIDENCE, false));
        assertEquals("[[bar:3/10]]", bar(card.body()));
        card.searchStarted("Vault Betrieb", 2, 2);
        card.funnel(funnel(10, 10, 9, 0, FunnelPhase.DISCOVERY, false));
        assertEquals("a second search moves on, never back", "[[bar:5/10]]", bar(card.body()));
        card.contextReady(3);
        assertEquals("[[bar:10/10]]", bar(card.body()));
    }

    @Test
    public void relevanceIsShownOnceNotAsASecondCounter() {
        card.open();
        card.searchStarted("Vault", 1, 1);
        card.ranked(14, 2);

        assertFalse(card.body(), card.body().contains("geeignete"));
    }

    private static String bar(String body) {
        return body.substring(body.indexOf("[[bar:"));
    }
}

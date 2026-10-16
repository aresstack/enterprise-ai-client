package com.aresstack.enterpriseai.proasteion.exedra.ui.memory;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MemoryManagerPanelTest {

    private static final class FakeBrowser implements MemoryBrowser {
        final List<MemoryEntry> entries = new ArrayList<MemoryEntry>();
        String lastQuery;
        String refusal;

        @Override
        public String title() {
            return "Projekt";
        }

        @Override
        public String description() {
            return "Erinnerungen des Projekts.";
        }

        @Override
        public List<MemoryEntry> list(String query) {
            lastQuery = query;
            return new ArrayList<MemoryEntry>(entries);
        }

        @Override
        public String delete(MemoryEntry entry) {
            if (refusal != null) {
                return refusal;
            }
            entries.remove(entry);
            return null;
        }
    }

    @Test
    public void listsAndDeletes() {
        FakeBrowser browser = new FakeBrowser();
        MemoryEntry first = new MemoryEntry("M-0001", 1, "Releases nur dienstags", "2026-10-16");
        browser.entries.add(first);
        browser.entries.add(new MemoryEntry("M-0002", 1, "Kunde heißt ACME", "2026-10-16"));
        MemoryManagerPanel panel = new MemoryManagerPanel(browser);
        assertEquals(2, panel.rowCount());

        assertNull(panel.deleteConfirmed(first));
        assertEquals(1, panel.rowCount());
        assertTrue(panel.statusText(), panel.statusText().startsWith("M-0001 gelöscht."));
    }

    @Test
    public void showsWhyNothingWasDeleted() {
        FakeBrowser browser = new FakeBrowser();
        MemoryEntry entry = new MemoryEntry("M-0003", 2, "x", "");
        browser.entries.add(entry);
        browser.refusal = "Nichts gelöscht: M-0003 wurde inzwischen geändert oder gelöscht.";
        MemoryManagerPanel panel = new MemoryManagerPanel(browser);

        assertEquals(browser.refusal, panel.deleteConfirmed(entry));
        assertEquals(browser.refusal, panel.statusText());
        assertEquals(1, panel.rowCount());
    }

    @Test
    public void emptyListSaysSo() {
        MemoryManagerPanel panel = new MemoryManagerPanel(new FakeBrowser());
        assertEquals(MemoryManagerPanel.EMPTY_TEXT, panel.statusText());
    }
}

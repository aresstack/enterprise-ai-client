package com.aresstack.enterpriseai.app.ui.sidebar;

import org.junit.Test;

import javax.swing.AbstractButton;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Die ausfaltende Reiterleiste (askai arch): ein Eintrag je Seite, Auffalten, Überlauf-Pfeile je Seite. */
public class SidebarTabRibbonTest {

    private static List<AbstractButton> entriesOf(SidebarTabRibbon ribbon) {
        List<AbstractButton> entries = new ArrayList<AbstractButton>();
        for (Component child : ribbon.contentForTest().getComponents()) {
            if (child instanceof AbstractButton) {
                entries.add((AbstractButton) child);
            }
        }
        return entries;
    }

    @Test
    public void oneEntryPerTabAndClicksReportTheTitle() {
        SidebarTabRibbon ribbon = new SidebarTabRibbon();
        final List<String> selected = new ArrayList<String>();
        ribbon.setListener(selected::add);
        ribbon.setTabs(Arrays.asList("Chats", "Wissensquellen"), "Chats");

        List<AbstractButton> entries = entriesOf(ribbon);
        assertEquals(2, entries.size());
        assertEquals("Chats", entries.get(0).getText());
        assertEquals("Wissensquellen", entries.get(1).getText());

        entries.get(1).doClick();
        assertEquals(Arrays.asList("Wissensquellen"), selected);
    }

    @Test
    public void theRibbonStartsFoldedAndUnfoldsOnOpen() {
        SidebarTabRibbon ribbon = new SidebarTabRibbon();
        ribbon.setTabs(Arrays.asList("Chats"), "Chats");
        assertFalse("beginnt eingefaltet", ribbon.isOpen());

        ribbon.open();
        ribbon.finishAnimation();
        assertTrue(ribbon.isOpen());

        ribbon.close();
        ribbon.finishAnimation();
        assertFalse(ribbon.isOpen());
    }

    @Test
    public void overflowShowsTheArrowExactlyOnTheSideWithMoreEntries() {
        SidebarTabRibbon ribbon = new SidebarTabRibbon();
        ribbon.setTabs(Arrays.asList("Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta"), "Alpha");
        ribbon.open();
        ribbon.finishAnimation();
        ribbon.setSize(120, 28); // viel schmaler als die Einträge
        ribbon.doLayout();

        assertFalse("links ist noch nichts verborgen", ribbon.scrollLeftForTest().isVisible());
        assertTrue("rechts folgen weitere Einträge", ribbon.scrollRightForTest().isVisible());

        for (int i = 0; i < 200; i++) {
            ribbon.scrollRightForTest().doClick();
            ribbon.doLayout();
        }
        assertTrue(ribbon.scrollLeftForTest().isVisible());
        assertFalse(ribbon.scrollRightForTest().isVisible());
        assertTrue("Versatz auf den Inhalt begrenzt", ribbon.scrollOffsetForTest() > 0);
    }

    @Test
    public void aWideEnoughRibbonNeedsNoArrows() {
        SidebarTabRibbon ribbon = new SidebarTabRibbon();
        ribbon.setTabs(Arrays.asList("Chats"), "Chats");
        ribbon.open();
        ribbon.finishAnimation();
        ribbon.setSize(600, 28);
        ribbon.doLayout();
        assertFalse(ribbon.scrollLeftForTest().isVisible());
        assertFalse(ribbon.scrollRightForTest().isVisible());
        assertEquals(0, ribbon.scrollOffsetForTest());
    }
}

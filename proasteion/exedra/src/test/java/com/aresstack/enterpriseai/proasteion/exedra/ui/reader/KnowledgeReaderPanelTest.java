package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class KnowledgeReaderPanelTest {

    @Test
    public void longPageStaysCompleteWhenTheReaderGetsNarrower() throws Exception {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final ReaderLink link = new ReaderLink("wiki", "wiki:page/1", "Roadmap", "", "wiki:page/1");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            reader.open(link, false);
            reader.showMarkdown(link.key(), longPage());
            reader.setSize(1400, 700);
            layout(reader);
            // Schmaler (Fenster verkleinert, Drawer geöffnet): die Zeilen brechen öfter um.
            reader.setSize(600, 700);
            layout(reader);
            assertNothingCut(reader);
        });
    }

    private static void assertNothingCut(KnowledgeReaderPanel reader) {
        List<JTextPane> panes = new ArrayList<JTextPane>();
        JScrollPane scroll = find(reader);
        assertNotNull(scroll);
        Component page = scroll.getViewport().getView();
        collect(page, panes);
        assertFalse(panes.isEmpty());
        for (JTextPane pane : panes) {
            View root = pane.getUI().getRootView(pane);
            root.setSize(pane.getWidth(), Integer.MAX_VALUE);
            int needed = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS));
            assertTrue("Absatz abgeschnitten: " + pane.getHeight() + " < " + needed, pane.getHeight() >= needed);
            int bottom = javax.swing.SwingUtilities.convertPoint(pane, 0, pane.getHeight(), page).y;
            assertTrue("Text unterhalb der Seite: " + bottom + " > " + page.getHeight(), bottom <= page.getHeight());
            Component content = ((Container) page).getComponent(0);
            int contentBottom = javax.swing.SwingUtilities.convertPoint(pane, 0, pane.getHeight(), content).y;
            assertTrue("Text unterhalb des Inhalts: " + contentBottom + " > " + content.getHeight(),
                    contentBottom <= content.getHeight());
        }
    }

    private static String longPage() {
        StringBuilder text = new StringBuilder("# Roadmap\n\n## Meilensteine\n\nName | Inhalt | Start | Ende\n");
        for (int i = 0; i < 40; i++) {
            text.append("MS").append(i).append(" | Betriebsübergabe der Container-Infrastruktur: alle Dienste sind ")
                    .append("bereitgestellt und werden vom Team betrieben, die Bereitstellung wird koordiniert. | ")
                    .append("01.02.27 | 16.09.27\n");
        }
        text.append("Letzte Zeile der Tabelle\n\n## Liefergegenstände\n\nKey | Summary | Status\n");
        return text.toString();
    }

    /** Wie validate(), aber ohne Bildschirm (headless hat die Komponente keinen Peer): von außen nach innen. */
    private static void layout(Component component) {
        if (component instanceof Container) {
            ((Container) component).doLayout();
            for (Component child : ((Container) component).getComponents()) {
                layout(child);
            }
        }
    }

    private static void collect(Component component, List<JTextPane> panes) {
        if (component instanceof JTextPane) {
            panes.add((JTextPane) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, panes);
            }
        }
    }

    private static JScrollPane find(Component component) {
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JScrollPane found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

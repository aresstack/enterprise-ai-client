package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JScrollPane;
import javax.swing.JEditorPane;
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

    @Test
    public void readingViewWithImagesStaysCompleteWhenTheReaderGetsNarrower() throws Exception {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final ReaderLink link = new ReaderLink("wiki", "wiki:page/2", "Roadmap", "", "wiki:page/2");
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            reader.open(link, false);
            reader.showHtml(link.key(), longHtml(), "https://confluence.invalid/", java.util.Collections.emptyList(),
                    (image, done) -> done.accept(null));
            reader.setSize(1400, 700);
            layout(reader);
            reader.setSize(600, 700);
            layout(reader);
            assertNothingCut(reader);
        });
    }

    @Test
    public void wideTableWithALongAddressIsNeverCutOnTheRight() throws Exception {
        final KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), url -> {
        }, (code, width) -> null);
        final ReaderLink link = new ReaderLink("wiki", "wiki:page/3", "Tasks", "", "wiki:page/3");
        final String url = "https://console.example.invalid/k8s/ns/devsecops/tekton.dev~v1beta1~Pipeline/update";
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            reader.open(link, false);
            reader.showHtml(link.key(), "<table><tr><td>Deployment</td><td>Pipeline starten, Version angeben</td>"
                            + "<td><a href=\"" + url + "\">" + url + "</a></td></tr></table><p>Ende des Satzes.</p>",
                    "", java.util.Collections.emptyList(), (image, done) -> done.accept(null));
            reader.setSize(500, 700);
            layout(reader);
            layout(reader);
            JScrollPane scroll = find(reader);
            List<JEditorPane> panes = new ArrayList<JEditorPane>();
            collect(scroll.getViewport().getView(), panes);
            JEditorPane pane = panes.get(0);
            // Die Adresse bricht innerhalb der Zelle um: kein Zeichen steht rechts außerhalb der Ansicht ...
            for (int offset = 0; offset < pane.getDocument().getLength(); offset++) {
                java.awt.Rectangle caret;
                try {
                    caret = pane.modelToView(offset);
                } catch (javax.swing.text.BadLocationException e) {
                    throw new AssertionError(e);
                }
                if (caret != null) {
                    assertTrue("Zeichen " + offset + " bei x=" + caret.x + " rechts außerhalb von " + pane.getWidth(),
                            caret.x <= pane.getWidth());
                }
            }
            // ... und die Ansicht ist nicht breiter als der Lesebereich.
            assertTrue("breiter als der Lesebereich: " + pane.getWidth(), pane.getWidth() <= 500);
            assertNothingCut(reader);
        });
    }

    private static String longHtml() {
        StringBuilder html = new StringBuilder("<h1>Roadmap</h1><h2>Meilensteine</h2>");
        for (int i = 0; i < 40; i++) {
            html.append("<p>MS").append(i).append(" Betriebsübergabe der Container-Infrastruktur: alle Dienste sind ")
                    .append("bereitgestellt und werden vom Team betrieben, die Bereitstellung wird koordiniert.</p>");
        }
        return html.append("<p>Letzte Zeile</p>").toString();
    }

    private static void assertNothingCut(KnowledgeReaderPanel reader) {
        List<JEditorPane> panes = new ArrayList<JEditorPane>();
        JScrollPane scroll = find(reader);
        assertNotNull(scroll);
        Component page = scroll.getViewport().getView();
        collect(page, panes);
        assertFalse(panes.isEmpty());
        for (JEditorPane pane : panes) {
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

    private static void collect(Component component, List<JEditorPane> panes) {
        if (component instanceof JEditorPane) {
            panes.add((JEditorPane) component);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collect(child, panes);
            }
        }
    }

    private static JScrollPane find(Component component) {
        if (!component.isVisible()) {
            return null; // z. B. der ausgeblendete Navigationsbanner über der Seite
        }
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

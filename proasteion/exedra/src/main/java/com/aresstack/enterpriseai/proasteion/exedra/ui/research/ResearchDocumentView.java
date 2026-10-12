package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownMessageView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.MarkdownTheme;
import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Der Drawer-Reiter „Dokument“: das Recherche-Dokument des Projekts als gerendertes Markdown. Jeder Beleg {@code [n]}
 * ist ein Link auf die Quelle {@code n}; ein Klick öffnet sie im Lesebereich (askai arch {@code DocumentPageView}:
 * typisierte Belege statt unsichtbarem Text). Nur auf dem EDT.
 */
public final class ResearchDocumentView extends JPanel {

    /** Schema der Beleg-Links; geht nie an den Browser. */
    static final String SOURCE_SCHEME = "research-source:";
    private static final Pattern REFERENCE = Pattern.compile("(?<![\\[\\w])\\[(\\d{1,3})\\](?![\\](])");
    private static final String EMPTY = "Noch kein Dokument. Wähle die Phase „Dokument“ und schreibe es unten "
            + "mit Play.";

    private final MarkdownMessageView markdownView;
    private final JLabel status = new JLabel(" ");
    private IntConsumer sourceHandler;
    private String markdown = "";

    public ResearchDocumentView() {
        super(new BorderLayout());
        setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        setOpaque(true);
        markdownView = new MarkdownMessageView(
                MarkdownTheme.forBubble(ResearchUiPalette.LIGHT_CONTROL_BG, ComicPalette.defaultPalette().getInk()),
                new DesktopLinkOpener() {
                    @Override
                    public void open(String url) {
                        openLink(url);
                    }
                }, CachingMermaidImageRenderer.forChat());
        Page page = new Page();
        page.setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        page.setOpaque(true);
        page.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));
        page.add(markdownView, BorderLayout.NORTH);
        status.setFont(ResearchUiTypography.regular(12f));
        status.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        status.setBorder(BorderFactory.createEmptyBorder(6, 12, 4, 12));
        add(status, BorderLayout.NORTH);
        add(new ComicScrollPane(page, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        render("", "");
    }

    /** Die Play-Aktion des Reiters (unten mittig). */
    public void setPlayBar(ResearchPlayBar bar) {
        add(bar, BorderLayout.SOUTH);
        revalidate();
    }

    /** Wer eine Quelle öffnet (Nummer im Dokument). */
    public void setSourceHandler(IntConsumer handler) {
        this.sourceHandler = handler;
    }

    /** Neue Fassung; {@code statusLine} sagt, wie weit das Dokument ist. */
    public void render(String documentMarkdown, String statusLine) {
        markdown = documentMarkdown == null ? "" : documentMarkdown;
        markdownView.setMarkdown(markdown.trim().isEmpty() ? EMPTY : linked(markdown));
        status.setText(statusLine == null || statusLine.trim().isEmpty() ? " " : statusLine);
        revalidate();
        repaint();
    }

    /** Das angezeigte Markdown ohne Beleg-Links (Tests). */
    public String markdown() {
        return markdown;
    }

    /** Die angezeigte Statuszeile (Tests). */
    public String statusLine() {
        return status.getText().trim();
    }

    /** Öffnet die Quelle {@code number} wie ein Klick auf ihren Beleg. */
    public void openSource(int number) {
        if (sourceHandler != null && number > 0) {
            sourceHandler.accept(number);
        }
    }

    private void openLink(String url) {
        if (url != null && url.startsWith(SOURCE_SCHEME)) {
            try {
                openSource(Integer.parseInt(url.substring(SOURCE_SCHEME.length()).trim()));
            } catch (NumberFormatException ignored) {
                // kein Beleg
            }
            return;
        }
        DesktopLinkOpener.systemDefault().open(url);
    }

    /** {@code [3]} → {@code [\[3\]](research-source:3)}; Markdown-Links bleiben unberührt. */
    static String linked(String text) {
        Matcher matcher = REFERENCE.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement("[\\[" + matcher.group(1) + "\\]]("
                    + SOURCE_SCHEME + matcher.group(1) + ")"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Gibt dem Markdown die Höhe, die es bei der aktuellen Breite braucht (wie der Lesebereich). */
    private static final class Page extends JPanel implements Scrollable {
        Page() {
            super(new BorderLayout());
        }

        @Override
        public Dimension getPreferredSize() {
            WidthAwareHeight view = view();
            if (view != null && getParent() != null && getParent().getWidth() > 0) {
                Insets insets = getInsets();
                int width = getParent().getWidth();
                return new Dimension(width, view.preferredHeightForWidth(width - insets.left - insets.right)
                        + insets.top + insets.bottom);
            }
            return super.getPreferredSize();
        }

        @Override
        public void doLayout() {
            WidthAwareHeight view = view();
            if (view == null) {
                super.doLayout();
                return;
            }
            Insets insets = getInsets();
            int inner = Math.max(0, getWidth() - insets.left - insets.right);
            getComponent(0).setBounds(insets.left, insets.top, inner, view.preferredHeightForWidth(inner));
        }

        private WidthAwareHeight view() {
            return getComponentCount() == 1 && getComponent(0) instanceof WidthAwareHeight
                    ? (WidthAwareHeight) getComponent(0) : null;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 18;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(18, visible.height - 18);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}

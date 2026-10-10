package com.aresstack.enterpriseai.app.ui.markdown;

import com.aresstack.enterpriseai.ui.comic.bubble.WidthAwareHeight;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Insets;

/**
 * Wiederverwendbare Assistentennachricht mit gedrosselten Streaming-Aktualisierungen (aus askai-java8
 * {@code MarkdownMessageView}).
 *
 * <p>Während des Streamings wird nicht nach jedem Delta neu gerendert, sondern höchstens alle
 * {@value #MIN_STREAM_RENDER_DELAY_MILLIS} ms; dauert ein Rendering länger, wächst die Pause auf das Dreifache der
 * gemessenen Renderzeit (bis {@value #MAX_STREAM_RENDER_DELAY_MILLIS} ms). So bleibt die Oberfläche auch bei langen
 * Antworten und dichten Deltas flüssig, zeigt den Text aber laufend an. Mermaid-Diagramme werden erst nach dem
 * Ende des Streamings gerendert.</p>
 */
public final class MarkdownMessageView extends JPanel implements WidthAwareHeight {

    private static final int MIN_STREAM_RENDER_DELAY_MILLIS = 90;
    private static final int MAX_STREAM_RENDER_DELAY_MILLIS = 2000;
    private static final int RENDER_COST_FACTOR = 3;

    private final StringBuilder markdown = new StringBuilder();
    private final FlexmarkSwingRenderer renderer;
    private final Timer renderTimer;
    private boolean streaming;

    public MarkdownMessageView(MarkdownTheme theme, DesktopLinkOpener linkOpener,
                               MermaidImageRenderer mermaidImageRenderer) {
        if (theme == null || linkOpener == null || mermaidImageRenderer == null) {
            throw new IllegalArgumentException("theme, linkOpener and mermaidImageRenderer must not be null");
        }
        this.renderer = new FlexmarkSwingRenderer(theme, linkOpener, mermaidImageRenderer);
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        this.renderTimer = new Timer(MIN_STREAM_RENDER_DELAY_MILLIS, event -> renderThrottled());
        this.renderTimer.setRepeats(false);
        renderNow();
    }

    public void setMarkdown(String value) {
        assertEventDispatchThread();
        streaming = false;
        renderTimer.stop();
        markdown.setLength(0);
        if (value != null) {
            markdown.append(value);
        }
        renderNow();
    }

    public void startStreaming() {
        assertEventDispatchThread();
        streaming = true;
        markdown.setLength(0);
        renderNow();
    }

    public void appendMarkdownDelta(String delta) {
        assertEventDispatchThread();
        if (delta == null || delta.isEmpty()) {
            return;
        }
        markdown.append(delta);
        if (!streaming) {
            renderNow();
        } else if (!renderTimer.isRunning()) {
            renderTimer.start(); // läuft der Timer schon, erfasst das anstehende Rendering auch dieses Delta
        }
    }

    public void finishStreaming() {
        assertEventDispatchThread();
        streaming = false;
        renderTimer.stop();
        renderNow();
    }

    public boolean isStreaming() {
        return streaming;
    }

    public String getMarkdown() {
        return markdown.toString();
    }

    /**
     * Berechnet deterministisch die Höhe dieser Ansicht bei der gegebenen Breite, damit ein Wirt, der die Zielbreite
     * kennt (eine Sprechblase/Zeile), schon bei der ersten Messung die richtige Höhe bekommt — ohne auf einen
     * späteren Swing-Layoutdurchlauf zu warten, der einen umbrochenen Absatz korrigiert.
     */
    @Override
    public int preferredHeightForWidth(int width) {
        Insets insets = getInsets();
        Component content = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.CENTER);
        int contentHeight = MarkdownHeights.forWidth(content, width - insets.left - insets.right);
        return contentHeight + insets.top + insets.bottom;
    }

    private void renderThrottled() {
        long started = System.nanoTime();
        renderNow();
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000L;
        long delay = Math.max(MIN_STREAM_RENDER_DELAY_MILLIS,
                Math.min(MAX_STREAM_RENDER_DELAY_MILLIS, elapsedMillis * RENDER_COST_FACTOR));
        renderTimer.setInitialDelay((int) delay);
    }

    private void renderNow() {
        removeAll();
        boolean complete = !streaming;
        String normalized = MarkdownResponseNormalizer.normalize(markdown.toString(), complete);
        add(renderer.render(normalized, complete), BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static void assertEventDispatchThread() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("MarkdownMessageView must be updated on the Swing EDT.");
        }
    }
}

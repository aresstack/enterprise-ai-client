package com.aresstack.enterpriseai.app.ui.sidebar;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;

import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

/**
 * Die aufklappende Reiterleiste neben dem Hamburger (askai-java8 arch): je Drawer-Seite ein Eintrag im
 * Composer-Stil. Sie schiebt sich nicht herein, sie ENTFALTET sich: die sichtbare Breite wächst animiert
 * nach rechts und gibt die Einträge an Ort und Stelle frei. Passen die Einträge nicht, erscheint ‹ bzw. ›
 * genau auf der Seite, auf der weitere folgen; das Überfahren eines Pfeils rollt. Die Leiste kennt keine
 * Öffnungsregel — die Arbeitsfläche öffnet sie beim Überfahren des Hamburgers und schließt sie wieder.
 */
public final class SidebarTabRibbon extends JPanel {

    /** Der Benutzer hat einen Eintrag gewählt. */
    public interface Listener {
        void tabSelected(String title);
    }

    private static final int ARROW_WIDTH = 18;
    private static final int RIBBON_HEIGHT = 28;
    private static final float ANIM_STEP = 0.18f;
    private static final int ANIM_INTERVAL_MS = 15;
    private static final int SCROLL_STEP_PX = 10;
    private static final int SCROLL_INTERVAL_MS = 30;

    private final JPanel content = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
    private final JPanel viewport = new JPanel(null);
    private final JButton scrollLeft = ComposerButton.ribbonArrow(true, "Nach links rollen");
    private final JButton scrollRight = ComposerButton.ribbonArrow(false, "Nach rechts rollen");
    private final Timer animator;
    private final Timer leftScroller;
    private final Timer rightScroller;

    private float progress; // 0 = eingefaltet, 1 = ganz entfaltet
    private boolean expanding;
    private int scrollOffset;
    private Listener listener;

    public SidebarTabRibbon() {
        super(null);
        setOpaque(false);
        content.setOpaque(false);
        viewport.setOpaque(false);
        viewport.add(content);
        add(scrollLeft);
        add(viewport);
        add(scrollRight);
        scrollLeft.setVisible(false);
        scrollRight.setVisible(false);

        animator = new Timer(ANIM_INTERVAL_MS, event -> stepAnimation());
        leftScroller = new Timer(SCROLL_INTERVAL_MS, event -> scrollBy(-SCROLL_STEP_PX));
        rightScroller = new Timer(SCROLL_INTERVAL_MS, event -> scrollBy(SCROLL_STEP_PX));
        wireArrow(scrollLeft, leftScroller, -SCROLL_STEP_PX);
        wireArrow(scrollRight, rightScroller, SCROLL_STEP_PX);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Baut die Einträge neu (der aktive ist hervorgehoben); der Entfaltungszustand bleibt. */
    public void setTabs(List<String> titles, String activeTitle) {
        content.removeAll();
        for (final String title : titles) {
            JButton entry = ComposerButton.ribbonEntry(title, title.equals(activeTitle));
            entry.addActionListener(event -> {
                if (listener != null) {
                    listener.tabSelected(title);
                }
            });
            content.add(entry);
        }
        revalidate();
        repaint();
    }

    /** Nach rechts entfalten (animiert). */
    public void open() {
        expanding = true;
        animator.start();
    }

    /** Wieder einfalten (animiert). */
    public void close() {
        expanding = false;
        animator.start();
    }

    public boolean isOpen() {
        return progress > 0f;
    }

    private void stepAnimation() {
        progress += expanding ? ANIM_STEP : -ANIM_STEP;
        if (progress <= 0f) {
            progress = 0f;
            animator.stop();
        } else if (progress >= 1f) {
            progress = 1f;
            animator.stop();
        }
        revalidate();
        repaint();
    }

    /** Springt an das Ende der Animation — für Tests und Screenshots ohne Timer. */
    public void finishAnimation() {
        animator.stop();
        progress = expanding ? 1f : 0f;
        revalidate();
        repaint();
    }

    private void wireArrow(JButton arrow, final Timer scroller, final int clickStep) {
        arrow.addActionListener(event -> scrollBy(clickStep));
        arrow.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                scroller.start();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                scroller.stop();
            }
        });
    }

    private void scrollBy(int delta) {
        scrollOffset += delta;
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------ Layout von Hand

    @Override
    public Dimension getPreferredSize() {
        // Nur der entfaltete Anteil wird beansprucht: eingefaltet nimmt die Leiste nichts.
        int fullWidth = content.getPreferredSize().width + 2 * ARROW_WIDTH;
        return new Dimension(Math.round(progress * fullWidth), RIBBON_HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return new Dimension(0, RIBBON_HEIGHT);
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        int contentWidth = content.getPreferredSize().width;
        int visibleWidth = Math.round(progress * Math.min(contentWidth + 2 * ARROW_WIDTH, width));

        // Ein Pfeil erscheint nur auf der Seite, auf der noch Einträge folgen. Rechts entscheidet der Sichtbereich
        // OHNE den rechten Pfeil: sonst wechselte der Pfeil am Ende bei jedem Klick zwischen sichtbar und
        // unsichtbar (mit Pfeil passt der Rest nicht, ohne Pfeil schon).
        boolean overflow = contentWidth > visibleWidth;
        boolean showLeft = overflow && scrollOffset > 0;
        int viewportWidth = visibleWidth - (showLeft ? ARROW_WIDTH : 0);
        boolean showRight = overflow && scrollOffset + viewportWidth < contentWidth;
        if (showRight) {
            viewportWidth -= ARROW_WIDTH;
        }
        viewportWidth = Math.max(0, viewportWidth);
        int maxOffset = Math.max(0, contentWidth - viewportWidth);
        if (scrollOffset > maxOffset) {
            scrollOffset = maxOffset;
        }
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }

        int x = 0;
        scrollLeft.setVisible(showLeft && visibleWidth > 0);
        if (scrollLeft.isVisible()) {
            scrollLeft.setBounds(x, 0, ARROW_WIDTH, height);
            x += ARROW_WIDTH;
        }
        viewport.setBounds(x, 0, viewportWidth, height);
        content.setBounds(-scrollOffset, 0, contentWidth, height);
        x += viewportWidth;
        scrollRight.setVisible(showRight && visibleWidth > 0);
        if (scrollRight.isVisible()) {
            scrollRight.setBounds(x, 0, ARROW_WIDTH, height);
        }
        if (!scrollLeft.isVisible()) {
            leftScroller.stop();
        }
        if (!scrollRight.isVisible()) {
            rightScroller.stop();
        }
    }

    // ------------------------------------------------------------------ für Tests

    JPanel contentForTest() {
        return content;
    }

    JButton scrollLeftForTest() {
        return scrollLeft;
    }

    JButton scrollRightForTest() {
        return scrollRight;
    }

    int scrollOffsetForTest() {
        return scrollOffset;
    }
}

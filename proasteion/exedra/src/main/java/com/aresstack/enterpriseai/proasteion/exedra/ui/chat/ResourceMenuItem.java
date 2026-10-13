package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;

import javax.swing.Icon;
import javax.swing.JMenuItem;
import javax.swing.MenuSelectionManager;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;

/**
 * Ein Eintrag der Ressourcenübersicht: ein Linksklick auf den Eintrag nimmt die Ressource als Verweis (die normale
 * Aktion des Menüeintrags), das Symbol rechts öffnet sie im Lesebereich, ein Rechtsklick hängt nichts an und meldet
 * nur den Wunsch nach dem Kontextmenü. Ohne Leseweg gibt es kein Symbol.
 */
final class ResourceMenuItem extends JMenuItem {

    static final String OPEN_IN_READER = "Im Lesebereich öffnen";
    private static final int READER_GAP = 12;

    private final transient Runnable openInReader;
    private final transient Consumer<MouseEvent> contextMenu;
    private final Icon readerIcon = ComposerIcons.openBook();

    /**
     * @param openInReader opens the resource in the reader; {@code null} when it cannot be read there
     * @param contextMenu  shows the context menu for a right click (the event is relative to this item)
     */
    ResourceMenuItem(ChatResource resource, Runnable openInReader, Consumer<MouseEvent> contextMenu) {
        super(resource.title(), ChatAttachmentStrip.iconOf(resource.kind()));
        this.openInReader = openInReader;
        this.contextMenu = contextMenu;
    }

    /** Whether the item offers the reader action. */
    boolean readable() {
        return openInReader != null;
    }

    /** The bounds of the trailing reader icon, empty without a reader action. */
    Rectangle readerIconBounds() {
        if (!readable()) {
            return new Rectangle();
        }
        Insets insets = getInsets();
        int x = getWidth() - insets.right - readerIcon.getIconWidth();
        int y = (getHeight() - readerIcon.getIconHeight()) / 2;
        return new Rectangle(x, y, readerIcon.getIconWidth(), readerIcon.getIconHeight());
    }

    /** Run the reader action without adding the resource and close the overview. */
    void openInReader() {
        if (openInReader == null) {
            return;
        }
        MenuSelectionManager.defaultManager().clearSelectedPath();
        openInReader.run();
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        if (readable()) {
            size = new Dimension(size.width + READER_GAP + readerIcon.getIconWidth(), size.height);
        }
        return size;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (readable()) {
            Rectangle icon = readerIconBounds();
            readerIcon.paintIcon(this, graphics, icon.x, icon.y);
        }
    }

    @Override
    public String getToolTipText(MouseEvent event) {
        if (event != null && readerIconBounds().contains(event.getPoint())) {
            return OPEN_IN_READER;
        }
        return super.getToolTipText(event);
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
        if (SwingUtilities.isRightMouseButton(event)) {
            // A right click never adds the resource: it only asks for the context menu (once, on release).
            if (event.getID() == MouseEvent.MOUSE_RELEASED && contextMenu != null) {
                contextMenu.accept(event);
            }
            event.consume();
            return;
        }
        if (SwingUtilities.isLeftMouseButton(event) && isEnabledReaderHit(event)) {
            if (event.getID() == MouseEvent.MOUSE_RELEASED) {
                openInReader();
            }
            event.consume();
            return;
        }
        super.processMouseEvent(event);
    }

    private boolean isEnabledReaderHit(MouseEvent event) {
        return (event.getID() == MouseEvent.MOUSE_PRESSED || event.getID() == MouseEvent.MOUSE_RELEASED
                || event.getID() == MouseEvent.MOUSE_CLICKED) && readerIconBounds().contains(event.getPoint());
    }
}

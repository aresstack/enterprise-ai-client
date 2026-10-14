package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.TransferHandler;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The visible sections of the navigation drawer stacked from top to bottom (#114), each with the share of the height
 * its {@link NavigationSectionLayout} gives it, a draggable divider between two neighbours and none above or below a
 * single one. Without a visible section it shows a hint. Dropping a section handle (a footer button) between or
 * around the sections puts that section there and shows it. The stack only displays; every change by the user goes to
 * the {@link Listener}, which decides and hands the new layout back with {@link #setSectionLayout}.
 */
final class NavigationSectionStack extends JPanel {

    /** Gets every layout the user asks for (divider dragged, section dropped). */
    interface Listener {
        void layoutRequested(NavigationSectionLayout layout);
    }

    /** What a section handle carries while it is dragged. */
    static final class SectionHandle {
        final String id;

        SectionHandle(String id) {
            this.id = id;
        }
    }

    static final DataFlavor SECTION_FLAVOR = new DataFlavor(SectionHandle.class, "Navigationsbereich");
    static final int DIVIDER_SIZE = 7;
    private static final int DROP_MARK_INSET = 6;

    private final Map<String, JComponent> sections;
    private final JComponent emptyHint;
    private final List<Divider> dividers = new ArrayList<Divider>();
    private NavigationSectionLayout layout = NavigationSectionLayout.defaults();
    private Listener listener;
    /** Position among the visible sections a drop would take; -1 without a drag over the stack. */
    private int dropPosition = -1;

    NavigationSectionStack(Map<String, JComponent> sections, JComponent emptyHint) {
        super(null);
        this.sections = Collections.unmodifiableMap(new LinkedHashMap<String, JComponent>(sections));
        this.emptyHint = emptyHint;
        setOpaque(false);
        setTransferHandler(new DropHandler());
        rebuild();
    }

    void setListener(Listener listener) {
        this.listener = listener;
    }

    NavigationSectionLayout sectionLayout() {
        return layout;
    }

    /** Show {@code next} without telling the listener. */
    void setSectionLayout(NavigationSectionLayout next) {
        if (next == null) {
            return;
        }
        boolean sameSections = next.visibleOrder().equals(layout.visibleOrder());
        layout = next;
        if (sameSections) {
            revalidate(); // only the shares changed
            repaint();
        } else {
            rebuild();
        }
    }

    /** The dividers shown, one between each two visible sections. */
    List<? extends JComponent> dividers() {
        return Collections.unmodifiableList(dividers);
    }

    /** Remove the drop mark (a drag ended outside the stack). */
    void clearDropMark() {
        if (dropPosition >= 0) {
            dropPosition = -1;
            repaint();
        }
    }

    private void request(NavigationSectionLayout next) {
        if (listener != null) {
            listener.layoutRequested(next);
        }
    }

    private void rebuild() {
        removeAll();
        dividers.clear();
        List<String> shown = layout.visibleOrder();
        if (shown.isEmpty()) {
            add(emptyHint);
        }
        for (int i = 0; i < shown.size(); i++) {
            if (i > 0) {
                Divider divider = new Divider(i - 1);
                dividers.add(divider);
                add(divider);
            }
            add(sections.get(shown.get(i)));
        }
        revalidate();
        repaint();
    }

    @Override
    public void doLayout() {
        int width = getWidth();
        int height = getHeight();
        List<String> shown = layout.visibleOrder();
        if (shown.isEmpty()) {
            emptyHint.setBounds(0, 0, width, Math.min(height, emptyHint.getPreferredSize().height));
            return;
        }
        int available = Math.max(0, height - dividers.size() * DIVIDER_SIZE);
        int y = 0;
        for (int i = 0; i < shown.size(); i++) {
            int sectionHeight = i == shown.size() - 1 ? Math.max(0, height - y)
                    : (int) Math.round(available * layout.share(shown.get(i)));
            sections.get(shown.get(i)).setBounds(0, y, width, sectionHeight);
            y += sectionHeight;
            if (i < dividers.size()) {
                dividers.get(i).setBounds(0, y, width, DIVIDER_SIZE);
                y += DIVIDER_SIZE;
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, 0);
    }

    /** The space the sections share, without the dividers. */
    private int sectionSpace() {
        return Math.max(1, getHeight() - dividers.size() * DIVIDER_SIZE);
    }

    /** Where a drop at {@code y} lands among the visible sections: before the first whose middle is below it. */
    int dropPositionAt(int y) {
        List<String> shown = layout.visibleOrder();
        for (int i = 0; i < shown.size(); i++) {
            Component section = sections.get(shown.get(i));
            if (y < section.getY() + section.getHeight() / 2) {
                return i;
            }
        }
        return shown.size();
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        if (dropPosition < 0) {
            return;
        }
        List<String> shown = layout.visibleOrder();
        int y;
        if (shown.isEmpty() || dropPosition == 0) {
            y = 1;
        } else if (dropPosition >= shown.size()) {
            y = getHeight() - 2;
        } else {
            Component section = sections.get(shown.get(dropPosition));
            y = section.getY() - DIVIDER_SIZE / 2;
        }
        graphics.setColor(ResearchUiPalette.ACCENT_BLUE);
        graphics.fillRect(DROP_MARK_INSET, y - 1, getWidth() - 2 * DROP_MARK_INSET, 3);
    }

    /** A thin line between two sections; dragging it moves the border of the two. */
    private final class Divider extends JComponent {
        private final int index;
        private int pressedY;
        private NavigationSectionLayout pressedLayout;

        Divider(int index) {
            this.index = index;
            setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
            MouseAdapter drag = new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    pressedY = event.getYOnScreen();
                    pressedLayout = layout;
                }

                @Override
                public void mouseDragged(MouseEvent event) {
                    if (pressedLayout != null) {
                        double delta = (event.getYOnScreen() - pressedY) / (double) sectionSpace();
                        request(pressedLayout.resize(Divider.this.index, delta));
                    }
                }

                @Override
                public void mouseReleased(MouseEvent event) {
                    pressedLayout = null;
                }
            };
            addMouseListener(drag);
            addMouseMotionListener(drag);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            graphics.setColor(ResearchUiPalette.LIGHT_CONTROL_BORDER);
            int y = getHeight() / 2;
            graphics.drawLine(8, y, getWidth() - 8, y);
        }
    }

    /** Accept a dragged section handle and put the section where it was dropped. */
    private final class DropHandler extends TransferHandler {
        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop() || !support.isDataFlavorSupported(SECTION_FLAVOR)) {
                return false;
            }
            Point point = support.getDropLocation().getDropPoint();
            int position = dropPositionAt(point.y);
            if (position != dropPosition) {
                dropPosition = position;
                repaint();
            }
            return true;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }
            int position = dropPosition;
            clearDropMark();
            try {
                SectionHandle handle = (SectionHandle) support.getTransferable().getTransferData(SECTION_FLAVOR);
                request(layout.moveTo(handle.id, position));
                return true;
            } catch (UnsupportedFlavorException | java.io.IOException e) {
                return false;
            }
        }
    }

    /** The transferable of a section handle. */
    static Transferable transferable(final String id) {
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[]{SECTION_FLAVOR};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return SECTION_FLAVOR.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return new SectionHandle(id);
            }
        };
    }
}

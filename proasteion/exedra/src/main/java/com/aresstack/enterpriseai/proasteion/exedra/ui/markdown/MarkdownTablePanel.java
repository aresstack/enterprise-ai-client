package com.aresstack.enterpriseai.proasteion.exedra.ui.markdown;

import com.vladsch.flexmark.ext.tables.TableBlock;
import com.vladsch.flexmark.ext.tables.TableCell;
import com.vladsch.flexmark.ext.tables.TableRow;
import com.vladsch.flexmark.ext.tables.TableSeparator;
import com.vladsch.flexmark.util.ast.Node;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableColumnModelEvent;
import javax.swing.event.TableColumnModelListener;
import javax.swing.plaf.basic.BasicTableHeaderUI;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.View;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Rendert eine Markdown-Tabelle als nicht editierbare Swing-Tabelle (aus askai-java8 {@code MarkdownTablePanel}); volle
 * Höhe bis zur Kappung, darüber vertikal scrollbar, ein nötiger horizontaler Balken wird eingerechnet.
 *
 * <p>Jede Zelle wird wie ein Absatz über den {@link InlineMarkdownRenderer} gesetzt: Links, Fett, Kursiv, Code und das
 * Inline-HTML, das Modelle in Zellen schreiben ({@code <ul><li>}, {@code <br>}), erscheinen formatiert statt als
 * Quelltext. Breite Zellen brechen ab {@value #MAX_COLUMN_WIDTH} px um, die Zeile wächst mit; Links in Zellen sind
 * klickbar.
 */
final class MarkdownTablePanel extends CappedScrollPane {

    static final int MAX_COLUMN_WIDTH = 420;
    private static final int CELL_PADDING = 18;
    /** Stärke der Linie unter dem Spaltenkopf. */
    private static final int HEADER_RULE = 2;

    private final DesktopLinkOpener linkOpener;

    MarkdownTablePanel(TableBlock tableBlock, MarkdownTheme theme, InlineMarkdownRenderer inlineRenderer,
                       DesktopLinkOpener linkOpener) {
        super(new JTable(createModel(tableBlock, theme, inlineRenderer)), 400, 4, theme);
        this.linkOpener = linkOpener;
        final JTable table = (JTable) getViewport().getView();
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(false);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFont(theme.getBodyFont());
        // Blasenschrift ist auf dunklen Blasen hell: Zellen bekommen daher den Hintergrund der Codeblöcke statt Weiß.
        table.setForeground(theme.getForeground());
        table.setBackground(theme.getCodeBackground());
        table.setSelectionForeground(theme.getForeground());
        table.setSelectionBackground(theme.getQuoteBorder());
        table.setFillsViewportHeight(true);
        getViewport().setBackground(theme.getCodeBackground());
        table.setGridColor(theme.getSeparatorColor());
        table.setShowGrid(true);
        table.setIntercellSpacing(new Dimension(8, 4));
        table.setRowHeight(minimumRowHeight(table));
        JTableHeader header = table.getTableHeader();
        installHeader(header, theme);
        table.setDefaultRenderer(Object.class, new RichCellRenderer());
        fitColumns(table);
        updateRowHeights(table);
        table.getColumnModel().addColumnModelListener(new RowHeightUpdater(table));
        installLinkHandling(table);
        setBorder(BorderFactory.createLineBorder(theme.getSeparatorColor()));
        // Steht rechts neben dem Kopf ein vertikaler Balken, setzt die Ecke den Kopf fort statt eines grauen Kastens.
        JComponent corner = new JPanel();
        corner.setBackground(theme.getTableHeaderBackground());
        corner.setBorder(BorderFactory.createMatteBorder(0, 0, HEADER_RULE, 0, theme.getQuoteBorder()));
        setCorner(UPPER_RIGHT_CORNER, corner);
    }

    /**
     * Der Spaltenkopf im Comic-Stil statt im Look-and-Feel (unter Windows ein gewölbter grauer Kopf): flacher Grund
     * eine Stufe über den Zellen, fette Blasenschrift, linksbündig wie die Zellen, darunter eine kräftigere Linie.
     * Die Basis-UI zeichnet nur die Renderer; Spalten lassen sich weiter in der Breite ziehen, aber nicht verschieben.
     */
    private static void installHeader(JTableHeader header, MarkdownTheme theme) {
        header.setUI(new BasicTableHeaderUI());
        header.setReorderingAllowed(false);
        header.setOpaque(true);
        header.setBackground(theme.getTableHeaderBackground());
        header.setForeground(theme.getForeground());
        header.setFont(theme.getBodyFont().deriveFont(Font.BOLD));
        header.setBorder(null);
        header.setDefaultRenderer(new HeaderRenderer(theme));
    }

    private static DefaultTableModel createModel(TableBlock tableBlock, MarkdownTheme theme,
                                                 InlineMarkdownRenderer inlineRenderer) {
        List<List<Node>> rows = collectRows(tableBlock);
        int columnCount = Math.max(1, maxColumnCount(rows));
        Object[] columns = new Object[columnCount];
        int firstDataRow = 0;
        if (!rows.isEmpty()) {
            List<Node> header = rows.get(0);
            for (int column = 0; column < columnCount; column++) {
                columns[column] = column < header.size()
                        ? RichCell.of(header.get(column), theme, inlineRenderer).toString() : "";
            }
            firstDataRow = 1;
        }

        Object[][] data = new Object[Math.max(0, rows.size() - firstDataRow)][columnCount];
        for (int row = firstDataRow; row < rows.size(); row++) {
            List<Node> values = rows.get(row);
            for (int column = 0; column < columnCount; column++) {
                data[row - firstDataRow][column] = column < values.size()
                        ? RichCell.of(values.get(column), theme, inlineRenderer) : "";
            }
        }
        return new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private static List<List<Node>> collectRows(Node root) {
        List<List<Node>> rows = new ArrayList<List<Node>>();
        collectRows(root, rows);
        return rows;
    }

    private static void collectRows(Node node, List<List<Node>> rows) {
        if (node instanceof TableSeparator) {
            return; // |---|---| ist keine Datenzeile
        }
        if (node instanceof TableRow) {
            List<Node> cells = new ArrayList<Node>();
            Node cell = node.getFirstChild();
            while (cell != null) {
                if (cell instanceof TableCell) {
                    cells.add(cell);
                }
                cell = cell.getNext();
            }
            rows.add(cells);
            return;
        }
        Node child = node.getFirstChild();
        while (child != null) {
            collectRows(child, rows);
            child = child.getNext();
        }
    }

    private static int maxColumnCount(List<List<Node>> rows) {
        int maximum = 0;
        for (List<Node> row : rows) {
            maximum = Math.max(maximum, row.size());
        }
        return maximum;
    }

    private static int minimumRowHeight(JTable table) {
        return table.getFontMetrics(table.getFont()).getHeight() + 8;
    }

    private static void fitColumns(JTable table) {
        for (int column = 0; column < table.getColumnCount(); column++) {
            int width = table.getTableHeader().getDefaultRenderer()
                    .getTableCellRendererComponent(table, table.getColumnName(column), false, false, -1, column)
                    .getPreferredSize().width + CELL_PADDING;
            for (int row = 0; row < table.getRowCount(); row++) {
                Object value = table.getValueAt(row, column);
                int cellWidth = value instanceof RichCell
                        ? ((RichCell) value).naturalWidth()
                        : table.prepareRenderer(table.getCellRenderer(row, column), row, column)
                        .getPreferredSize().width;
                width = Math.max(width, cellWidth + CELL_PADDING);
            }
            // Auch die aktuelle Breite setzen: die Zeilenhöhen werden gleich danach bei dieser Breite gemessen,
            // nicht erst nach dem ersten Layout der Tabelle.
            int fitted = Math.min(MAX_COLUMN_WIDTH, width);
            table.getColumnModel().getColumn(column).setPreferredWidth(fitted);
            table.getColumnModel().getColumn(column).setWidth(fitted);
        }
    }

    /** Jede Zeile so hoch wie ihre höchste umbrochene Zelle bei der aktuellen Spaltenbreite. */
    static void updateRowHeights(JTable table) {
        int minimum = minimumRowHeight(table);
        int spacing = table.getIntercellSpacing().height;
        for (int row = 0; row < table.getRowCount(); row++) {
            int height = minimum;
            for (int column = 0; column < table.getColumnCount(); column++) {
                Object value = table.getValueAt(row, column);
                if (value instanceof RichCell) {
                    int width = table.getColumnModel().getColumn(column).getWidth()
                            - table.getIntercellSpacing().width;
                    height = Math.max(height, ((RichCell) value).pane.heightForWidth(Math.max(1, width)) + spacing);
                }
            }
            if (table.getRowHeight(row) != height) {
                table.setRowHeight(row, height);
            }
        }
    }

    private void installLinkHandling(final JTable table) {
        MouseAdapter links = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent event) {
                table.setCursor(linkAt(table, event.getPoint()) == null
                        ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                if (event.getButton() != MouseEvent.BUTTON1) {
                    return;
                }
                String link = linkAt(table, event.getPoint());
                if (link != null) {
                    linkOpener.open(link);
                }
            }
        };
        table.addMouseListener(links);
        table.addMouseMotionListener(links);
    }

    /** Liefert den Link unter einem Tabellenpunkt oder {@code null}. */
    static String linkAt(JTable table, Point point) {
        int row = table.rowAtPoint(point);
        int column = table.columnAtPoint(point);
        if (row < 0 || column < 0) {
            return null;
        }
        Object value = table.getValueAt(row, column);
        if (!(value instanceof RichCell)) {
            return null;
        }
        Rectangle cell = table.getCellRect(row, column, false);
        WrappingTextPane pane = ((RichCell) value).pane;
        pane.setBounds(0, 0, cell.width, cell.height);
        return LinkMouseListener.linkOnGlyphAt(pane, new Point(point.x - cell.x, point.y - cell.y));
    }

    /** Eine gesetzte Zelle; {@link #toString()} liefert den sichtbaren Text (Kopieren, Spaltentitel). */
    static final class RichCell {

        final WrappingTextPane pane;
        private final String text;

        private RichCell(WrappingTextPane pane) {
            this.pane = pane;
            this.text = pane.getText().trim();
        }

        static RichCell of(Node cell, MarkdownTheme theme, InlineMarkdownRenderer inlineRenderer) {
            WrappingTextPane pane = inlineRenderer.render(cell, theme.getBodyFont());
            pane.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 2));
            return new RichCell(pane);
        }

        /** Breite der längsten Zeile ohne Umbruch. */
        int naturalWidth() {
            View root = pane.getUI().getRootView(pane);
            Insets insets = pane.getInsets();
            return (int) Math.ceil(root.getPreferredSpan(View.X_AXIS)) + insets.left + insets.right;
        }

        @Override
        public String toString() {
            return text;
        }
    }

    /**
     * Spaltentitel als flaches Etikett; Titel bleiben Text und werden nie als Swing-HTML gedeutet (ein
     * „&lt;html&gt;…“ aus der Modellantwort bleibt Text).
     */
    static final class HeaderRenderer extends JLabel implements TableCellRenderer {

        private final Color rule;
        private final Color divider;

        HeaderRenderer(MarkdownTheme theme) {
            putClientProperty("html.disable", Boolean.TRUE);
            setOpaque(true);
            setHorizontalAlignment(LEFT);
            setVerticalAlignment(CENTER);
            setBackground(theme.getTableHeaderBackground());
            setForeground(theme.getForeground());
            setFont(theme.getBodyFont().deriveFont(Font.BOLD));
            this.rule = theme.getQuoteBorder();
            this.divider = theme.getSeparatorColor();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setText(value == null ? "" : value.toString());
            boolean last = table != null && column == table.getColumnCount() - 1;
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, HEADER_RULE, 0, rule),
                    BorderFactory.createCompoundBorder(
                            BorderFactory.createMatteBorder(0, 0, 0, last ? 0 : 1, divider),
                            BorderFactory.createEmptyBorder(5, 6, 5, 6))));
            return this;
        }
    }

    private static final class RichCellRenderer implements TableCellRenderer {

        private final DefaultTableCellRenderer fallback = new DefaultTableCellRenderer();

        RichCellRenderer() {
            fallback.putClientProperty("html.disable", Boolean.TRUE);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            if (!(value instanceof RichCell)) {
                return fallback.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            }
            WrappingTextPane pane = ((RichCell) value).pane;
            pane.setOpaque(true);
            pane.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return pane;
        }
    }

    /** Ändert der Nutzer eine Spaltenbreite, brechen die Zellen neu um und die Zeilenhöhen folgen. */
    private static final class RowHeightUpdater implements TableColumnModelListener {

        private final JTable table;

        RowHeightUpdater(JTable table) {
            this.table = table;
        }

        @Override
        public void columnMarginChanged(ChangeEvent event) {
            updateRowHeights(table);
        }

        @Override
        public void columnAdded(TableColumnModelEvent event) {
        }

        @Override
        public void columnRemoved(TableColumnModelEvent event) {
        }

        @Override
        public void columnMoved(TableColumnModelEvent event) {
        }

        @Override
        public void columnSelectionChanged(ListSelectionEvent event) {
        }
    }
}

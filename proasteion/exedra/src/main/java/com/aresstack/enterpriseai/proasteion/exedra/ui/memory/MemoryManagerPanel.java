package com.aresstack.enterpriseai.proasteion.exedra.ui.memory;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.List;

/**
 * Lists the memories of one {@link MemoryBrowser} with a filter field and deletes the selected one after a
 * confirmation. Reusable: the project view can embed it as is; {@link #showDialog} opens one or several owners as
 * tabs.
 */
public final class MemoryManagerPanel extends JPanel {

    static final String FILTER_LABEL = "Filtern:";
    static final String DELETE_LABEL = "Löschen …";
    static final String REFRESH_LABEL = "Aktualisieren";
    static final String EMPTY_TEXT = "Keine Erinnerungen.";

    private final MemoryBrowser browser;
    private final JTextField filter = new JTextField(24);
    private final DefaultListModel<MemoryEntry> rows = new DefaultListModel<MemoryEntry>();
    private final JList<MemoryEntry> list = new JList<MemoryEntry>(rows);
    private final JTextArea detail = new JTextArea(4, 48);
    private final JButton delete = new JButton(DELETE_LABEL);
    private final JLabel status = new JLabel(" ");

    public MemoryManagerPanel(MemoryBrowser browser) {
        super(new BorderLayout(0, 6));
        if (browser == null) {
            throw new IllegalArgumentException("browser must not be null");
        }
        this.browser = browser;
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.add(new JLabel(browser.description()), BorderLayout.NORTH);
        JPanel search = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        search.add(new JLabel(FILTER_LABEL));
        search.add(filter);
        JButton refresh = new JButton(REFRESH_LABEL);
        search.add(refresh);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.addListSelectionListener(event -> showSelected());
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setPreferredSize(new Dimension(560, 260));
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        JPanel center = new JPanel(new BorderLayout(0, 6));
        center.add(listScroll, BorderLayout.CENTER);
        center.add(new JScrollPane(detail), BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.CENTER);
        bottom.add(delete, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        filter.addActionListener(event -> reload());
        refresh.addActionListener(event -> reload());
        delete.addActionListener(event -> deleteSelected());
        reload();
    }

    /** Reads the memories again with the current filter. */
    public void reload() {
        rows.clear();
        try {
            List<MemoryEntry> entries = browser.list(filter.getText());
            for (MemoryEntry entry : entries) {
                rows.addElement(entry);
            }
            status.setText(entries.isEmpty() ? EMPTY_TEXT
                    : entries.size() == 1 ? "1 Erinnerung" : entries.size() + " Erinnerungen");
        } catch (RuntimeException e) {
            status.setText("Erinnerungen nicht verfügbar: " + e.getMessage());
        }
        showSelected();
    }

    int rowCount() {
        return rows.size();
    }

    String statusText() {
        return status.getText();
    }

    void select(int index) {
        list.setSelectedIndex(index);
    }

    private void showSelected() {
        MemoryEntry entry = list.getSelectedValue();
        detail.setText(entry == null ? "" : entry.content());
        detail.setCaretPosition(0);
        delete.setEnabled(entry != null);
    }

    private void deleteSelected() {
        MemoryEntry entry = list.getSelectedValue();
        if (entry == null) {
            return;
        }
        String preview = entry.content().length() <= 200 ? entry.content() : entry.content().substring(0, 200) + "…";
        if (JOptionPane.showConfirmDialog(this, "Erinnerung " + entry.id() + " löschen?\n\n" + preview,
                "Erinnerung löschen", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE)
                != JOptionPane.OK_OPTION) {
            return;
        }
        deleteConfirmed(entry);
    }

    /** The delete after the confirmation; package-private for headless tests. */
    String deleteConfirmed(MemoryEntry entry) {
        String problem = browser.delete(entry);
        reload();
        if (problem != null) {
            status.setText(problem);
        } else {
            status.setText(entry.id() + " gelöscht. " + status.getText());
        }
        return problem;
    }

    /** One panel per browser, as tabs when there are several; modal, closes with "Schließen". */
    public static void showDialog(Component parent, String title, List<MemoryBrowser> browsers) {
        if (browsers == null || browsers.isEmpty()) {
            return;
        }
        JComponent content;
        if (browsers.size() == 1) {
            content = new MemoryManagerPanel(browsers.get(0));
        } else {
            JTabbedPane tabs = new JTabbedPane();
            for (MemoryBrowser browser : browsers) {
                tabs.addTab(browser.title(), new MemoryManagerPanel(browser));
            }
            content = tabs;
        }
        Object[] options = {"Schließen"};
        JOptionPane.showOptionDialog(parent, content, title,
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
    }
}

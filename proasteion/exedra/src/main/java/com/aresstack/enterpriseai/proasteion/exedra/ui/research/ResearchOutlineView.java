package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Der Drawer-Reiter „Gliederung“: die Gliederung des Projekts als Kapitel-Plaketten (askai arch
 * {@code OutlineTreeView}) und, solange die Phase Gliederung es erlaubt, als Text zum Korrigieren. „Bearbeiten“
 * öffnet den Text, „Speichern“ übergibt ihn dem Besitzer, „Verwerfen“ kehrt zur gespeicherten Fassung zurück. Ein
 * Hintergrund-Update überschreibt nie einen ungespeicherten Text. Nur auf dem EDT.
 */
public final class ResearchOutlineView extends JPanel {

    /** Speichert eine Korrektur; {@code null} bei Erfolg, sonst der Grund. */
    public interface SaveHandler {
        String save(String markdown);
    }

    private static final String TREE = "tree";
    private static final String TEXT = "text";
    private static final String EMPTY = "Noch keine Gliederung. Wähle die Phase „Gliederung“ und erstelle sie unten mit "
            + "Play.";

    private final OutlineTreeView tree = new OutlineTreeView();
    private final JTextArea editor = new JTextArea();
    private final JLabel status = new JLabel(" ");
    private final ComicButton editButton = new ComicButton("Bearbeiten");
    private final ComicButton saveButton = new ComicButton("Speichern");
    private final ComicButton discardButton = new ComicButton("Verwerfen");
    private final CardLayout cards = new CardLayout();
    private final JPanel footer;
    private final JPanel body = new JPanel(cards);
    private SaveHandler saveHandler;
    private String markdown = "";
    private boolean editable;
    private boolean editing;

    public ResearchOutlineView() {
        super(new BorderLayout());
        setBackground(ResearchUiPalette.LIGHT_CONTROL_BG);
        setOpaque(true);
        editor.setFont(ResearchUiTypography.regular(13f));
        editor.setLineWrap(true);
        editor.setWrapStyleWord(true);
        editor.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        tree.setFont(ResearchUiTypography.semiBold(13f));
        body.setOpaque(false);
        body.add(new ComicScrollPane(tree, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), TREE);
        body.add(new ComicScrollPane(editor, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), TEXT);
        status.setFont(ResearchUiTypography.regular(12f));
        status.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        status.setBorder(BorderFactory.createEmptyBorder(6, 10, 4, 10));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        buttons.setOpaque(false);
        buttons.add(editButton);
        buttons.add(discardButton);
        buttons.add(saveButton);
        footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(status, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        add(body, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
        editButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                startEditing();
            }
        });
        discardButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                stopEditing("Änderungen verworfen.");
            }
        });
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent event) {
                save();
            }
        });
        render("", false);
    }

    /** Die Play-Aktion des Reiters (unten mittig, über Statuszeile und Bearbeiten). */
    public void setPlayBar(ResearchPlayBar bar) {
        footer.add(bar, BorderLayout.NORTH);
        revalidate();
    }

    public void setSaveHandler(SaveHandler handler) {
        this.saveHandler = handler;
    }

    /** Neue Fassung aus dem Projekt; {@code editable}, solange die Phase Gliederung Korrekturen annimmt. */
    public void render(String outline, boolean editable) {
        this.markdown = outline == null ? "" : outline;
        this.editable = editable;
        tree.setRows(OutlineTreeModel.parse(markdown));
        if (!editing) {
            editor.setText(markdown);
            status.setText(markdown.trim().isEmpty() ? EMPTY
                    : editable ? "Klicke auf „Bearbeiten“, um die Gliederung zu korrigieren."
                    : "Bearbeiten in der Phase „Gliederung“.");
        } else if (!editable) {
            status.setText("Speichern geht nur in der Phase „Gliederung“; dein Text bleibt stehen.");
        }
        updateButtons();
    }

    /** Öffnet den Text der Gliederung (Aktion „Ändern“). */
    public void startEditing() {
        if (!editable) {
            status.setText("Die Gliederung kann in dieser Phase nicht geändert werden.");
            return;
        }
        editing = true;
        editor.setText(markdown);
        cards.show(body, TEXT);
        status.setText("Ändere die Gliederung (## Kapitel, - Leitfrage) und speichere.");
        updateButtons();
        editor.requestFocusInWindow();
    }

    public boolean isEditing() {
        return editing;
    }

    /** Text im Editor (Tests). */
    public String editorText() {
        return editor.getText();
    }

    /** Setzt den Editortext (Tests, Einfügen). */
    public void setEditorText(String text) {
        editor.setText(text);
    }

    public void save() {
        if (!editing || saveHandler == null) {
            return;
        }
        String rejection = saveHandler.save(editor.getText());
        if (rejection != null) {
            status.setText("Nicht gespeichert: " + rejection);
            return;
        }
        stopEditing("Gliederung gespeichert.");
    }

    private void stopEditing(String message) {
        editing = false;
        editor.setText(markdown);
        cards.show(body, TREE);
        status.setText(message);
        updateButtons();
    }

    private void updateButtons() {
        editButton.setVisible(editable && !editing);
        saveButton.setVisible(editing);
        discardButton.setVisible(editing);
        revalidate();
        repaint();
    }
}

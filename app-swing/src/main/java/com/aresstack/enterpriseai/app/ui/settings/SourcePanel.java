package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;

/**
 * Der Inhalt des Quellen-Dialogs in der Sprache des Einstellungen-Dialogs:
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │ MediaWiki-Quelle bearbeiten            ✕ │  stille Überschrift; rechts Platz für das Fenster-✕
 * │ Hinweis                                  │
 * ├──────────────────────────────────────────┤
 * │ Platte „Quelle“ mit den Feldern          │
 * ├──────────────────────────────────────────┤
 * │ Probleme (rote Platte, nur bei Bedarf)   │
 * │                    [Abbrechen] [Speichern]│
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * Die Felder kommen aus dem Quelltyp ({@link SourceEditor}); beim Hinzufügen wählt der Benutzer oben den Typ.
 * Speichern prüft über {@link SourceActions#validate} und schreibt nur fehlerfreie Entwürfe. Entfernt wird nicht
 * hier, sondern in der Zeile der Quelle. Läuft headless (Tests); der Rahmen ist {@link SourceDialog}.
 */
public final class SourcePanel extends JPanel {

    /** Wie der Dialog endete. */
    public enum Outcome {
        SAVED,
        CANCELLED
    }

    /** Wird gerufen, sobald der Dialog fertig ist (gespeichert, entfernt oder abgebrochen). */
    public interface Listener {
        void finished(Outcome outcome, SourceDefinition source);
    }

    public static final String SAVE_LABEL = "Speichern";
    public static final String CANCEL_LABEL = "Abbrechen";
    static final String NOTE = "Gespeichert wird in die Konfigurationsdatei; danach wird die Quelle indexiert.\n"
            + "Zugangsdaten bleiben in KeePass; hier steht nur der Titel des Eintrags.";

    private final SourceActions actions;
    private final String originalId;
    private final ComicPalette palette;
    private final SourceEditor editor;
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final ComicSectionPanel problemsPlate;
    private final JTextArea problemsText = new JTextArea();
    private final ComposerButton saveButton;
    private final ComposerButton cancelButton;
    private final JLabel heading = new JLabel();
    private Listener listener;

    /**
     * @param initial    die Quelle, wie sie in der Datei steht, oder ein neuer Entwurf mit Vorgaben
     * @param originalId die ID in der Datei beim Bearbeiten, {@code null} beim Hinzufügen
     * @param types      die angebotenen Quelltypen (beim Hinzufügen zur Auswahl)
     */
    public SourcePanel(SourceDefinition initial, String originalId, List<KnowledgeSourceType> types,
                       SourceActions actions, ComicPalette palette) {
        super(new BorderLayout(0, 8));
        if (initial == null || types == null || actions == null || palette == null) {
            throw new IllegalArgumentException("initial, types, actions and palette must not be null");
        }
        this.actions = actions;
        this.originalId = originalId;
        this.palette = palette;
        this.editor = new SourceEditor(palette, types, originalId == null, this::typeChanged);
        this.problemsPlate = new ComicSectionPanel(palette);
        this.saveButton = ComposerButton.primary(null, SAVE_LABEL, ResearchUiPalette.ACCENT_BLUE, null);
        this.cancelButton = new ComposerButton(null, CANCEL_LABEL, false);
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        buildUi();
        wire();
        load(initial);
    }

    /** Die Überschrift: „Quelle hinzufügen“ bzw. „Quelle „id“ bearbeiten (Confluence)“. */
    public static String titleFor(SourceDefinition source, KnowledgeSourceType type, String originalId) {
        if (originalId == null) {
            return "Quelle hinzufügen";
        }
        String kind = type == null ? source.typeId() : type.displayName();
        return "Quelle „" + originalId + "“ bearbeiten (" + kind + ")";
    }

    private void load(SourceDefinition source) {
        editor.load(source);
        heading.setText(titleFor(source, editor.type(), originalId));
        showProblems(Collections.<String>emptyList());
    }

    /** Typwechsel beim Hinzufügen: neuer Entwurf mit den Vorgaben des Typs. */
    private void typeChanged(String typeId) {
        load(actions.draft(typeId));
    }

    private void buildUi() {
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 14, 0, 8));
        heading.setFont(ResearchUiTypography.semiBold(15f));
        heading.setForeground(palette.getInk());
        header.add(heading, BorderLayout.CENTER);
        windowControls.setOpaque(false);
        header.add(windowControls, BorderLayout.EAST);
        north.add(header, BorderLayout.NORTH);
        JPanel note = new JPanel(new GridLayout(0, 1, 0, 2));
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(2, 14, 0, 14));
        for (String line : NOTE.split("\n")) {
            JLabel label = new JLabel(line);
            label.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            label.setFont(ResearchUiTypography.regular(12f));
            note.add(label);
        }
        north.add(note, BorderLayout.CENTER);

        JPanel column = FormRows.column(palette, FormRows.plate("Quelle", palette.getAgentPetrol(), editor.panel(),
                palette));
        ComicScrollPane scroll = new ComicScrollPane(column, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, palette);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.getViewport().setBackground(palette.getSurface());

        problemsPlate.setAccentStripe(palette.getAccentRed());
        problemsPlate.setLayout(new BorderLayout());
        problemsText.setEditable(false);
        problemsText.setFocusable(false);
        problemsText.setLineWrap(true);
        problemsText.setWrapStyleWord(true);
        problemsText.setOpaque(false);
        problemsText.setForeground(palette.getInk());
        problemsText.setFont(problemsText.getFont().deriveFont(Font.PLAIN, 12f));
        problemsText.getAccessibleContext().setAccessibleName("Probleme der Quelle");
        problemsPlate.add(problemsText, BorderLayout.CENTER);
        problemsPlate.setVisible(false);
        JPanel problemsWrap = new JPanel(new BorderLayout());
        problemsWrap.setOpaque(false);
        problemsWrap.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        problemsWrap.add(problemsPlate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new BorderLayout());
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 12));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        saveButton.setToolTipText("Quelle prüfen, speichern und indexieren");
        cancelButton.setToolTipText("Änderungen verwerfen");
        right.add(cancelButton);
        right.add(saveButton);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(problemsWrap, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void wire() {
        saveButton.addActionListener(event -> save());
        cancelButton.addActionListener(event -> finish(Outcome.CANCELLED, null));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** Die Fensterknöpfe (das ✕) rechts in der Überschrift. */
    public void setWindowControls(JComponent controls) {
        windowControls.removeAll();
        if (controls != null) {
            windowControls.add(controls);
        }
        windowControls.revalidate();
        windowControls.repaint();
    }

    /** Die Überschrift, an der das rahmenlose Fenster gezogen wird. */
    public JComponent header() {
        return header;
    }

    public String title() {
        return heading.getText();
    }

    /** Der aktuelle Stand der Felder. */
    public SourceDefinition toDefinition() {
        return editor.toDefinition();
    }

    /** Prüft und speichert; bei Erfolg endet der Dialog mit {@link Outcome#SAVED}. */
    public boolean save() {
        SourceDefinition draft = editor.toDefinition();
        List<String> problems;
        try {
            problems = actions.validate(draft, originalId);
        } catch (RuntimeException e) {
            problems = Collections.singletonList("Prüfung fehlgeschlagen: " + e.getClass().getSimpleName());
        }
        if (!problems.isEmpty()) {
            showProblems(problems);
            return false;
        }
        try {
            actions.save(draft, originalId);
        } catch (IOException | RuntimeException e) {
            showProblems(Collections.singletonList("Speichern fehlgeschlagen: " + e.getClass().getSimpleName()));
            return false;
        }
        showProblems(Collections.<String>emptyList());
        finish(Outcome.SAVED, draft);
        return true;
    }

    public void cancel() {
        finish(Outcome.CANCELLED, null);
    }

    void showProblems(List<String> problems) {
        if (problems == null || problems.isEmpty()) {
            problemsText.setText("");
            problemsPlate.setVisible(false);
        } else {
            StringBuilder sb = new StringBuilder("Bitte korrigieren:");
            for (String problem : problems) {
                sb.append("\n• ").append(problem);
            }
            problemsText.setText(sb.toString());
            problemsPlate.setVisible(true);
        }
        revalidate();
        repaint();
    }

    public void focusFirstField() {
        editor.focusFirstField();
    }

    private void finish(Outcome outcome, SourceDefinition source) {
        if (listener != null) {
            listener.finished(outcome, source);
        }
    }

    // Für Tests.

    SourceEditor editor() {
        return editor;
    }

    String problemsText() {
        return problemsPlate.isVisible() ? problemsText.getText() : "";
    }

    ComposerButton saveButton() {
        return saveButton;
    }

    ComposerButton cancelButton() {
        return cancelButton;
    }
}

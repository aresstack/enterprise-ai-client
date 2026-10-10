package com.aresstack.enterpriseai.app.ui.settings;

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
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Der Inhalt des Index-Dialogs in der Sprache des Einstellungen-Dialogs:
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │ Index-Einstellungen                    ✕ │  stille Überschrift; rechts Platz für das Fenster-✕
 * │ Hinweis: gilt beim nächsten Start        │
 * ├──────────────────────────────────────────┤
 * │ Platte „Index“: Verzeichnis, Häkchen     │
 * ├──────────────────────────────────────────┤
 * │ Probleme (rote Platte, nur bei Bedarf)   │
 * │                    [Abbrechen] [Speichern]│
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * Speichern prüft über {@link IndexActions#validate} und schreibt nur fehlerfreie Entwürfe. Läuft headless
 * (Tests); der Rahmen ist {@link IndexDialog}.
 */
public final class IndexPanel extends JPanel {

    /** Wird gerufen, sobald der Dialog fertig ist: mit dem gespeicherten Stand, oder {@code null} beim Abbrechen. */
    public interface Listener {
        void finished(IndexForm saved);
    }

    public static final String TITLE = "Index-Einstellungen";
    public static final String SAVE_LABEL = "Speichern";
    public static final String CANCEL_LABEL = "Abbrechen";
    static final String NOTE = "Indexverzeichnis und Indexierung beim Start der Anwendung.\n"
            + "Gespeichert wird in die Konfigurationsdatei; Änderungen gelten beim nächsten Start.";

    private final IndexActions actions;
    private final ComicPalette palette;
    private final IndexEditor editor;
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final ComicSectionPanel problemsPlate;
    private final JTextArea problemsText = new JTextArea();
    private final ComposerButton saveButton;
    private final ComposerButton cancelButton;
    private Listener listener;

    /** @param initial der Stand der Datei */
    public IndexPanel(IndexForm initial, IndexActions actions, ComicPalette palette) {
        super(new BorderLayout(0, 8));
        if (initial == null || actions == null || palette == null) {
            throw new IllegalArgumentException("initial, actions and palette must not be null");
        }
        this.actions = actions;
        this.palette = palette;
        this.editor = new IndexEditor(palette);
        this.problemsPlate = new ComicSectionPanel(palette);
        this.saveButton = ComposerButton.primary(null, SAVE_LABEL, ResearchUiPalette.ACCENT_BLUE, null);
        this.cancelButton = new ComposerButton(null, CANCEL_LABEL, false);
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        buildUi();
        wire();
        editor.load(initial);
    }

    private void buildUi() {
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 14, 0, 8));
        JLabel heading = new JLabel(TITLE);
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

        JPanel column = FormRows.column(palette, FormRows.plate("Index", palette.getNavigationBlue(), editor.panel(),
                palette));

        problemsPlate.setAccentStripe(palette.getAccentRed());
        problemsPlate.setLayout(new BorderLayout());
        problemsText.setEditable(false);
        problemsText.setFocusable(false);
        problemsText.setLineWrap(true);
        problemsText.setWrapStyleWord(true);
        problemsText.setOpaque(false);
        problemsText.setForeground(palette.getInk());
        problemsText.setFont(problemsText.getFont().deriveFont(Font.PLAIN, 12f));
        problemsText.getAccessibleContext().setAccessibleName("Probleme der Index-Einstellungen");
        problemsPlate.add(problemsText, BorderLayout.CENTER);
        problemsPlate.setVisible(false);
        JPanel problemsWrap = new JPanel(new BorderLayout());
        problemsWrap.setOpaque(false);
        problemsWrap.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        problemsWrap.add(problemsPlate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 12));
        saveButton.setToolTipText("In die Konfigurationsdatei schreiben; gilt beim nächsten Start");
        cancelButton.setToolTipText("Änderungen verwerfen");
        buttons.add(cancelButton);
        buttons.add(saveButton);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(problemsWrap, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(column, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void wire() {
        saveButton.addActionListener(event -> save());
        cancelButton.addActionListener(event -> cancel());
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

    /** Der aktuelle Stand der Felder. */
    public IndexForm toForm() {
        return editor.toForm();
    }

    /** Prüft und speichert; bei Erfolg endet der Dialog mit dem gespeicherten Stand. */
    public boolean save() {
        IndexForm draft = editor.toForm();
        List<String> problems;
        try {
            problems = actions.validate(draft);
        } catch (RuntimeException e) {
            problems = Collections.singletonList("Prüfung fehlgeschlagen: " + e.getClass().getSimpleName());
        }
        if (!problems.isEmpty()) {
            showProblems(problems);
            return false;
        }
        try {
            actions.save(draft);
        } catch (IOException | RuntimeException e) {
            showProblems(Collections.singletonList("Speichern fehlgeschlagen: " + e.getClass().getSimpleName()));
            return false;
        }
        showProblems(Collections.<String>emptyList());
        finish(draft);
        return true;
    }

    public void cancel() {
        finish(null);
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

    private void finish(IndexForm saved) {
        if (listener != null) {
            listener.finished(saved);
        }
    }

    // Für Tests.

    IndexEditor editor() {
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

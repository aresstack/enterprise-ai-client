package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.ComposerToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Der Inhalt des Einstellungen-Dialogs:
 *
 * <pre>
 * ┌──────────────────────────────────────────┐
 * │ Einstellungen                          ✕  │  stille Überschrift; rechts Platz für das Fenster-✕
 * │ Hinweis zum Erststart bzw. zum Neustart   │
 * ├──────────────────────────────────────────┤
 * │ [KI-Dienst] Wissensbasis  KeePass  …      │  Pillen wie die Reiterleiste des Drawers
 * ├──────────────────────────────────────────┤
 * │ Platten mit Formularzeilen (scrollbar)    │  CardLayout, je Reiter eine Karte
 * ├──────────────────────────────────────────┤
 * │ Probleme (rote Platte, nur bei Bedarf)    │
 * │                     [Abbrechen] [Speichern]│
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * Speichern prüft über {@link SettingsDialogActions#validate}, zeigt Probleme mit Feldnamen und schreibt nur
 * fehlerfreie Entwürfe ({@link SettingsDialogActions#save}); danach ruft es den Erfolgs-Callback. Läuft
 * headless (Tests) ohne Fenster; der modale Rahmen ist {@link SettingsDialog}.
 */
public final class SettingsPanel extends JPanel {

    /** Beim ersten Start gibt es keine Anwendung hinter dem Dialog; Abbrechen heißt dann Beenden. */
    public enum Mode {
        FIRST_START,
        EDIT
    }

    public static final String TITLE = "Einstellungen";
    public static final String SAVE_LABEL = "Speichern";
    public static final String CANCEL_LABEL = "Abbrechen";
    public static final String QUIT_LABEL = "Beenden";
    private static final String[] TAB_LABELS = {"KI-Dienst", "Wissensbasis", "KeePass", "Netzwerk & Agent"};

    /** Die Reiter in Reihenfolge: KI-Dienst, Wissensbasis, KeePass, Netzwerk &amp; Agent. */
    public static int tabCount() {
        return TAB_LABELS.length;
    }

    public static String tabLabel(int index) {
        return TAB_LABELS[index];
    }
    /** Hinweiszeilen unter dem Titel; {@code \n} trennt Zeilen (keine HTML-Umbrüche, die sind headless unzuverlässig). */
    static final String FIRST_START_NOTE = "Willkommen. Für den ersten Start fehlen noch Basis-URL und Modell des "
            + "KI-Dienstes\nsowie das Embedding-Modell mit seiner Dimension; der KeePass-Titel ist ein Vorschlag.\n"
            + "Alles andere hat sinnvolle Vorgaben und lässt sich später ändern.";
    static final String EDIT_NOTE = "Gespeicherte Änderungen gelten beim nächsten Start der Anwendung.\n"
            + "Secrets bleiben in KeePass; hier stehen nur die Titel der Einträge.";

    private final Mode mode;
    private final SettingsDialogActions actions;
    private final ComicPalette palette;
    private final ServiceTab serviceTab;
    private final KnowledgeTab knowledgeTab;
    private final SecurityTab securityTab;
    private final SystemTab systemTab;
    private final List<ComposerToggleButton> tabButtons = new ArrayList<ComposerToggleButton>();
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final ComicSectionPanel problemsPlate;
    private final JTextArea problemsText = new JTextArea();
    private final ComposerButton saveButton;
    private final ComposerButton cancelButton;
    private Consumer<SettingsForm> onSaved;
    private Runnable onCancel;

    public SettingsPanel(SettingsForm initial, List<String> problems, Mode mode, SettingsDialogActions actions,
                         ComicPalette palette) {
        super(new BorderLayout(0, 8));
        if (initial == null || mode == null || actions == null || palette == null) {
            throw new IllegalArgumentException("initial, mode, actions and palette must not be null");
        }
        this.mode = mode;
        this.actions = actions;
        this.palette = palette;
        Supplier<SettingsForm> current = new Supplier<SettingsForm>() {
            @Override
            public SettingsForm get() {
                return toForm();
            }
        };
        this.serviceTab = new ServiceTab(actions, current, palette);
        this.knowledgeTab = new KnowledgeTab(palette);
        this.securityTab = new SecurityTab(actions, current, palette);
        this.systemTab = new SystemTab(actions, current, palette);
        this.problemsPlate = new ComicSectionPanel(palette);
        this.saveButton = ComposerButton.primary(null, SAVE_LABEL, ResearchUiPalette.ACCENT_BLUE, null);
        this.cancelButton = mode == Mode.FIRST_START
                ? ComposerButton.primary(null, QUIT_LABEL, ResearchUiPalette.DANGER_RED, null)
                : new ComposerButton(null, CANCEL_LABEL, false);
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        buildUi();
        wire();
        setForm(initial);
        showProblems(problems == null ? Collections.<String>emptyList() : problems);
    }

    private void buildUi() {
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(6, 14, 0, 8));
        JLabel title = new JLabel(TITLE);
        title.setFont(ResearchUiTypography.semiBold(15f));
        title.setForeground(palette.getInk());
        header.add(title, BorderLayout.CENTER);
        windowControls.setOpaque(false);
        header.add(windowControls, BorderLayout.EAST);
        north.add(header, BorderLayout.NORTH);
        JPanel note = new JPanel(new GridLayout(0, 1, 0, 2));
        note.setOpaque(false);
        note.setBorder(BorderFactory.createEmptyBorder(2, 14, 0, 14));
        for (String line : (mode == Mode.FIRST_START ? FIRST_START_NOTE : EDIT_NOTE).split("\n")) {
            JLabel label = new JLabel(line);
            label.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            label.setFont(ResearchUiTypography.regular(12f));
            note.add(label);
        }
        north.add(note, BorderLayout.CENTER);
        JPanel tabs = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        tabs.setOpaque(false);
        tabs.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));
        ButtonGroup group = new ButtonGroup();
        JPanel[] pages = {serviceTab.panel(), knowledgeTab.panel(), securityTab.panel(), systemTab.panel()};
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final String name = TAB_LABELS[i];
            ComposerToggleButton button = new ComposerToggleButton(null, name, null);
            button.setAccent(ResearchUiPalette.SECONDARY_SURFACE);
            button.getAccessibleContext().setAccessibleName("Reiter " + name);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    cards.show(deck, name);
                }
            });
            group.add(button);
            tabButtons.add(button);
            tabs.add(button);
            ComicScrollPane scroll = new ComicScrollPane(pages[i], ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, palette);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getViewport().setBackground(palette.getSurface());
            deck.add(scroll, name);
        }
        north.add(tabs, BorderLayout.SOUTH);
        tabButtons.get(0).setSelected(true);
        deck.setOpaque(false);

        problemsPlate.setAccentStripe(palette.getAccentRed());
        problemsPlate.setLayout(new BorderLayout());
        problemsText.setEditable(false);
        problemsText.setFocusable(false);
        problemsText.setLineWrap(true);
        problemsText.setWrapStyleWord(true);
        problemsText.setOpaque(false);
        problemsText.setForeground(palette.getInk());
        problemsText.setFont(problemsText.getFont().deriveFont(Font.PLAIN, 12f));
        problemsText.getAccessibleContext().setAccessibleName("Probleme der Konfiguration");
        problemsPlate.add(problemsText, BorderLayout.CENTER);
        problemsPlate.setVisible(false);
        JPanel problemsWrap = new JPanel(new BorderLayout());
        problemsWrap.setOpaque(false);
        problemsWrap.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        problemsWrap.add(problemsPlate, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 12));
        saveButton.setToolTipText(mode == Mode.FIRST_START ? "Konfiguration schreiben und die Anwendung starten"
                : "Konfiguration schreiben; gilt beim nächsten Start");
        cancelButton.setToolTipText(mode == Mode.FIRST_START ? "Ohne Konfiguration beenden" : "Änderungen verwerfen");
        buttons.add(cancelButton);
        buttons.add(saveButton);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(problemsWrap, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        add(north, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private void wire() {
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
    }

    /** Nach erfolgreichem Speichern (auf dem EDT). */
    public void setOnSaved(Consumer<SettingsForm> callback) {
        this.onSaved = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    /** Füllt alle Reiter aus dem Formular. */
    public void setForm(SettingsForm form) {
        serviceTab.load(form);
        knowledgeTab.load(form);
        securityTab.load(form);
        systemTab.load(form);
    }

    /** Der aktuelle Stand aller Felder als Formular. */
    public SettingsForm toForm() {
        SettingsForm.Builder b = SettingsForm.builder();
        serviceTab.store(b);
        knowledgeTab.store(b);
        securityTab.store(b);
        systemTab.store(b);
        return b.build();
    }

    /** Zeigt die Probleme unter den Reitern; leer blendet die Platte aus. */
    public void showProblems(List<String> problems) {
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

    /** Prüft und speichert; bei Erfolg wird der Callback gerufen. @return {@code true}, wenn gespeichert wurde */
    public boolean save() {
        SettingsForm form = toForm();
        List<String> problems;
        try {
            problems = actions.validate(form);
        } catch (RuntimeException e) {
            problems = Collections.singletonList("Prüfung fehlgeschlagen: " + e.getClass().getSimpleName());
        }
        if (!problems.isEmpty()) {
            showProblems(problems);
            selectTabFor(problems);
            return false;
        }
        try {
            actions.save(form);
        } catch (IOException e) {
            showProblems(Collections.singletonList("Speichern fehlgeschlagen: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")")));
            return false;
        } catch (RuntimeException e) {
            showProblems(Collections.singletonList("Speichern fehlgeschlagen: " + e.getClass().getSimpleName()));
            return false;
        }
        showProblems(Collections.<String>emptyList());
        if (onSaved != null) {
            onSaved.accept(form);
        }
        return true;
    }

    private void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    /** Springt zu dem Reiter, zu dem das erste Problem gehört. */
    private void selectTabFor(List<String> problems) {
        String first = problems.get(0);
        int tab = 0;
        if (first.contains("(source.") || first.contains("(sources") || first.contains("(knowledge.")) {
            tab = 1;
        } else if (first.contains("(security.")) {
            tab = 2;
        } else if (first.contains("(network.") || first.contains("(agent.") || first.contains("(ui.")) {
            tab = 3;
        }
        selectTab(tab);
    }

    public void selectTab(int index) {
        ComposerToggleButton button = tabButtons.get(index);
        button.setSelected(true);
        cards.show(deck, TAB_LABELS[index]);
    }

    /** Setzt den Fokus in das erste Pflichtfeld (nach dem Öffnen). */
    public void focusFirstField() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                serviceTab.chatBaseUrl().requestFocusInWindow();
            }
        });
    }

    public Mode mode() {
        return mode;
    }

    public ComposerButton saveButton() {
        return saveButton;
    }

    public ComposerButton cancelButton() {
        return cancelButton;
    }

    /** Die Überschrift-Zeile — im rahmenlosen Dialog zugleich die Zieh-Fläche. */
    public JComponent header() {
        return header;
    }

    /** Die Fensterknöpfe (das ✕) rechts in der Überschrift; {@code null} entfernt sie. */
    public void setWindowControls(JComponent controls) {
        windowControls.removeAll();
        if (controls != null) {
            windowControls.add(controls);
        }
        windowControls.revalidate();
        windowControls.repaint();
    }

    /** Der angezeigte Problemtext (leer, wenn die Platte unsichtbar ist). */
    public String problemsText() {
        return problemsPlate.isVisible() ? problemsText.getText() : "";
    }

    ComposerToggleButton tabButton(int index) {
        return tabButtons.get(index);
    }

    JComponent deck() {
        return deck;
    }

    ServiceTab serviceTab() {
        return serviceTab;
    }

    KnowledgeTab knowledgeTab() {
        return knowledgeTab;
    }

    SecurityTab securityTab() {
        return securityTab;
    }

    SystemTab systemTab() {
        return systemTab;
    }
}

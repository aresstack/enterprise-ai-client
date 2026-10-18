package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupWizardMascot;
import com.aresstack.enterpriseai.ui.comic.control.ComicScrollPane;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.control.ComposerToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
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
 * │ Hinweis zum Erststart bzw. zu KeePass     │
 * ├──────────────────────────────────────────┤
 * │ KI-Dienst  │ Platten mit Formularzeilen  │  Navigationsliste links wie askai arch
 * │ KeePass    │ (scrollbar)                 │  (Outlook-Stil), rechts je Kategorie eine
 * │ …          │                             │  Karte im CardLayout
 * ├──────────────────────────────────────────┤
 * │ Probleme (rote Platte, nur bei Bedarf)    │
 * │ [Einstellungsordner öffnen] [Offline-Paket erstellen … | Fortschritt ×]  [Abbrechen] [Speichern]│
 * └──────────────────────────────────────────┘
 * </pre>
 *
 * Speichern prüft über {@link SettingsDialogActions#validate}, zeigt Probleme mit Feldnamen und schreibt nur
 * fehlerfreie Entwürfe ({@link SettingsDialogActions#save}); danach ruft es den Erfolgs-Callback. Läuft
 * headless (Tests) ohne Fenster; der modale Rahmen ist {@link SettingsDialog}. Wissensquellen haben keinen Reiter
 * (Drawer-Reiter „Wissensquellen“ und {@link SourceDialog}); das Formular trägt sie unverändert weiter. Das
 * Indexverzeichnis steht im Reiter „Index“.
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
    public static final String OPEN_FOLDER_LABEL = "Einstellungsordner öffnen";
    public static final String OFFLINE_PACKAGE_LABEL = "Offline-Paket erstellen …";
    static final String OFFLINE_PACKAGE_HINT =
            "Packt alle installierten lokalen KI- und Sprachmodelle in ein ZIP für eine Offline-Installation.";
    public static final String QUIT_LABEL = "Beenden";
    private static final String[] TAB_LABELS = {"KI-Dienst", "KeePass", "Netzwerk & Agent", "Modelle",
            "Sprachausgabe", "Index", "Tools", "Browser", "Darstellung", "Technische Details", "Merlins Kristallhöhle"};
    /** Index des Reiters „Modelle“ (Auswahl je Kategorie). */
    static final int MODELS_TAB = 3;
    /** Index der Kategorie „Sprachausgabe“. */
    static final int SPEECH_TAB = 4;
    /** Index der Kategorie „Index“ (Indexverzeichnis). */
    static final int INDEX_TAB = 5;
    /** Index der Kategorie „Tools“ (Werkzeuge der zentralen Registry mit Häkchen „aktiv“). */
    static final int TOOLS_TAB = 6;
    /** Index of the "Browser" category (global browser access policy). */
    static final int BROWSER_TAB = 7;
    /** Index der Kategorie „Darstellung“ (Farben der Sprechblasen). */
    static final int APPEARANCE_TAB = 8;
    /** Index der Kategorie „Technische Details“ (Protokoll). */
    static final int TECHNICAL_TAB = 9;

    /**
     * Die Kategorien in Reihenfolge: KI-Dienst, KeePass, Netzwerk &amp; Agent, Modelle, Sprachausgabe, Index, Tools,
     * Browser, Darstellung, Technische Details.
     */
    public static int tabCount() {
        return TAB_LABELS.length;
    }

    public static String tabLabel(int index) {
        return TAB_LABELS[index];
    }
    /** Hinweiszeilen unter dem Titel; {@code \n} trennt Zeilen (keine HTML-Umbrüche, die sind headless unzuverlässig). */
    static final String FIRST_START_NOTE = "Willkommen. Für den ersten Start fehlen noch der KeePass-Eintrag mit API-Key und "
            + "URL sowie die Embedding-Dimension\n(Reiter KI-Dienst) und Chat- und Embedding-Modell (Reiter Modelle).\n"
            + "Alles andere hat sinnvolle Vorgaben und lässt sich später ändern.";
    static final String EDIT_NOTE = "Gespeicherte Änderungen gelten sofort, ohne Neustart.\n"
            + "Mit KeePass stehen API-Key und URL im Eintrag; hier steht nur sein Titel.";

    private final Mode mode;
    private final SettingsDialogActions actions;
    private final ComicPalette palette;
    private final ServiceTab serviceTab;
    private final SecurityTab securityTab;
    private final SystemTab systemTab;
    private final ModelsTab modelsTab;
    private final SpeechTab speechTab;
    private final IndexEditor indexTab;
    private final ToolsTab toolsTab;
    private final BrowserTab browserTab;
    private final AppearanceTab appearanceTab;
    private final JTextArea technicalText = new JTextArea();
    private final JTextArea technicalStatus = new JTextArea();
    private final SetupWizardMascot merlin;
    private final ComposerButton technicalConnection = new ComposerButton(null, "Verbindung testen", false,
            "Weg zum KI-Dienst mit den Angaben dieses Dialogs prüfen");
    private final ComposerButton technicalWizardButton = new ComposerButton(null,
            "Einrichtungsassistent erneut starten", false, "Merlin führt noch einmal durch die Einrichtung");
    private final List<ComposerToggleButton> tabButtons = new ArrayList<ComposerToggleButton>();
    private final JPanel header = new JPanel(new BorderLayout(8, 0));
    private final JPanel windowControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final ComicSectionPanel problemsPlate;
    private final JTextArea problemsText = new JTextArea();
    private final ComposerButton saveButton;
    private final ComposerButton cancelButton;
    private final ComposerButton setupWizardButton = new ComposerButton(null, "Einrichtungsassistent", false,
            "Den Einrichtungsassistenten erneut öffnen (KeePass, KI-Dienst, Modelle, Confluence)");
    private final ComposerButton openFolderButton = new ComposerButton(null, OPEN_FOLDER_LABEL, false,
            "Den Ordner mit Konfiguration und Protokollen im Dateimanager öffnen");
    private final ComposerButton offlinePackageButton = new ComposerButton(null, OFFLINE_PACKAGE_LABEL, false,
            OFFLINE_PACKAGE_HINT);
    private final DownloadProgress offlineProgress = new DownloadProgress("Erstellen abbrechen");
    /** Ergebnis des letzten Exports (Erfolg, Fehler oder Abbruch); nur Tooltip bzw. Fehlerdialog, nie Fußzeilentext. */
    private String offlineResult = "";
    /** Export area of the footer: {@link #OFFLINE_IDLE} shows the button, {@link #OFFLINE_RUNNING} the progress. */
    private final CardLayout offlineCards = new CardLayout();
    private final JPanel offlineArea = new JPanel(offlineCards);
    private static final String OFFLINE_IDLE = "idle";
    private static final String OFFLINE_RUNNING = "running";
    /** Der laufende Export oder {@code null}. */
    private InstallCancellation offlineExport;
    private Consumer<SettingsForm> onSaved;
    private Runnable onCancel;
    /** Der geladene Stand; liefert die Felder ohne Reiter (Quellen) unverändert in {@link #toForm()}. */
    private SettingsForm loaded;

    public SettingsPanel(SettingsForm initial, List<String> problems, Mode mode, SettingsDialogActions actions,
                         ComicPalette palette) {
        this(initial, problems, mode, actions, ToolOverview.none(), palette);
    }

    /** @param tools die Werkzeuge der laufenden Anwendung für den Reiter „Tools“ */
    public SettingsPanel(SettingsForm initial, List<String> problems, Mode mode, SettingsDialogActions actions,
                         ToolOverview tools, ComicPalette palette) {
        super(new BorderLayout(0, 8));
        if (initial == null || mode == null || actions == null || tools == null || palette == null) {
            throw new IllegalArgumentException("initial, mode, actions, tools and palette must not be null");
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
        this.securityTab = new SecurityTab(actions, current, palette);
        this.systemTab = new SystemTab(actions, current, palette);
        this.modelsTab = new ModelsTab(actions, current, palette);
        this.speechTab = new SpeechTab(modelsTab, actions, current, palette);
        this.indexTab = new IndexEditor(palette);
        this.toolsTab = new ToolsTab(tools, palette);
        this.browserTab = new BrowserTab(palette);
        this.appearanceTab = new AppearanceTab(palette);
        this.merlin = SetupWizardMascot.corner(palette);
        // Der Name unter „Sprachausgabe“ steht sofort auch über der Antwort-Vorschau unter „Darstellung“.
        speechTab.assistantName().onNameChanged(appearanceTab::setAssistantName);
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
        JPanel tabs = new JPanel(new GridLayout(0, 1, 0, 4));
        tabs.setOpaque(false);
        ButtonGroup group = new ButtonGroup();
        JPanel[] pages = {serviceTab.panel(), securityTab.panel(), systemTab.panel(), modelsTab.panel(),
                speechTab.panel(), indexTab.panel(), toolsTab.panel(), browserTab.panel(), appearanceTab.panel(), technicalPage(), new MerlinCavePanel(palette)};
        for (int i = 0; i < TAB_LABELS.length; i++) {
            final String name = TAB_LABELS[i];
            ComposerToggleButton button = new ComposerToggleButton(null, name, null);
            button.setAccent(ResearchUiPalette.SECONDARY_SURFACE);
            button.getAccessibleContext().setAccessibleName("Kategorie " + name);
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    showCard(name);
                }
            });
            group.add(button);
            tabButtons.add(button);
            tabs.add(button);
            ComicScrollPane scroll = new ComicScrollPane(new FittingPage(pages[i]), ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                    ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER, palette);
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.getViewport().setBackground(palette.getSurface());
            deck.add(scroll, name);
        }
        JPanel navigation = new JPanel(new BorderLayout());
        navigation.setOpaque(false);
        navigation.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 4));
        navigation.add(tabs, BorderLayout.NORTH);
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
                : "Konfiguration schreiben; gilt sofort");
        cancelButton.setToolTipText(mode == Mode.FIRST_START ? "Ohne Konfiguration beenden" : "Änderungen verwerfen");
        setupWizardButton.setVisible(false);
        buttons.add(setupWizardButton);
        buttons.add(cancelButton);
        buttons.add(saveButton);
        JPanel folder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        folder.setOpaque(false);
        folder.setBorder(BorderFactory.createEmptyBorder(4, 12, 0, 0));
        openFolderButton.setVisible(actions.settingsFolder() != null);
        folder.add(openFolderButton);
        JPanel idle = new JPanel(new BorderLayout());
        idle.setOpaque(false);
        idle.add(offlinePackageButton, BorderLayout.WEST);
        JPanel idleTop = new JPanel(new BorderLayout());
        idleTop.setOpaque(false);
        idleTop.add(idle, BorderLayout.NORTH);
        offlineArea.setOpaque(false);
        offlineArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 0, 8));
        offlineArea.add(idleTop, OFFLINE_IDLE);
        offlineArea.add(offlineProgress, OFFLINE_RUNNING);
        offlinePackageButton.setVisible(actions.offlinePackageFileName() != null);
        offlineArea.setVisible(offlinePackageButton.isVisible());
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.add(folder, BorderLayout.WEST);
        footer.add(offlineArea, BorderLayout.CENTER);
        footer.add(buttons, BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout(0, 6));
        south.setOpaque(false);
        south.add(problemsWrap, BorderLayout.CENTER);
        south.add(footer, BorderLayout.SOUTH);

        JPanel body = new JPanel(new BorderLayout(4, 0));
        body.setOpaque(false);
        body.add(navigation, BorderLayout.WEST);
        body.add(deck, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    /**
     * Technische Details (askai arch): Protokolldatei und ihre letzten Zeilen, nur lesend. Darüber die Werkzeuge für
     * den Maschinenraum (Einrichtungsassistent, Konfiguration prüfen, Verbindungstest, Ordner öffnen), rechts unten
     * Merlin als Hausmeister dieses Bereichs.
     */
    private JPanel technicalPage() {
        JPanel page = new JPanel(new BorderLayout(0, 6));
        page.setOpaque(false);
        page.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        technicalText.setEditable(false);
        technicalText.setLineWrap(true);
        technicalText.setWrapStyleWord(false);
        technicalText.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        technicalText.setForeground(palette.getInk());
        technicalText.setBackground(palette.getSurface());
        technicalText.getAccessibleContext().setAccessibleName("Technische Details");
        ComposerButton reload = new ComposerButton(null, "Aktualisieren", false);
        reload.addActionListener(event -> loadTechnicalDetails());
        ComposerButton validate = new ComposerButton(null, "Konfiguration prüfen", false,
                "Prüft die Angaben dieses Dialogs, ohne zu speichern");
        validate.addActionListener(event -> checkConfiguration());
        technicalConnection.addActionListener(event -> testConnectionFromTechnical());
        ComposerButton folder = new ComposerButton(null, OPEN_FOLDER_LABEL, false,
                "Ordner mit Konfiguration und Protokoll öffnen");
        folder.addActionListener(event -> openSettingsFolder());
        technicalWizardButton.setVisible(false);
        technicalWizardButton.addActionListener(event -> setupWizardButton.doClick());
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        bar.setOpaque(false);
        bar.add(reload);
        bar.add(technicalWizardButton);
        bar.add(validate);
        bar.add(technicalConnection);
        bar.add(folder);
        technicalStatus.setEditable(false);
        technicalStatus.setFocusable(false);
        technicalStatus.setLineWrap(true);
        technicalStatus.setWrapStyleWord(true);
        technicalStatus.setOpaque(false);
        technicalStatus.setForeground(palette.getInk());
        technicalStatus.setVisible(false);
        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.setOpaque(false);
        north.add(bar, BorderLayout.NORTH);
        north.add(technicalStatus, BorderLayout.CENTER);
        page.add(north, BorderLayout.NORTH);
        page.add(technicalText, BorderLayout.CENTER);
        page.add(merlin, BorderLayout.SOUTH);
        return page;
    }

    /** „Konfiguration prüfen“: die Prüfung des Speicherns über dem Entwurf, ohne zu speichern. */
    private void checkConfiguration() {
        List<String> problems = actions.validate(toForm());
        technicalStatus.setText(problems == null || problems.isEmpty() ? "Konfiguration in Ordnung."
                : "Konfiguration unvollständig:\n• " + String.join("\n• ", problems));
        technicalStatus.setVisible(true);
        revalidate();
    }

    /** „Verbindung testen“: derselbe Test wie im Reiter KI-Dienst, die Schritte als Zeilen. */
    private void testConnectionFromTechnical() {
        technicalConnection.setEnabled(false);
        final StringBuilder lines = new StringBuilder("Verbindungstest läuft …");
        technicalStatus.setText(lines.toString());
        technicalStatus.setVisible(true);
        revalidate();
        lines.setLength(0);
        actions.checkConnection(toForm(), new ConnectionCheckListener() {
            @Override
            public void onStep(ConnectionCheckStep step) {
                lines.append(step.isFailure() ? "Fehler – " : "OK – ").append(step.title());
                if (step.detail() != null && !step.detail().isEmpty()) {
                    lines.append(": ").append(step.detail());
                }
                lines.append('\n');
                technicalStatus.setText(lines.toString().trim());
            }

            @Override
            public void onFinished(boolean success) {
                technicalConnection.setEnabled(true);
                lines.append(success ? "Verbindung in Ordnung." : "Verbindungstest fehlgeschlagen.");
                technicalStatus.setText(lines.toString().trim());
                revalidate();
            }
        });
    }

    private void loadTechnicalDetails() {
        String text = actions.technicalDetails();
        technicalText.setText(text == null || text.isEmpty() ? "Keine technischen Details verfügbar." : text);
        technicalText.setCaretPosition(technicalText.getDocument().getLength());
    }

    private void wire() {
        saveButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                save();
            }
        });
        openFolderButton.addActionListener(e -> openSettingsFolder());
        offlinePackageButton.addActionListener(e -> chooseOfflinePackage());
        offlineProgress.cancelButton().addActionListener(e -> stopOfflinePackage());
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cancel();
            }
        });
    }

    /**
     * Öffnet den Einstellungsordner im Dateimanager des Systems; kann das System keine Ordner öffnen, zeigt ein
     * Hinweis den Pfad zum Kopieren.
     */
    private void openSettingsFolder() {
        File folder = actions.settingsFolder();
        if (folder == null) {
            return;
        }
        String reason;
        try {
            if (!folder.isDirectory() && !folder.mkdirs()) {
                throw new IOException("Ordner fehlt und lässt sich nicht anlegen");
            }
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Das System kann hier keine Ordner öffnen");
            }
            Desktop.getDesktop().open(folder);
            return;
        } catch (IOException | RuntimeException e) {
            reason = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        JTextField path = new JTextField(folder.getAbsolutePath());
        path.setEditable(false);
        path.getAccessibleContext().setAccessibleName("Pfad des Einstellungsordners");
        JPanel message = new JPanel(new BorderLayout(0, 6));
        message.setOpaque(false);
        message.add(new JLabel(reason + ". Der Einstellungsordner liegt hier:"), BorderLayout.NORTH);
        message.add(path, BorderLayout.CENTER);
        JOptionPane.showMessageDialog(this, message, OPEN_FOLDER_LABEL, JOptionPane.INFORMATION_MESSAGE);
    }

    public ComposerButton openFolderButton() {
        return openFolderButton;
    }

    /** Fragt mit einem Speichern-Dialog nach dem Ziel (Vorschlag mit Zeitstempel) und erstellt dann das Paket. */
    private void chooseOfflinePackage() {
        String name = actions.offlinePackageFileName();
        if (name == null || offlineExport != null) {
            return;
        }
        JFileChooser chooser = new JFileChooser(actions.offlinePackageDirectory());
        chooser.setDialogTitle(OFFLINE_PACKAGE_LABEL);
        chooser.setFileFilter(new FileNameExtensionFilter("ZIP-Archiv (*.zip)", "zip"));
        chooser.setSelectedFile(new File(chooser.getCurrentDirectory(), name));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
            return;
        }
        File target = chooser.getSelectedFile();
        if (!target.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
            target = new File(target.getParentFile(), target.getName() + ".zip");
        }
        if (target.exists() && JOptionPane.showConfirmDialog(this, target.getName() + " gibt es schon. Ersetzen?",
                OFFLINE_PACKAGE_LABEL, JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        exportOfflinePackage(target);
    }

    /**
     * Erstellt das Offline-Paket nach {@code target} im Hintergrund. Solange es läuft, ersetzt der Fortschritt in
     * voller Breite den Knopf (auch nach dem Klick auf das X, bis der Export wirklich endet); danach steht das
     * Ergebnis im Tooltip des wieder sichtbaren Knopfs, ein Fehler zusätzlich als Meldung.
     */
    void exportOfflinePackage(File target) {
        if (offlineExport != null) {
            return;
        }
        final InstallCancellation cancellation = new InstallCancellation();
        offlineExport = cancellation;
        offlineProgress.reset("Vorbereiten …");
        offlineCards.show(offlineArea, OFFLINE_RUNNING);
        revalidate();
        actions.exportOfflinePackage(toForm(), target, cancellation, new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                if (offlineExport == cancellation && !cancellation.isCancelled()) {
                    offlineProgress.preparing(line);
                }
            }

            @Override
            public void file(String name, int number, int count, long done, long total) {
                if (offlineExport == cancellation && !cancellation.isCancelled()) {
                    offlineProgress.file(name, number, count, done, total);
                }
            }

            @Override
            public void finished(boolean written, String message) {
                if (endOfflinePackage(cancellation, message) && !written) {
                    showOfflinePackageFailure(message);
                }
            }

            @Override
            public void cancelled(String message) {
                endOfflinePackage(cancellation, message);
            }
        });
    }

    private void stopOfflinePackage() {
        if (offlineExport != null && offlineExport.cancel()) {
            offlineProgress.cancelling("Erstellen wird abgebrochen …");
        }
    }

    /** Zurück auf IDLE, genau einmal je Export; das Ergebnis steht danach im Tooltip des Knopfs. */
    private boolean endOfflinePackage(InstallCancellation cancellation, String message) {
        if (offlineExport != cancellation) {
            return false;
        }
        offlineExport = null;
        offlineResult = message == null ? "" : message.trim();
        offlinePackageButton.setToolTipText(offlineResult.isEmpty() ? OFFLINE_PACKAGE_HINT
                : OFFLINE_PACKAGE_HINT + " Zuletzt: " + offlineResult);
        offlineCards.show(offlineArea, OFFLINE_IDLE);
        revalidate();
        repaint();
        return true;
    }

    /** Ein Fehler kommt als Meldung, nicht als Text in der schmalen Fußzeile. */
    private void showOfflinePackageFailure(String message) {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;
        }
        JOptionPane.showMessageDialog(this, message == null ? "Offline-Paket nicht geschrieben." : message,
                OFFLINE_PACKAGE_LABEL, JOptionPane.ERROR_MESSAGE);
    }

    public ComposerButton offlinePackageButton() {
        return offlinePackageButton;
    }

    DownloadProgress offlineProgress() {
        return offlineProgress;
    }

    String offlineResult() {
        return offlineResult;
    }

    /**
     * Zeigt den Knopf „Einrichtungsassistent“ (nur im Modus {@link Mode#EDIT}): er verwirft den Dialog wie Abbrechen
     * und führt danach {@code action} aus; {@code null} blendet ihn aus.
     */
    public void setSetupWizardAction(final Runnable action) {
        for (java.awt.event.ActionListener listener : setupWizardButton.getActionListeners()) {
            setupWizardButton.removeActionListener(listener);
        }
        setupWizardButton.setVisible(action != null && mode == Mode.EDIT);
        technicalWizardButton.setVisible(setupWizardButton.isVisible());
        if (action != null) {
            setupWizardButton.addActionListener(e -> {
                cancel();
                action.run();
            });
        }
    }

    public ComposerButton setupWizardButton() {
        return setupWizardButton;
    }

    /** Index der ersten Kategorie, deren Name mit {@code prefix} beginnt, sonst 0. */
    public static int tabIndexStartingWith(String prefix) {
        for (int i = 0; i < TAB_LABELS.length; i++) {
            if (prefix != null && TAB_LABELS[i].startsWith(prefix)) {
                return i;
            }
        }
        return 0;
    }

    /** Nach erfolgreichem Speichern (auf dem EDT). */
    public void setOnSaved(Consumer<SettingsForm> callback) {
        this.onSaved = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    /** Füllt alle Reiter aus dem Formular; die Quellen merkt es sich unverändert. */
    public void setForm(SettingsForm form) {
        if (form == null) {
            throw new IllegalArgumentException("form must not be null");
        }
        loaded = form;
        serviceTab.load(form);
        securityTab.load(form);
        systemTab.load(form);
        modelsTab.load(form);
        speechTab.load(form);
        indexTab.load(form);
        toolsTab.load(form);
        browserTab.load(form);
        appearanceTab.load(form);
    }

    /** Der aktuelle Stand aller Felder als Formular, Quellen wie geladen. */
    public SettingsForm toForm() {
        SettingsForm.Builder b = loaded.toBuilder();
        serviceTab.store(b);
        securityTab.store(b);
        systemTab.store(b);
        modelsTab.store(b);
        speechTab.store(b);
        indexTab.store(b);
        toolsTab.store(b);
        browserTab.store(b);
        appearanceTab.store(b);
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

    /** Springt zu dem Reiter, zu dem das erste Problem gehört; ohne eigenen Reiter (Quellen) zum ersten. */
    private void selectTabFor(List<String> problems) {
        String first = problems.get(0);
        int tab = 0;
        if (first.contains("(security.")) {
            tab = 1;
        } else if (first.contains("(network.") || first.contains("(agent.") || first.contains("(ui.")) {
            tab = 2;
        } else if (first.contains("(chat.model)") || first.contains("(embedding.model)") || first.contains("(model.")
                || first.contains("(models.")) {
            tab = MODELS_TAB;
        } else if (first.contains("(knowledge.indexDirectory)")) {
            tab = INDEX_TAB;
        }
        selectTab(tab);
    }

    public void selectTab(int index) {
        ComposerToggleButton button = tabButtons.get(index);
        button.setSelected(true);
        showCard(TAB_LABELS[index]);
    }

    /** Reiter „Modelle“ mit dem Unterreiter „Lokale Modelle“ bzw. „Cloud-Modelle“ (Screenshots). */
    public void selectModelsPage(boolean local) {
        selectTab(MODELS_TAB);
        modelsTab.showPage(local);
    }

    /** Zeigt eine Karte; der Reiter „Modelle“ fragt beim ersten Anzeigen die Modellquellen im Hintergrund ab. */
    private void showCard(String name) {
        cards.show(deck, name);
        if (TAB_LABELS[MODELS_TAB].equals(name)) {
            modelsTab.shown();
        } else if (TAB_LABELS[TOOLS_TAB].equals(name)) {
            toolsTab.reload();
        } else if (TAB_LABELS[TECHNICAL_TAB].equals(name)) {
            loadTechnicalDetails();
        }
    }

    /** Setzt den Fokus in das erste Pflichtfeld (nach dem Öffnen). */
    public void focusFirstField() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                serviceTab.focusFirstField();
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

    /** Die Kategorie „Tools“ (für Tests). */
    AppearanceTab appearanceTab() {
        return appearanceTab;
    }

    ToolsTab toolsTab() {
        return toolsTab;
    }

    /** The "Browser" category (for tests). */
    BrowserTab browserTab() {
        return browserTab;
    }

    /** Die Kategorie „Index“ (für Tests). */
    IndexEditor indexTab() {
        return indexTab;
    }

    JComponent deck() {
        return deck;
    }

    ServiceTab serviceTab() {
        return serviceTab;
    }

    SecurityTab securityTab() {
        return securityTab;
    }

    SystemTab systemTab() {
        return systemTab;
    }

    /** Kategorie „Sprachausgabe“ (für Tests). */
    SpeechTab speechTab() {
        return speechTab;
    }

    /** Merlin am Rand der technischen Details (Tests). */
    SetupWizardMascot technicalMerlin() {
        return merlin;
    }

    JTextArea technicalStatus() {
        return technicalStatus;
    }

    void checkConfigurationForTest() {
        checkConfiguration();
    }

    void testConnectionForTest() {
        testConnectionFromTechnical();
    }

    ModelsTab modelsTab() {
        return modelsTab;
    }

    /**
     * Hält eine Einstellungsseite auf der Breite des sichtbaren Bereichs (wie die Wissensquellen im Drawer): ohne
     * waagrechten Scrollbalken würde sonst die Wunschbreite langer Pfade die Seite rechts abschneiden.
     */
    private static final class FittingPage extends JPanel implements Scrollable {

        FittingPage(JPanel page) {
            super(new BorderLayout());
            setOpaque(false);
            add(page, BorderLayout.CENTER);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return Math.max(16, visible.height - 16);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // Kürzere Seiten füllen die Höhe, längere scrollen.
            return getParent() != null && getParent().getHeight() > getPreferredSize().height;
        }
    }
}

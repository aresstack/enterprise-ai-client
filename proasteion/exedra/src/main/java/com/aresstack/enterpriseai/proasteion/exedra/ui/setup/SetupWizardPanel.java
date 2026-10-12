package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.DownloadText;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceOffersCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ResearchCheckIcon;
import com.aresstack.enterpriseai.ui.comic.paint.StrokeIcon;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Der Einrichtungsassistent als wiederverwendbare Komponente: fünf Schritte (KeePass verbinden, KI-Dienst, Modelle,
 * Confluence, Abschlussprüfung) mit Fortschrittsanzeige, Fehlern im jeweiligen Schritt und „Zurück“, „Weiter“,
 * „Abbrechen“, am Ende „Fertig“. Er läuft beim Erststart in einem eigenen modalen Fenster
 * ({@link SetupWizardWindow}) und später als Comic-Overlay über dem Antwortfenster. Er benutzt nur die vorhandenen
 * Wege: Pairing und Einträge über den {@link PasswordStore} der Einstellungen (KeePassRPC mit Pairing-Dialog und
 * dauerhaftem Pairing-Schlüssel), den Verbindungstest und den Modellkatalog der {@link SettingsDialogActions};
 * hinzu kommen nur Embedding-Test, Abschlussprüfung, Netzwerkdialog und Zertifikatsauswahl ({@link SetupActions}).
 * Properties-Schlüssel erscheinen nirgends. Alles auf dem EDT.
 */
public final class SetupWizardPanel extends JPanel {

    private static final long serialVersionUID = 1L;

    public static final String TITLE = "Einrichtung";
    private static final String[] STEP_NAMES = {"KeePass", "KI-Dienst", "Modelle", "Confluence", "Abschluss",
            "Spracheingabe"};
    static final int STEP_KEEPASS = 0;
    static final int STEP_SERVICE = 1;
    static final int STEP_MODELS = 2;
    static final int STEP_CONFLUENCE = 3;
    static final int STEP_FINISH = 4;
    /** Optional nach der bestandenen Abschlussprüfung: ein lokales Modell für die Spracheingabe (STT). */
    static final int STEP_SPEECH = 5;
    /** Je Schritt der Zauberer mit dem passenden Gegenstand (Schlüssel, Kugel, Karten, Buch, Haken, Mikrofon). */
    private static final String[] STEP_MASCOTS = {"wizard/wizard-01-keepass.png", "wizard/wizard-02-ai-service.png",
            "wizard/wizard-03-models.png", "wizard/wizard-04-confluence.png", "wizard/wizard-05-complete.png",
            "wizard/wizard-06-speech.png"};
    /** Vorgaben je Schritt: Platz des Zauberers im freien Bereich (wechselnd, damit es lebendig wirkt). */
    private static final SetupWizardMascot.Placement[] MASCOT_PLACEMENTS = {SetupWizardMascot.Placement.RIGHT,
            SetupWizardMascot.Placement.LEFT, SetupWizardMascot.Placement.RIGHT, SetupWizardMascot.Placement.LEFT,
            SetupWizardMascot.Placement.CENTER, SetupWizardMascot.Placement.RIGHT};
    /** Vorgaben je Schritt: Höhe des Zauberers in logischen Pixeln (passend zum üblichen freien Platz). */
    private static final int[] MASCOT_HEIGHTS = {230, 200, 210, 140, 220, 230};

    /** Der Standardeintrag des KI-Dienstes in KeePass. */
    static final String DEFAULT_SERVICE_ENTRY = "Enterprise AI API";
    static final String DEFAULT_CONFLUENCE_ENTRY = "Confluence";
    static final String NEW_ENTRY_HINT = "(neuer Eintrag)";
    private static final String KEEPASS_PREFIX = "keepass:";
    /** Ohne Stimme bleibt eine Zeile der Übergabe so lange stehen: je Zeichen, mindestens {@link #MIN_READING_MILLIS}. */
    private static final int READING_MILLIS_PER_CHARACTER = 60;
    private static final int MIN_READING_MILLIS = 1800;
    private static final String SKIP_STEP_HINT = "Ohne Prüfung zum nächsten Schritt; Eingaben bleiben erhalten";
    static final String CONFLUENCE_SKIPPED = "Betriebsbereit. Confluence ist übersprungen und lässt sich später "
            + "in der Seitenleiste unter Wissensquellen einrichten.";
    private static final String SKIP_SPEECH_HINT =
            "Ohne lokale Spracheingabe abschließen; später unter Einstellungen → Modelle → Lokale Modelle";
    /** Platzhalter für Modelle, solange der Schritt „Modelle“ noch nicht gewählt hat (Verbindungstest). */
    private static final String MODEL_PLACEHOLDER = "-";
    /** Schritte des Verbindungstests ({@code ConnectionProbe}), die das Netz betreffen bzw. den API-Key. */
    static final String PROBE_KEY = "API-Key";

    private final SettingsDialogActions settings;
    private final SetupActions setup;
    private final ComicPalette palette;
    private final SetupStepTrail trail;
    private final CardLayout cards = new CardLayout();
    private final JPanel deck = new JPanel(cards);
    private final boolean[] done = new boolean[STEP_NAMES.length];
    private final String[] errors = new String[STEP_NAMES.length];
    private final ComicSectionPanel errorPlate;
    private final JTextArea errorText = new JTextArea();
    private final ComposerButton networkButton;
    private final ComposerButton cancelButton;
    private final ComposerButton backButton;
    private final ComposerButton nextButton;
    private final ComposerButton finishButton;
    private final ComposerButton skipButton;
    private final List<SetupWizardMascot> mascots = new ArrayList<SetupWizardMascot>();

    private SettingsForm form;
    private int step;
    private Runnable onFinished;
    private Runnable onCancel;
    private boolean busy;

    // Merlin: feste Texte (WizardGuide), Sprechblase oder Stimme (WizardNarrator), Modelle vorbereiten
    private final WizardGuide guide = new WizardGuide();
    private final WizardNarrator narrator = new WizardNarrator(text -> mascotOf(step).say(text));
    private final WizardModelBootstrap bootstrap;
    /** Merlin spricht erst, wenn der KI-Dienst verbunden ist (oder bei einem späteren Aufruf von Anfang an). */
    private boolean merlinAwake;
    private boolean serviceAnnounced;
    private boolean merlinVoiceBinding;
    private SetupVoices ownMerlinVoice;
    private boolean handoff;
    private boolean handingOff;
    private SetupVoices handoffVoices;
    private int readingPace = READING_MILLIS_PER_CHARACTER;

    // Schritt 1: KeePass
    private final JTextField keePassHost = new JTextField(16);
    private final JTextField keePassPort = new JTextField(6);
    private final JTextField keePassClient = new JTextField(20);
    private final JLabel keePassStatus = statusLabel();
    private final ComposerButton pairButton;
    private List<PasswordEntry> entries = Collections.emptyList();
    /**
     * Ob die Eintragsliste in dieser Sitzung gelesen wurde. Eine leere KeePass-Datenbank ist etwas anderes als
     * „noch nie gelesen“: Erst nach dem Lesen heißt ein unbekannter Titel „neuer Eintrag“.
     */
    private boolean entriesLoaded;
    /** Warum das Nachladen der Einträge zuletzt scheiterte ({@code null}: kein Fehler). */
    private String entriesProblem;

    // Schritt 2: KI-Dienst
    private final JComboBox<String> serviceEntry = new JComboBox<String>();
    private final JLabel serviceEntryStatus = statusLabel();
    private final JTextField serviceUrl = new JTextField(28);
    private final JPasswordField serviceKey = new JPasswordField(28);
    private final JPanel serviceMissing = transparent(new GridBagLayout());
    private final ComposerButton serviceSave;
    private final ComposerButton serviceTest;
    private final JLabel serviceStatus = statusLabel();
    private PasswordEntry serviceRead;
    private boolean serviceEntryComplete;

    // Schritt 3: Modelle
    private final JComboBox<String> chatModel = new JComboBox<String>();
    private final JComboBox<String> embeddingModel = new JComboBox<String>();
    private final JTextField embeddingDimension = new JTextField(8);
    private final JLabel modelsStatus = statusLabel();
    private final JLabel dimensionStatus = statusLabel();
    private final ComposerButton modelsRefresh;
    private final ComposerButton dimensionMeasure;
    private boolean modelsLoaded;
    /** Die Modellabfrage läuft schon im Hintergrund (nach dem erfolgreichen Verbindungstest). */
    private boolean modelsPreparing;
    private String measuredFor = "";

    // Schritt 4: Confluence
    private final JComboBox<String> confluenceEntry = new JComboBox<String>();
    private final JLabel confluenceEntryStatus = statusLabel();
    /** Stand eines vollständigen Eintrags (ohne Secrets) und „Zugangsdaten ändern“. */
    private final JPanel confluenceSummary = transparent(new GridBagLayout());
    private final JLabel confluenceHasUrl = statusLabel();
    private final JLabel confluenceHasUser = statusLabel();
    private final JLabel confluenceHasSecret = statusLabel();
    private final ComposerButton confluenceChange;
    /** URL, Benutzer und Passwort bzw. Token: nur bei neuem oder unvollständigem Eintrag oder zum Ändern. */
    private final JPanel confluenceManual = transparent(new GridBagLayout());
    /** Je Zeile der Felder (URL, Benutzer, Passwort, Hinweis): Beschriftung und Feld, einzeln ein- und ausblendbar. */
    private final List<List<JComponent>> manualRows = new ArrayList<List<JComponent>>();
    private final JTextField confluenceUrl = new JTextField(28);
    private final JTextField confluenceUser = new JTextField(20);
    private final JPasswordField confluencePassword = new JPasswordField(20);
    private final JLabel confluencePasswordHint = statusLabel();
    private final JLabel certificateLabel = statusLabel();
    private final JCheckBox withoutCertificate = new JCheckBox("Ohne Zertifikat fortfahren");
    private final ComposerButton certificateChoose;
    private final ComposerButton confluenceTest;
    private final JLabel confluenceStatus = statusLabel();
    private PasswordEntry confluenceRead;
    private boolean confluenceReading;
    private boolean confluenceEditing;
    private String certificateAlias = "";
    private boolean confluenceOk;
    private ConfluenceSetup confluence;

    // Schritt 5: Abschluss
    private final JPanel checkRows = transparent(new GridBagLayout());
    private final JLabel finishStatus = statusLabel();
    private final ComposerButton finishRetry;
    private List<SetupCheck> lastChecks = Collections.emptyList();

    // Schritt 6: Spracheingabe (optional)
    private final JComboBox<String> speechModel = new JComboBox<String>();
    private final ComposerButton speechInstall;
    private final ComposerButton speechRetry;
    private final JProgressBar speechProgress = new JProgressBar(0, 100);
    private final JLabel speechStatus = statusLabel();
    private List<LocalVoiceOffer> speechOffers = Collections.emptyList();
    private boolean speechLoaded;
    private boolean speechDownloading;
    /** Die Angebote ließen sich nicht ermitteln (nicht dasselbe wie „keine Angebote“). */
    private boolean speechLoadFailed;

    public SetupWizardPanel(SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                            ComicPalette palette) {
        super(new BorderLayout(0, 8));
        if (initial == null || settings == null || setup == null || palette == null) {
            throw new IllegalArgumentException("initial, settings, setup and palette must not be null");
        }
        this.form = withKeePassDefaults(initial);
        this.settings = settings;
        this.setup = setup;
        this.palette = palette;
        this.bootstrap = new WizardModelBootstrap(settings);
        setOpaque(false);
        this.trail = new SetupStepTrail(STEP_NAMES, palette);
        this.errorPlate = new ComicSectionPanel(palette);
        this.networkButton = new ComposerButton(null, "Netzwerkeinstellungen…", false,
                "Proxy und Zertifikate in den erweiterten Einstellungen anpassen");
        this.cancelButton = new ComposerButton(null, "Abbrechen", false, "Einrichtung abbrechen");
        this.backButton = new ComposerButton(null, "Zurück", false);
        this.nextButton = ComposerButton.primary(null, "Weiter", ResearchUiPalette.ACCENT_BLUE, null);
        this.finishButton = ComposerButton.primary(null, "Fertig", ResearchUiPalette.WAVE_GREEN,
                "Einrichtung abschließen und die Anwendung öffnen");
        this.pairButton = ComposerButton.primary(null, "Mit KeePass verbinden", ResearchUiPalette.ACCENT_BLUE, null);
        this.serviceSave = new ComposerButton(null, "In KeePass speichern", false);
        this.serviceTest = ComposerButton.primary(null, "Verbindung testen", ResearchUiPalette.ACCENT_BLUE, null);
        this.modelsRefresh = new ComposerButton(null, "Modelle abfragen", false);
        this.dimensionMeasure = new ComposerButton(null, "Dimension ermitteln", false);
        this.certificateChoose = new ComposerButton(null, "Zertifikat wählen…", false);
        this.confluenceChange = new ComposerButton(null, "Zugangsdaten ändern", false,
                "URL, Benutzername oder Passwort bzw. Token im KeePass-Eintrag ändern");
        this.confluenceTest = ComposerButton.primary(null, "Verbindung testen", ResearchUiPalette.ACCENT_BLUE, null);
        this.finishRetry = new ComposerButton(null, "Erneut prüfen", false);
        this.skipButton = new ComposerButton(null, "Überspringen", false, SKIP_STEP_HINT);
        this.speechInstall = ComposerButton.primary(null, "Installieren", ResearchUiPalette.ACCENT_BLUE, null);
        this.speechRetry = new ComposerButton(null, "Erneut versuchen", false);
        buildUi();
        wire();
        load(form);
        showStep(STEP_KEEPASS);
    }

    // ------------------------------------------------------------------ Aufbau

    private void buildUi() {
        JPanel north = transparent(new BorderLayout(0, 4));
        north.add(trail, BorderLayout.CENTER);
        deck.setOpaque(false);
        deck.add(keePassStep(), STEP_NAMES[STEP_KEEPASS]);
        deck.add(serviceStep(), STEP_NAMES[STEP_SERVICE]);
        deck.add(modelsStep(), STEP_NAMES[STEP_MODELS]);
        deck.add(confluenceStep(), STEP_NAMES[STEP_CONFLUENCE]);
        deck.add(finishStep(), STEP_NAMES[STEP_FINISH]);
        deck.add(speechStep(), STEP_NAMES[STEP_SPEECH]);

        errorPlate.setAccentStripe(palette.getAccentRed());
        errorPlate.setLayout(new BorderLayout(8, 0));
        errorText.setEditable(false);
        errorText.setFocusable(false);
        errorText.setLineWrap(true);
        errorText.setWrapStyleWord(true);
        errorText.setOpaque(false);
        errorText.setForeground(palette.getInk());
        errorText.setFont(ResearchUiTypography.regular(12f));
        errorText.getAccessibleContext().setAccessibleName("Fehler in diesem Schritt");
        errorPlate.add(errorText, BorderLayout.CENTER);
        JPanel networkWrap = transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        networkWrap.add(networkButton);
        errorPlate.add(networkWrap, BorderLayout.EAST);
        errorPlate.setVisible(false);

        JPanel buttons = transparent(new BorderLayout());
        JPanel left = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(cancelButton);
        left.add(javax.swing.Box.createHorizontalStrut(8));
        left.add(skipButton);
        JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.add(backButton);
        right.add(nextButton);
        right.add(finishButton);
        buttons.add(left, BorderLayout.WEST);
        buttons.add(right, BorderLayout.EAST);

        JPanel south = transparent(new BorderLayout(0, 8));
        south.add(errorPlate, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(deck, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
    }

    private JPanel keePassStep() {
        Rows rows = new Rows("KeePass verbinden");
        rows.note("Der Enterprise AI Client liest API-Key und Zugangsdaten nur aus KeePass (KeePassRPC). "
                + "Beim ersten Verbinden zeigt KeePass einen Pairing-Schlüssel; der Client merkt sich die Kopplung "
                + "dauerhaft. Die Vorgaben passen in der Regel.");
        rows.field("Rechner", keePassHost);
        rows.field("Port", keePassPort);
        rows.field("Name des Clients", keePassClient);
        rows.field("", buttonRow(pairButton));
        rows.field("", keePassStatus);
        return rows.panel();
    }

    private JPanel serviceStep() {
        Rows rows = new Rows("KI-Dienst");
        rows.note("Der KeePass-Eintrag des KI-Dienstes trägt den API-Key im Passwortfeld und die Basis-URL im "
                + "URL-Feld. Der API-Key wird nie in die Konfiguration geschrieben.");
        serviceEntry.setEditable(true);
        rows.field("KeePass-Eintrag", serviceEntry);
        rows.field("", serviceEntryStatus);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        c.gridx = 0;
        c.gridy = 0;
        serviceMissing.add(fieldLabel("Basis-URL"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        serviceMissing.add(serviceUrl, c);
        c.gridx = 0;
        c.gridy = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0;
        serviceMissing.add(fieldLabel("API-Key"), c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        serviceMissing.add(serviceKey, c);
        c.gridx = 1;
        c.gridy = 2;
        c.fill = GridBagConstraints.NONE;
        serviceMissing.add(buttonRow(serviceSave), c);
        serviceMissing.setVisible(false);
        rows.wide(serviceMissing);
        rows.field("", buttonRow(serviceTest));
        rows.field("", serviceStatus);
        return rows.panel();
    }

    private JPanel modelsStep() {
        Rows rows = new Rows("Modelle");
        rows.note("Die Liste kommt vom KI-Dienst. Gibt es je Art genau ein Modell, ist es schon gewählt. Die "
                + "Vektorlänge des Embedding-Modells ermittelt ein kleiner Test-Aufruf.");
        chatModel.setEditable(true);
        embeddingModel.setEditable(true);
        rows.field("Chat-Modell", chatModel);
        rows.field("Embedding-Modell", embeddingModel);
        JPanel dimension = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        dimension.add(embeddingDimension);
        dimension.add(dimensionMeasure);
        rows.field("Embedding-Dimension", dimension);
        rows.field("", dimensionStatus);
        rows.field("", buttonRow(modelsRefresh));
        rows.field("", modelsStatus);
        return rows.panel();
    }

    private JPanel confluenceStep() {
        Rows rows = new Rows("Confluence");
        rows.note("Confluence wird als Wissensquelle eingerichtet; alle Spaces stehen danach unter Wissensquellen "
                + "zur Auswahl. Mit Benutzername meldet sich der Client mit Passwort an, ohne Benutzername mit "
                + "einem persönlichen Zugriffstoken.");
        confluenceEntry.setEditable(true);
        rows.field("KeePass-Eintrag", confluenceEntry);
        rows.field("", confluenceEntryStatus);
        int line = 0;
        for (JLabel state : new JLabel[] {confluenceHasUrl, confluenceHasUser, confluenceHasSecret}) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = line++;
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(1, 0, 1, 0);
            state.setForeground(palette.getInk());
            confluenceSummary.add(state, c);
        }
        GridBagConstraints change = new GridBagConstraints();
        change.gridx = 0;
        change.gridy = line;
        change.anchor = GridBagConstraints.WEST;
        change.insets = new Insets(4, 0, 0, 0);
        confluenceSummary.add(confluenceChange, change);
        confluenceSummary.setVisible(false);
        rows.field("", confluenceSummary);
        manualRow(0, "Confluence-URL", confluenceUrl);
        manualRow(1, "Benutzername", confluenceUser);
        manualRow(2, "Passwort oder Token", confluencePassword);
        manualRow(3, "", confluencePasswordHint);
        confluenceManual.setVisible(false);
        rows.wide(confluenceManual);
        JPanel certificate = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        certificate.add(certificateLabel);
        certificate.add(certificateChoose);
        rows.field("Client-Zertifikat", certificate);
        withoutCertificate.setOpaque(false);
        withoutCertificate.setIcon(new ResearchCheckIcon());
        withoutCertificate.setSelectedIcon(new ResearchCheckIcon());
        withoutCertificate.setForeground(palette.getInk());
        rows.field("", withoutCertificate);
        rows.field("", buttonRow(confluenceTest));
        rows.field("", confluenceStatus);
        return rows.panel();
    }

    /** Eine Zeile der manuellen Confluence-Felder (Beschriftung, Feld). */
    private void manualRow(int row, String label, JComponent field) {
        List<JComponent> parts = new ArrayList<JComponent>();
        manualRows.add(parts);
        parts.add(field);
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(3, 0, 3, 10);
        c.gridx = 0;
        c.gridy = row;
        if (!label.isEmpty()) {
            JLabel caption = fieldLabel(label);
            parts.add(caption);
            confluenceManual.add(caption, c);
        }
        c.gridx = 1;
        c.insets = new Insets(3, 0, 3, 0);
        c.fill = field instanceof JTextField ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
        c.weightx = 1;
        confluenceManual.add(field, c);
    }

    private JPanel finishStep() {
        Rows rows = new Rows("Abschlussprüfung");
        rows.note("Die Einstellungen werden gespeichert und dann geprüft. „Fertig“ gibt es erst, wenn alles "
                + "betriebsbereit ist.");
        rows.wide(checkRows);
        rows.field("", finishStatus);
        rows.field("", buttonRow(finishRetry));
        return rows.panel();
    }

    private JPanel speechStep() {
        Rows rows = new Rows("Lokale Spracheingabe");
        rows.note("Optional. Ein lokales Modell ermöglicht Spracheingabe ohne Cloud-STT. Dieser Schritt kann "
                + "übersprungen und später unter Einstellungen → Modelle → Lokale Modelle nachgeholt werden. "
                + "Das Modell wird einmal heruntergeladen und läuft danach im Client.");
        rows.field("Modell", speechModel);
        JPanel actions = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(speechInstall);
        actions.add(speechRetry);
        rows.field("", actions);
        speechProgress.setStringPainted(false);
        speechProgress.setVisible(false);
        rows.wide(speechProgress);
        rows.field("", speechStatus);
        return rows.panel();
    }

    // ------------------------------------------------------------------ Verhalten

    private void wire() {
        cancelButton.addActionListener(e -> cancel());
        backButton.addActionListener(e -> showStep(step - 1));
        nextButton.addActionListener(e -> showStep(step + 1));
        trail.setOnSelect(this::jumpTo);
        finishButton.addActionListener(e -> finish());
        networkButton.addActionListener(e -> editNetwork());
        pairButton.addActionListener(e -> pair());
        serviceEntry.addActionListener(e -> {
            if (!busy) {
                readServiceEntry();
            }
        });
        serviceSave.addActionListener(e -> saveServiceEntry());
        serviceTest.addActionListener(e -> testService());
        modelsRefresh.addActionListener(e -> refreshModels());
        embeddingModel.addActionListener(e -> {
            if (!busy) {
                measureDimension();
            }
        });
        dimensionMeasure.addActionListener(e -> {
            measuredFor = "";
            measureDimension();
        });
        embeddingDimension.getDocument().addDocumentListener(new SimpleDocumentListener(this::updateButtons));
        chatModel.addActionListener(e -> updateButtons());
        confluenceEntry.addActionListener(e -> {
            if (!busy) {
                readConfluenceEntry();
            }
        });
        SimpleDocumentListener invalidate = new SimpleDocumentListener(() -> {
            if (!busy) {
                confluenceOk = false;
                updateButtons();
            }
        });
        confluenceUrl.getDocument().addDocumentListener(invalidate);
        confluenceUser.getDocument().addDocumentListener(invalidate);
        confluencePassword.getDocument().addDocumentListener(invalidate);
        certificateChoose.addActionListener(e -> chooseCertificate());
        confluenceChange.addActionListener(e -> {
            confluenceEditing = true;
            showConfluenceCredentials();
        });
        withoutCertificate.addActionListener(e -> {
            if (withoutCertificate.isSelected()) {
                certificateAlias = "";
                certificateLabel.setText("kein Zertifikat");
            }
            confluenceOk = false;
            updateButtons();
        });
        confluenceTest.addActionListener(e -> testConfluence());
        finishRetry.addActionListener(e -> runFinalCheck());
        skipButton.addActionListener(e -> skip());
        speechModel.addActionListener(e -> {
            if (!busy) {
                showSpeechChoice();
            }
        });
        speechInstall.addActionListener(e -> installSpeech());
        speechRetry.addActionListener(e -> {
            if (speechLoadFailed) {
                loadSpeechModels(null);
            } else {
                installSpeech();
            }
        });
    }

    public void setOnFinished(Runnable callback) {
        this.onFinished = callback;
    }

    public void setOnCancel(Runnable callback) {
        this.onCancel = callback;
    }

    /** Der aktuelle Entwurf (ohne Secrets). */
    public SettingsForm form() {
        return collect();
    }

    /** Bricht ab (auch für das ✕ des Overlays und Escape). */
    public void cancel() {
        if (onCancel != null) {
            onCancel.run();
        }
    }

    int step() {
        return step;
    }

    boolean isDone(int index) {
        return done[index];
    }

    String errorOf(int index) {
        return errors[index];
    }

    /**
     * Springt ohne Prüfung zu einem Schritt (Klick in der Schrittleiste, „Überspringen“): Eingaben bleiben stehen,
     * der Erfüllungsstatus der Schritte ändert sich dadurch nicht. Nur während einer laufenden Abfrage geht es nicht,
     * damit deren Ergebnis nicht in einem anderen Schritt landet.
     */
    void jumpTo(int index) {
        if (busy || speechDownloading || index == step) {
            return;
        }
        showStep(index);
    }

    // ------------------------------------------------------------------ Merlin

    /**
     * Ein späterer Aufruf (aus den Einstellungen): der KI-Dienst ist schon eingerichtet, Merlin ist von Anfang an
     * wach und begrüßt als Wiedersehen statt sich ein zweites Mal vorzustellen.
     */
    public void welcomeBack() {
        wakeMerlin(true);
    }

    /** Die Stimme der Anwendung (Vorlesen); ohne gewähltes TTS-Modell bleibt es bei Sprechblasen. */
    public void setVoice(ReadAloudControl voice) {
        narrator.setVoice(voice);
    }

    /** Beendet eine laufende Sprachausgabe Merlins (Assistent geschlossen). */
    void stopVoice() {
        narrator.stop();
        if (ownMerlinVoice != null) {
            ownMerlinVoice.close();
            ownMerlinVoice = null;
        }
        if (handoffVoices != null) {
            handoffVoices.close();
            handoffVoices = null;
        }
    }

    /**
     * Ob „Fertig“ die Übergabe an den Chat-Assistenten spielt: nur beim ersten erfolgreichen Abschluss, nicht bei
     * jedem späteren Aufruf aus den Einstellungen.
     */
    public void setHandoff(boolean firstCompletion) {
        this.handoff = firstCompletion;
    }

    boolean isHandingOff() {
        return handingOff;
    }

    /** Wie lange eine Zeile ohne Stimme stehen bleibt, je Zeichen (Tests: 0 = sofort weiter). */
    void setReadingPace(int millisPerCharacter) {
        this.readingPace = Math.max(0, millisPerCharacter);
    }

    /** „Fertig“ mit gespeicherter Einrichtung: beim ersten Mal erst die Übergabe, danach schließen. */
    private void complete() {
        if (handingOff) {
            return;
        }
        if (!handoff) {
            closeWizard();
            return;
        }
        handingOff = true;
        updateButtons();
        trail.setNavigable(false);
        setup.voices(collect(), voices -> {
            handoffVoices = voices == null ? SetupVoices.silent(null) : voices;
            narrator.stop();
            handoffLine(0);
        });
    }

    /**
     * Zeile {@code index} der Übergabe: Merlin mit seiner Stimme, der Chat-Assistent mit ihrer und ihrem Namen. Die
     * nächste Zeile kommt erst, wenn diese zu Ende gesprochen ist (ohne Stimme nach einer Lesepause); nach der letzten
     * schließt der Assistent.
     */
    private void handoffLine(final int index) {
        final SetupVoices voices = handoffVoices;
        if (voices == null) {
            return; // inzwischen abgebrochen
        }
        if (index >= WizardGuide.handoffLines()) {
            handoffVoices = null;
            voices.close();
            closeWizard();
            return;
        }
        WizardGuide.Line line = WizardGuide.handoff(index);
        mascotOf(step).say(line.merlin ? WizardGuide.NAME : voices.assistantName(), line.text);
        ReadAloudControl voice = line.merlin ? voices.merlin() : voices.assistant();
        Runnable next = () -> {
            if (handoffVoices == voices) {
                handoffLine(index + 1);
            }
        };
        if (voice != null && voice.isAvailable()) {
            voice.speak(line.text, next);
        } else if (readingPace == 0) {
            next.run();
        } else {
            Timer pause = new Timer(Math.max(MIN_READING_MILLIS, line.text.length() * readingPace), e -> next.run());
            pause.setRepeats(false);
            pause.start();
        }
    }

    private void closeWizard() {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    private void wakeMerlin(boolean returning) {
        if (merlinAwake) {
            return;
        }
        merlinAwake = true;
        narrator.say(guide.greeting(returning));
    }

    /** Eine freie Frage geht nur, wenn Merlin wach ist und ein Chat-Modell feststeht. */
    boolean canAskMerlin() {
        return merlinAwake && !text(chatModel).isEmpty();
    }

    boolean isMerlinAwake() {
        return merlinAwake;
    }

    /** Das Menü beim Klick auf Merlin: feste Antworten, Überspringen und (mit Chat-Modell) eine freie Frage. */
    JPopupMenu merlinMenu() {
        final int current = step;
        JPopupMenu menu = new JPopupMenu();
        JMenuItem what = new JMenuItem("Was muss ich hier machen?");
        what.addActionListener(e -> narrator.say(guide.whatToDo(current)));
        menu.add(what);
        JMenuItem why = new JMenuItem("Warum brauche ich das?");
        why.addActionListener(e -> narrator.say(guide.whyNeeded(current)));
        menu.add(why);
        JMenuItem skip = new JMenuItem("Diesen Schritt überspringen");
        skip.setEnabled(skipButton.isEnabled());
        skip.addActionListener(e -> skip());
        menu.add(skip);
        JMenuItem ask = new JMenuItem("Eigene Frage stellen …");
        ask.setEnabled(canAskMerlin());
        ask.setToolTipText(canAskMerlin() ? null : "Geht, sobald der KI-Dienst verbunden und ein Chat-Modell gewählt ist");
        ask.addActionListener(e -> mascotOf(current).askQuestion(this::askMerlin));
        menu.add(ask);
        return menu;
    }

    /** Die freie Frage an das Chat-Modell des Entwurfs; Merlins Rahmen kommt aus {@link WizardGuide}. */
    void askMerlin(String question) {
        if (!canAskMerlin() || question == null || question.trim().isEmpty()) {
            return;
        }
        narrator.show(WizardGuide.THINKING);
        setup.ask(collect(), guide.systemPrompt(step), question.trim(), (answer, error) -> {
            boolean answered = answer != null && !answer.trim().isEmpty();
            narrator.say(answered ? answer.trim() : WizardGuide.NO_ANSWER);
        });
    }

    /** „Überspringen“: weiter zum nächsten Schritt; im letzten Schritt ohne lokale Spracheingabe abschließen. */
    private void skip() {
        if (step == STEP_SPEECH) {
            skipSpeech();
        } else {
            jumpTo(step + 1);
        }
    }

    void showStep(int index) {
        if (index < 0 || index >= STEP_NAMES.length) {
            return;
        }
        form = collect();
        step = index;
        cards.show(deck, STEP_NAMES[index]);
        for (SetupWizardMascot mascot : mascots) {
            mascot.say(null);
        }
        if (merlinAwake) {
            narrator.say(guide.enteredStep(index));
        }
        if (index == STEP_SERVICE && serviceRead == null && !done[STEP_SERVICE]) {
            withEntries(this::preselectServiceEntry);
        } else if (index == STEP_MODELS && !modelsLoaded && !modelsPreparing) {
            refreshModels();
        } else if (index == STEP_CONFLUENCE && confluenceRead == null && !confluenceOk) {
            withEntries(this::preselectConfluenceEntry);
        } else if (index == STEP_FINISH) {
            runFinalCheck();
        } else if (index == STEP_SPEECH && !speechLoaded) {
            loadSpeechModels(null);
        }
        updateButtons();
    }

    private void updateButtons() {
        done[STEP_MODELS] = !text(chatModel).isEmpty() && !text(embeddingModel).isEmpty()
                && positive(embeddingDimension.getText());
        done[STEP_CONFLUENCE] = confluenceOk && (!certificateAlias.isEmpty() || withoutCertificate.isSelected());
        done[STEP_SPEECH] = selectedSpeechOffer() != null && selectedSpeechOffer().isInstalled();
        trail.show(step, done);
        boolean speech = step == STEP_SPEECH;
        backButton.setEnabled(step > 0 && !busy && !speechDownloading);
        nextButton.setVisible(!speech);
        finishButton.setVisible(speech);
        nextButton.setEnabled(!speech && done[step] && !busy);
        // Der Schritt „Spracheingabe“ blockiert nie: Überspringen geht immer, auch während eines Downloads.
        finishButton.setEnabled(speech && done[STEP_FINISH] && done[STEP_SPEECH] && !busy && !speechDownloading);
        cancelButton.setVisible(!speech);
        // „Überspringen“ in jedem Schritt: weiter ohne Prüfung; im letzten Schritt abschließen ohne Spracheingabe,
        // das geht erst nach bestandener Abschlussprüfung.
        skipButton.setEnabled(speech ? done[STEP_FINISH] : !busy);
        skipButton.setToolTipText(speech ? SKIP_SPEECH_HINT : SKIP_STEP_HINT);
        trail.setNavigable(!busy && !speechDownloading && !handingOff);
        if (handingOff) { // die Übergabe läuft bis zum Ende, dann schließt der Assistent selbst
            backButton.setEnabled(false);
            nextButton.setEnabled(false);
            finishButton.setEnabled(false);
            skipButton.setEnabled(false);
        }
        LocalVoiceOffer chosen = selectedSpeechOffer();
        speechInstall.setVisible(chosen != null && !chosen.isInstalled() && errors[STEP_SPEECH] == null);
        speechInstall.setEnabled(!busy && !speechDownloading);
        speechRetry.setVisible(speechLoadFailed || (chosen != null && !chosen.isInstalled()
                && errors[STEP_SPEECH] != null && !speechDownloading));
        speechRetry.setEnabled(!busy && !speechDownloading);
        speechModel.setEnabled(!speechDownloading);
        String error = errors[step];
        errorPlate.setVisible(error != null && !error.isEmpty());
        errorText.setText(error == null ? "" : error);
        networkButton.setVisible((step == STEP_SERVICE || step == STEP_CONFLUENCE) && error != null
                && !error.isEmpty());
        revalidate();
        repaint();
    }

    private void fail(int index, String message) {
        errors[index] = message;
        updateButtons();
        if (merlinAwake && index == step) {
            narrator.say(guide.stepFailed(index));
        }
    }

    private void clearError(int index) {
        errors[index] = null;
    }

    // --- Schritt 1: KeePass

    private void pair() {
        form = collect();
        clearError(STEP_KEEPASS);
        done[STEP_KEEPASS] = false;
        keePassStatus.setText("Verbinde mit KeePass …");
        setBusy(true);
        store().list((list, error) -> {
            setBusy(false);
            if (error != null) {
                keePassStatus.setText("Nicht verbunden.");
                fail(STEP_KEEPASS, "KeePass ist nicht gekoppelt: " + error + " KeePass muss laufen, das Plugin "
                        + "KeePassRPC muss installiert sein, und der Pairing-Schlüssel muss bestätigt werden.");
                return;
            }
            acceptEntries(list);
            updateButtons();
        });
    }

    /**
     * Liest die KeePass-Einträge, wenn sie in dieser Sitzung noch nie gelesen wurden (KeePass ist oft längst
     * gekoppelt, und die Schrittleiste springt an „Mit KeePass verbinden“ vorbei), und macht dann weiter. Scheitert
     * das Lesen, merkt sich der Assistent den Grund; der Schritt sagt ihn, statt Einträge für neu zu halten.
     */
    private void withEntries(final Runnable then) {
        if (entriesLoaded) {
            then.run();
            return;
        }
        form = collect();
        keePassStatus.setText("Verbinde mit KeePass …");
        setBusy(true);
        store().list((list, error) -> {
            setBusy(false);
            if (error != null) {
                entriesProblem = error;
                keePassStatus.setText("Nicht verbunden.");
            } else {
                acceptEntries(list);
            }
            then.run();
        });
    }

    private void acceptEntries(List<PasswordEntry> list) {
        entries = list == null ? Collections.<PasswordEntry>emptyList() : list;
        entriesLoaded = true;
        entriesProblem = null;
        done[STEP_KEEPASS] = true;
        keePassStatus.setText("Gekoppelt; " + entries.size() + " Einträge lesbar.");
        fillEntryChoices();
        if (step == STEP_KEEPASS) { // nicht beim stillen Nachladen in späteren Schritten
            narrator.say(WizardGuide.KEEPASS_OK);
        }
    }

    /** Hinweis, solange die Eintragsliste nicht gelesen ist. */
    private String entriesMissingHint() {
        return "Die KeePass-Einträge sind nicht lesbar" + (entriesProblem == null ? "" : " (" + entriesProblem + ")")
                + "; bitte zuerst im Schritt „KeePass“ verbinden.";
    }

    private void fillEntryChoices() {
        List<String> titles = new ArrayList<String>();
        for (PasswordEntry entry : entries) {
            titles.add(entry.title());
        }
        setChoices(serviceEntry, titles, text(serviceEntry));
        setChoices(confluenceEntry, titles, text(confluenceEntry));
    }

    // --- Schritt 2: KI-Dienst

    /** Der konfigurierte Titel exakt, sonst ein eindeutiger Titel mit „Enterprise AI“, sonst der Standardtitel. */
    private void preselectServiceEntry() {
        String configured = stripPrefix(form.chatApiKeyRef());
        String chosen = null;
        if (!configured.isEmpty() && titleExists(configured)) {
            chosen = configured;
        } else {
            chosen = unique("enterprise ai");
        }
        if (chosen == null) {
            chosen = configured.isEmpty() ? DEFAULT_SERVICE_ENTRY : configured;
        }
        setText(serviceEntry, chosen);
        readServiceEntry();
    }

    private void readServiceEntry() {
        final String title = text(serviceEntry);
        serviceEntryComplete = false;
        done[STEP_SERVICE] = false;
        serviceRead = null;
        clearError(STEP_SERVICE);
        serviceStatus.setText("");
        if (title.isEmpty()) {
            serviceEntryStatus.setText("Bitte einen KeePass-Eintrag wählen oder einen Titel eintragen.");
            serviceMissing.setVisible(false);
            updateButtons();
            return;
        }
        if (!entriesLoaded) {
            serviceEntryStatus.setText(entriesMissingHint());
            serviceMissing.setVisible(false);
            updateButtons();
            return;
        }
        if (!titleExists(title)) {
            serviceEntryStatus.setText("Der Eintrag „" + title + "“ fehlt; bitte Basis-URL und API-Key angeben, "
                    + "er wird in KeePass angelegt.");
            serviceMissing.setVisible(true);
            serviceUrl.setText("");
            serviceKey.setText("");
            updateButtons();
            return;
        }
        serviceEntryStatus.setText("Lese den Eintrag …");
        setBusy(true);
        store().read(title, (entry, error) -> {
            setBusy(false);
            if (error != null || entry == null) {
                serviceEntryStatus.setText("Nicht lesbar.");
                fail(STEP_SERVICE, "Der Eintrag „" + title + "“ ist nicht lesbar: " + error);
                return;
            }
            serviceRead = entry;
            boolean hasKey = entry.hasPassword();
            boolean hasUrl = !entry.url().isEmpty();
            serviceEntryComplete = hasKey && hasUrl;
            serviceEntryStatus.setText("API-Key: " + (hasKey ? "vorhanden" : "fehlt") + ".  Basis-URL: "
                    + (hasUrl ? entry.url() : "fehlt") + ".");
            serviceMissing.setVisible(!serviceEntryComplete);
            serviceUrl.setText(entry.url());
            serviceKey.setText("");
            updateButtons();
            if (serviceEntryComplete) {
                testService();
            }
        });
    }

    private void saveServiceEntry() {
        final String title = text(serviceEntry);
        String url = serviceUrl.getText().trim();
        char[] typed = serviceKey.getPassword();
        char[] password = typed.length > 0 ? typed : serviceRead == null ? null : serviceRead.copyPassword();
        if (title.isEmpty() || url.isEmpty() || password == null || password.length == 0) {
            fail(STEP_SERVICE, "Für den Eintrag werden Basis-URL und API-Key gebraucht.");
            wipe(password);
            wipe(typed);
            return;
        }
        PasswordEntry entry = new PasswordEntry(title, serviceRead == null ? "" : serviceRead.userName(), url,
                serviceRead == null ? Collections.<String, String>emptyMap() : serviceRead.fields(), password);
        wipe(password);
        wipe(typed);
        serviceEntryStatus.setText("Speichere in KeePass …");
        setBusy(true);
        store().save(entry, (ignored, error) -> {
            entry.wipe();
            setBusy(false);
            if (error != null) {
                fail(STEP_SERVICE, "Der Eintrag ließ sich nicht in KeePass speichern: " + error);
                return;
            }
            addEntry(new PasswordEntry(title, entry.userName(), entry.url(), entry.fields(), null));
            serviceKey.setText("");
            readServiceEntry();
        });
    }

    private void testService() {
        if (!serviceEntryComplete) {
            fail(STEP_SERVICE, "Im KeePass-Eintrag fehlen noch API-Key oder Basis-URL.");
            return;
        }
        form = collect();
        clearError(STEP_SERVICE);
        done[STEP_SERVICE] = false;
        serviceStatus.setText("Teste die Verbindung zum KI-Dienst …");
        setBusy(true);
        final List<ConnectionCheckStep> failures = new ArrayList<ConnectionCheckStep>();
        settings.checkConnection(probeForm(form), new ConnectionCheckListener() {
            @Override
            public void onStep(ConnectionCheckStep checked) {
                // Ohne gelesenen API-Key geht der Test weiter; für die Einrichtung ist das ein Fehler.
                if (checked.isFailure() || (PROBE_KEY.equals(checked.title())
                        && checked.status() != ConnectionCheckStep.Status.OK)) {
                    failures.add(checked);
                }
            }

            @Override
            public void onFinished(boolean success) {
                setBusy(false);
                if (success && failures.isEmpty()) {
                    done[STEP_SERVICE] = true;
                    serviceStatus.setText("Der KI-Dienst ist erreichbar; der API-Key wurde gelesen.");
                    updateButtons();
                    prepareModelsAndWakeMerlin();
                    return;
                }
                serviceStatus.setText("Verbindung fehlgeschlagen.");
                StringBuilder message = new StringBuilder();
                for (ConnectionCheckStep failure : failures) {
                    message.append(failure.title()).append(": ").append(failure.detail()).append('\n');
                }
                if (message.length() == 0) {
                    message.append("Der Verbindungstest ist fehlgeschlagen.");
                }
                fail(STEP_SERVICE, message.toString().trim());
            }
        });
    }

    // --- Schritt 3: Modelle

    private void refreshModels() {
        form = collect();
        clearError(STEP_MODELS);
        modelsStatus.setText("Frage die Modelle des KI-Dienstes ab …");
        setBusy(true);
        bootstrap.prepare(form, models -> {
            setBusy(false);
            applyModels(models);
        });
    }

    /**
     * Nach dem erfolgreichen Verbindungstest: Modellkatalog im Hintergrund abfragen und je Art das eingetragene bzw.
     * einzige Modell vorwählen (ohne den Schritt zu sperren); danach wacht Merlin auf und stellt sich vor.
     */
    private void prepareModelsAndWakeMerlin() {
        bindMerlinVoice(this::serviceAnswered); // erst die Stimme, dann spricht er hörbar
        if (modelsLoaded || modelsPreparing) {
            return;
        }
        modelsPreparing = true;
        modelsStatus.setText("Frage die Modelle des KI-Dienstes ab …");
        bootstrap.prepare(collect(), models -> {
            modelsPreparing = false;
            applyModels(models);
        });
    }

    /**
     * Merlins eigene Stimme aus dem Entwurf, sobald der KI-Dienst antwortet (beim Erststart gibt es vorher keine):
     * nur Basis-URL, API-Key aus KeePass, Netz und {@code setup.merlin.*}. Hat er schon eine verfügbare Stimme (Aufruf
     * aus der Anwendung oder früher gebunden), bleibt sie; ohne Stimme spricht er weiter nur in Sprechblasen.
     */
    private void bindMerlinVoice(final Runnable then) {
        if (narrator.canSpeak() || merlinVoiceBinding) {
            then.run();
            return;
        }
        merlinVoiceBinding = true;
        setup.merlinVoice(collect(), voices -> {
            merlinVoiceBinding = false;
            if (ownMerlinVoice != null) {
                ownMerlinVoice.close();
            }
            ownMerlinVoice = voices;
            if (voices != null && voices.merlin() != null) {
                narrator.setVoice(voices.merlin());
            }
            then.run();
        });
    }

    /** Der KI-Dienst antwortet: beim ersten Mal stellt Merlin sich vor, danach meldet er den Erfolg. */
    private void serviceAnswered() {
        if (serviceAnnounced) {
            return; // ein zweiter erfolgreicher Test (Nachladen) überschreibt die Vorstellung nicht
        }
        serviceAnnounced = true;
        if (merlinAwake) {
            narrator.say(WizardGuide.SERVICE_OK);
            return;
        }
        merlinAwake = true;
        narrator.say(guide.greeting(false) + "\n\n" + WizardGuide.SERVICE_OK);
    }

    private void applyModels(WizardModelBootstrap.Models models) {
        modelsLoaded = true;
        List<String> chats = models.chats();
        List<String> embeddings = models.embeddings();
        boolean wasBusy = busy;
        busy = true;
        try {
            setChoices(chatModel, chats, WizardModelBootstrap.preferred(chats, text(chatModel)));
            setChoices(embeddingModel, embeddings, WizardModelBootstrap.preferred(embeddings, text(embeddingModel)));
        } finally {
            busy = wasBusy;
        }
        if (chats.isEmpty() && embeddings.isEmpty()) {
            modelsStatus.setText("Keine Modelle gefunden; die Namen lassen sich auch eintragen.");
            String reason = failedStatus(models.snapshot());
            if (reason != null) {
                fail(STEP_MODELS, "Die Modellliste ist nicht abrufbar: " + reason);
            }
        } else {
            modelsStatus.setText(chats.size() + " Chat-Modell(e), " + embeddings.size() + " Embedding-Modell(e). "
                    + (chats.size() > 1 || embeddings.size() > 1 ? "Bitte auswählen." : ""));
        }
        measureDimension();
        updateButtons();
    }

    private static String failedStatus(ModelCatalogSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        for (CatalogStatus status : snapshot.statuses()) {
            if (!status.available()) {
                return status.message();
            }
        }
        return null;
    }

    private void measureDimension() {
        final String model = text(embeddingModel);
        if (model.isEmpty() || model.equals(measuredFor)) {
            updateButtons();
            return;
        }
        measuredFor = model;
        form = collect();
        clearError(STEP_MODELS);
        dimensionStatus.setText("Ermittle die Dimension mit einem Test-Aufruf …");
        setBusy(true);
        setup.embeddingDimension(form, (dimension, error) -> {
            setBusy(false);
            if (!model.equals(text(embeddingModel))) {
                return;
            }
            if (dimension != null && dimension > 0) {
                embeddingDimension.setText(Integer.toString(dimension));
                dimensionStatus.setText("Ermittelt: " + dimension + " Werte je Vektor.");
            } else {
                measuredFor = "";
                dimensionStatus.setText("Nicht automatisch ermittelbar; bitte die Dimension eintragen.");
                fail(STEP_MODELS, "Der Embedding-Test ist fehlgeschlagen: " + error
                        + " Die Dimension kann von Hand eingetragen werden.");
            }
            updateButtons();
        });
    }

    // --- Schritt 4: Confluence

    private void preselectConfluenceEntry() {
        String chosen = null;
        for (PasswordEntry entry : entries) {
            if (contains(entry.title(), "confluence") || contains(entry.url(), "confluence")) {
                if (chosen != null) {
                    chosen = null;
                    break;
                }
                chosen = entry.title();
            }
        }
        busy = true;
        try {
            setText(confluenceEntry, chosen == null ? DEFAULT_CONFLUENCE_ENTRY : chosen);
        } finally {
            busy = false;
        }
        readConfluenceEntry();
    }

    /**
     * Liest den gewählten Eintrag. Ein vollständiger Eintrag (URL und Passwort bzw. Token) braucht keine Eingabe:
     * Der Schritt zeigt nur, was vorhanden ist, und testet mit den Werten aus KeePass. Die Felder erscheinen bei
     * einem neuen oder unvollständigen Eintrag und nach „Zugangsdaten ändern“.
     */
    private void readConfluenceEntry() {
        final String title = text(confluenceEntry);
        confluenceRead = null;
        confluenceOk = false;
        confluenceEditing = false;
        confluenceReading = false;
        clearError(STEP_CONFLUENCE);
        confluenceStatus.setText("");
        if (title.isEmpty() || !entriesLoaded || !titleExists(title)) {
            busy = true;
            try {
                confluenceEntryStatus.setText(title.isEmpty()
                        ? "Bitte einen KeePass-Eintrag wählen oder einen Titel eintragen."
                        : !entriesLoaded ? entriesMissingHint()
                        : "Neuer Eintrag: URL und Passwort bzw. Token angeben; er wird beim Testen in KeePass "
                        + "angelegt.");
                confluencePasswordHint.setText(" ");
                certificateAlias = "";
                certificateLabel.setText("kein Zertifikat");
            } finally {
                busy = false;
            }
            showConfluenceCredentials();
            return;
        }
        confluenceEntryStatus.setText("Lese den Eintrag …");
        confluenceReading = true;
        showConfluenceCredentials();
        setBusy(true);
        store().read(title, (entry, error) -> {
            confluenceReading = false;
            setBusy(false);
            if (error != null || entry == null) {
                confluenceEntryStatus.setText("Nicht lesbar.");
                showConfluenceCredentials();
                fail(STEP_CONFLUENCE, "Der Eintrag „" + title + "“ ist nicht lesbar: " + error);
                return;
            }
            confluenceRead = entry;
            busy = true;
            try {
                confluenceUrl.setText(entry.url());
                confluenceUser.setText(entry.userName());
                confluencePassword.setText("");
                confluencePasswordHint.setText(entry.hasPassword()
                        ? "Passwort bzw. Token steht in KeePass; nur zum Ändern eintragen."
                        : "Im Eintrag fehlt das Passwort bzw. Token.");
                String alias = entry.field(PasswordEntry.CERT_ALIAS);
                certificateAlias = alias == null ? "" : alias.trim();
                certificateLabel.setText(certificateAlias.isEmpty() ? "kein Zertifikat" : certificateAlias);
                withoutCertificate.setSelected(false);
                confluenceEntryStatus.setText(complete(entry) ? "Vorhandener Eintrag; die Zugangsdaten stehen in KeePass."
                        : "Im Eintrag fehlt noch etwas; bitte ergänzen, es wird beim Testen in KeePass gespeichert.");
            } finally {
                busy = false;
            }
            showConfluenceCredentials();
            // Alles da, Zertifikat aus dem Eintrag: gleich prüfen, wie beim KI-Dienst.
            if (complete(entry) && !certificateAlias.isEmpty()) {
                testConfluence();
            }
        });
    }

    /** Vollständig heißt: URL und Passwort bzw. Token; ohne Benutzername meldet sich der Client mit Token an. */
    private static boolean complete(PasswordEntry entry) {
        return entry != null && !entry.url().trim().isEmpty() && entry.hasPassword();
    }

    /**
     * Status eines gelesenen Eintrags (was vorhanden ist, ohne Secrets) und darunter die Felder: bei neuem Eintrag
     * und nach „Zugangsdaten ändern“ alle, bei einem unvollständigen nur die fehlenden (etwa die URL). Was hier
     * eingetragen wird, schreibt „Verbindung testen“ in den KeePass-Eintrag, wie den Zertifikatsalias.
     */
    private void showConfluenceCredentials() {
        String title = text(confluenceEntry);
        boolean existing = confluenceRead != null;
        boolean complete = complete(confluenceRead);
        boolean summary = existing && !confluenceEditing && !confluenceReading;
        boolean manual = entriesLoaded && !title.isEmpty() && !confluenceReading && (!complete || confluenceEditing);
        if (summary) {
            boolean hasUrl = !confluenceRead.url().trim().isEmpty();
            state(confluenceHasUrl, hasUrl, hasUrl ? "URL vorhanden: " + confluenceRead.url()
                    : "URL fehlt: bitte unten eintragen, sie wird im KeePass-Eintrag gespeichert");
            String user = confluenceRead.userName().trim();
            state(confluenceHasUser, true, user.isEmpty()
                    ? "Kein Benutzername: Anmeldung mit persönlichem Zugriffstoken" : "Benutzername vorhanden");
            boolean hasSecret = confluenceRead.hasPassword();
            state(confluenceHasSecret, hasSecret, !hasSecret
                    ? "Passwort bzw. Token fehlt: bitte unten eintragen, es wird im KeePass-Eintrag gespeichert"
                    : user.isEmpty() ? "Token vorhanden" : "Passwort bzw. Token vorhanden");
        }
        boolean all = !existing || confluenceEditing;
        manualRowVisible(0, all || confluenceRead.url().trim().isEmpty());
        manualRowVisible(1, all);
        manualRowVisible(2, all || !confluenceRead.hasPassword());
        manualRowVisible(3, all || !confluenceRead.hasPassword());
        confluenceSummary.setVisible(summary);
        confluenceManual.setVisible(manual);
        updateButtons();
    }

    private void manualRowVisible(int row, boolean visible) {
        for (JComponent part : manualRows.get(row)) {
            part.setVisible(visible);
        }
    }

    /** Eine Statuszeile mit Haken oder Kreuz (StrokeIcon), ohne Secret. */
    private static void state(JLabel label, boolean ok, String text) {
        label.setIcon(ok ? new CheckGlyph() : new CrossGlyph());
        label.setText(text);
    }

    private void chooseCertificate() {
        String chosen = setup.chooseCertificate(this, certificateAlias);
        if (chosen != null && !chosen.trim().isEmpty()) {
            certificateAlias = chosen.trim();
            certificateLabel.setText(certificateAlias);
            withoutCertificate.setSelected(false);
            confluenceOk = false;
            updateButtons();
        }
    }

    private void testConfluence() {
        final String title = text(confluenceEntry);
        final String url = confluenceUrl.getText().trim();
        final String user = confluenceUser.getText().trim();
        char[] typed = confluencePassword.getPassword();
        char[] password = typed.length > 0 ? typed : confluenceRead == null ? null : confluenceRead.copyPassword();
        wipe(typed == password ? null : typed);
        if (title.isEmpty() || url.isEmpty() || password == null || password.length == 0) {
            wipe(password);
            fail(STEP_CONFLUENCE, "Für Confluence werden ein Titel für den KeePass-Eintrag, die URL und Passwort "
                    + "bzw. Token gebraucht.");
            return;
        }
        if (certificateAlias.isEmpty() && !withoutCertificate.isSelected()) {
            wipe(password);
            fail(STEP_CONFLUENCE, "Bitte ein Client-Zertifikat wählen oder ausdrücklich ohne Zertifikat fortfahren.");
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>(confluenceRead == null
                ? Collections.<String, String>emptyMap() : confluenceRead.fields());
        if (confluenceRead == null) {
            fields.put(PasswordEntry.CATEGORY, "Confluence");
        }
        if (!certificateAlias.isEmpty()) {
            fields.put(PasswordEntry.CERT_ALIAS, certificateAlias);
        } else {
            fields.remove(PasswordEntry.CERT_ALIAS);
        }
        final PasswordEntry entry = new PasswordEntry(title, user, url, fields, password);
        wipe(password);
        clearError(STEP_CONFLUENCE);
        confluenceOk = false;
        setBusy(true);
        if (changed(entry, typed.length > 0)) {
            confluenceStatus.setText("Speichere den Eintrag in KeePass …");
            store().save(entry, (ignored, error) -> {
                if (error != null) {
                    entry.wipe();
                    setBusy(false);
                    fail(STEP_CONFLUENCE, "Der Eintrag ließ sich nicht in KeePass speichern: " + error);
                    return;
                }
                addEntry(new PasswordEntry(title, user, url, entry.fields(), null));
                confluenceRead = new PasswordEntry(title, user, url, entry.fields(), entry.copyPassword());
                busy = true;
                confluencePassword.setText("");
                busy = false;
                runConfluenceTest(entry);
            });
        } else {
            runConfluenceTest(entry);
        }
    }

    private boolean changed(PasswordEntry entry, boolean newPassword) {
        if (confluenceRead == null || newPassword) {
            return true;
        }
        return !confluenceRead.url().equals(entry.url()) || !confluenceRead.userName().equals(entry.userName())
                || !trimmed(confluenceRead.field(PasswordEntry.CERT_ALIAS)).equals(certificateAlias);
    }

    private void runConfluenceTest(final PasswordEntry entry) {
        confluenceStatus.setText("Teste Confluence (Spaces abrufen) …");
        store().testConnection(entry, (message, error) -> {
            entry.wipe();
            setBusy(false);
            if (error != null) {
                confluenceStatus.setText("Verbindung fehlgeschlagen.");
                fail(STEP_CONFLUENCE, error);
                return;
            }
            confluenceOk = true;
            confluence = new ConfluenceSetup(entry.title(), entry.url(), entry.userName(), certificateAlias);
            confluenceStatus.setText(message == null ? "Verbindung erfolgreich." : message);
            updateButtons();
        });
    }

    // --- Schritt 5: Abschluss

    private void runFinalCheck() {
        done[STEP_FINISH] = false;
        clearError(STEP_FINISH);
        checkRows.removeAll();
        // Confluence ist optional: übersprungen ({@code null}) prüft der Abschluss es nicht und legt keine Quelle an.
        final boolean withoutConfluence = confluence == null;
        form = collect();
        finishStatus.setText("Speichere und prüfe …");
        setBusy(true);
        setup.finish(form, confluence, checks -> {
            setBusy(false);
            lastChecks = checks == null ? Collections.<SetupCheck>emptyList() : checks;
            showChecks(lastChecks);
            boolean allOk = !lastChecks.isEmpty();
            StringBuilder failed = new StringBuilder();
            for (SetupCheck check : lastChecks) {
                if (!check.isOk()) {
                    allOk = false;
                    failed.append(check.label()).append(": ").append(check.detail()).append('\n');
                }
            }
            done[STEP_FINISH] = allOk;
            finishStatus.setText(allOk ? withoutConfluence ? CONFLUENCE_SKIPPED : "Alles betriebsbereit."
                    : "Noch nicht betriebsbereit.");
            if (!allOk) {
                fail(STEP_FINISH, failed.length() == 0 ? "Die Prüfung lieferte kein Ergebnis."
                        : failed.toString().trim());
            } else {
                updateButtons();
            }
        });
    }

    List<SetupCheck> lastChecks() {
        return lastChecks;
    }

    private void showChecks(List<SetupCheck> checks) {
        checkRows.removeAll();
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 0, 2, 8);
        int row = 0;
        for (SetupCheck check : checks) {
            c.gridx = 0;
            c.gridy = row;
            c.weightx = 0;
            JLabel icon = new JLabel(check.isOk() ? new CheckGlyph() : new CrossGlyph());
            icon.setForeground(check.isOk() ? ResearchUiPalette.WAVE_GREEN : ResearchUiPalette.DANGER_RED);
            checkRows.add(icon, c);
            c.gridx = 1;
            c.weightx = 1;
            JLabel label = new JLabel(check.label() + (check.isOk() || check.detail().isEmpty() ? ""
                    : " (" + shorten(check.detail()) + ")"));
            label.setForeground(palette.getInk());
            label.setFont(ResearchUiTypography.regular(12.5f));
            label.setToolTipText(check.detail().isEmpty() ? null : check.detail());
            checkRows.add(label, c);
            row++;
        }
        checkRows.revalidate();
        checkRows.repaint();
    }

    private void finish() {
        if (!done[STEP_FINISH]) {
            return;
        }
        if (step == STEP_SPEECH && done[STEP_SPEECH] && !saveSpeechChoice()) {
            return;
        }
        complete();
    }

    // --- Schritt 6: Spracheingabe (optional)

    /** Fertig ohne lokale Spracheingabe; die Grundeinrichtung ist mit der Abschlussprüfung schon gespeichert. */
    private void skipSpeech() {
        if (!done[STEP_FINISH]) {
            return;
        }
        complete();
    }

    /**
     * Die lokalen Modelle der Kategorie Spracheingabe aus dem Modellverzeichnis des Entwurfs (installierte zuerst,
     * dann Vorschläge), wie im Reiter „Lokale Modelle“.
     *
     * @param prefer die Kennungen, die vor dem Laden schon installiert waren ({@code null}: keine Installation lief);
     *               ein danach neu installiertes Modell wird gewählt
     */
    private void loadSpeechModels(final List<String> prefer) {
        clearError(STEP_SPEECH);
        speechStatus.setText("Suche lokale Modelle für die Spracheingabe …");
        setBusy(true);
        settings.localVoices(collect(), new LocalVoiceOffersCallback() {
            @Override
            public void offers(List<LocalVoiceOffer> offers) {
                setBusy(false);
                speechLoaded = true;
                speechLoadFailed = false;
                showSpeechOffers(offers, prefer);
            }

            @Override
            public void failed(String message) {
                setBusy(false);
                // beim nächsten Betreten erneut laden; „keine Modelle“ wäre hier falsch
                speechLoaded = false;
                speechLoadFailed = true;
                speechOffers = Collections.emptyList();
                busy = true;
                try {
                    speechModel.setModel(new DefaultComboBoxModel<String>());
                } finally {
                    busy = false;
                }
                speechStatus.setText("Die Modelle für die Spracheingabe sind nicht geladen.");
                fail(STEP_SPEECH, "Die Angebote für die Spracheingabe ließen sich nicht ermitteln: "
                        + (message == null || message.trim().isEmpty() ? "unbekannter Grund." : message.trim())
                        + " „Erneut versuchen“ lädt sie neu; Überspringen geht immer.");
            }
        });
    }

    private void showSpeechOffers(List<LocalVoiceOffer> offers, List<String> prefer) {
        List<LocalVoiceOffer> installed = new ArrayList<LocalVoiceOffer>();
        List<LocalVoiceOffer> offered = new ArrayList<LocalVoiceOffer>();
        for (LocalVoiceOffer offer : offers == null ? Collections.<LocalVoiceOffer>emptyList() : offers) {
            if (offer.category() == ModelCategory.STT) {
                (offer.isInstalled() ? installed : offered).add(offer);
            }
        }
        List<LocalVoiceOffer> all = new ArrayList<LocalVoiceOffer>(installed);
        all.addAll(offered);
        speechOffers = all;
        List<String> labels = new ArrayList<String>();
        for (LocalVoiceOffer offer : all) {
            labels.add(speechLabel(offer));
        }
        LocalVoiceOffer chosen = preferredSpeechOffer(installed, offered, prefer);
        busy = true;
        try {
            speechModel.setModel(new DefaultComboBoxModel<String>(labels.toArray(new String[0])));
            speechModel.setSelectedIndex(chosen == null ? -1 : all.indexOf(chosen));
        } finally {
            busy = false;
        }
        if (all.isEmpty()) {
            speechStatus.setText("Kein lokales Modell für die Spracheingabe angeboten; bitte überspringen.");
            updateButtons();
            return;
        }
        showSpeechChoice();
    }

    /** Neu installiert vor bereits gewählt vor erstem installierten vor erstem Vorschlag. */
    private LocalVoiceOffer preferredSpeechOffer(List<LocalVoiceOffer> installed, List<LocalVoiceOffer> offered,
                                                List<String> prefer) {
        if (prefer != null) {
            for (LocalVoiceOffer offer : installed) {
                if (!prefer.contains(offer.id())) {
                    return offer;
                }
            }
        }
        String current = form.modelSelection(ModelCategory.STT.key());
        for (LocalVoiceOffer offer : installed) {
            if (settings.localVoiceSelection(offer.id()).equals(current)) {
                return offer;
            }
        }
        if (!installed.isEmpty()) {
            return installed.get(0);
        }
        return offered.isEmpty() ? null : offered.get(0);
    }

    private static String speechLabel(LocalVoiceOffer offer) {
        return offer.displayName() + (offer.isInstalled() ? " (installiert)" : "");
    }

    private LocalVoiceOffer selectedSpeechOffer() {
        int index = speechModel.getSelectedIndex();
        return index < 0 || index >= speechOffers.size() ? null : speechOffers.get(index);
    }

    private void showSpeechChoice() {
        clearError(STEP_SPEECH);
        LocalVoiceOffer chosen = selectedSpeechOffer();
        if (chosen == null) {
            speechStatus.setText("");
        } else if (chosen.isInstalled()) {
            speechStatus.setText("Installiert; „Fertig“ setzt es als Modell für die Spracheingabe.");
        } else {
            speechStatus.setText("Noch nicht installiert; „Installieren“ lädt es herunter. Bitte kurz warten oder "
                    + "überspringen.");
        }
        updateButtons();
    }

    /** Installiert das gewählte Modell über den vorhandenen Weg des Reiters „Lokale Modelle“ und wählt es danach. */
    private void installSpeech() {
        final LocalVoiceOffer chosen = selectedSpeechOffer();
        if (chosen == null || chosen.isInstalled() || speechDownloading) {
            return;
        }
        final List<String> before = new ArrayList<String>();
        for (LocalVoiceOffer offer : speechOffers) {
            if (offer.isInstalled()) {
                before.add(offer.id());
            }
        }
        clearError(STEP_SPEECH);
        speechDownloading = true;
        speechProgress.setIndeterminate(true);
        speechProgress.setValue(0);
        speechProgress.setVisible(true);
        speechStatus.setText("Installiere " + chosen.displayName() + " …");
        updateButtons();
        settings.installLocalVoice(collect(), chosen.id(), new LocalVoiceInstallProgress() {
            @Override
            public void progress(String line) {
                speechProgress.setIndeterminate(true);
                speechStatus.setText(line);
            }

            @Override
            public void file(String name, int number, int count, long done, long total) {
                int percent = DownloadText.percent(done, total);
                speechProgress.setIndeterminate(percent < 0);
                speechProgress.setValue(Math.max(0, percent));
                speechStatus.setText(DownloadText.line(name, number, count, done, total));
            }

            @Override
            public void finished(boolean installed, String message) {
                speechDownloading = false;
                speechProgress.setVisible(false);
                if (!installed) {
                    speechStatus.setText("Installation fehlgeschlagen.");
                    fail(STEP_SPEECH, "Das Modell ließ sich nicht installieren: "
                            + (message == null || message.isEmpty() ? "unbekannter Grund." : message)
                            + " Erneut versuchen oder überspringen.");
                    return;
                }
                speechStatus.setText(chosen.displayName() + " ist installiert.");
                loadSpeechModels(before);
            }
        });
    }

    /**
     * Speichert die Wahl wie der Reiter „Modelle“ ({@code model.stt} über die Modellauswahl des Entwurfs). Die
     * übrige Konfiguration hat die Abschlussprüfung schon geschrieben; hier kommt nur die Auswahl dazu.
     */
    private boolean saveSpeechChoice() {
        LocalVoiceOffer chosen = selectedSpeechOffer();
        if (chosen == null || !chosen.isInstalled()) {
            return true;
        }
        SettingsForm withSpeech = collect().toBuilder()
                .modelSelection(ModelCategory.STT.key(), settings.localVoiceSelection(chosen.id())).build();
        try {
            settings.save(withSpeech);
        } catch (IOException | RuntimeException e) {
            fail(STEP_SPEECH, "Die Auswahl der Spracheingabe ließ sich nicht speichern ("
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage())
                    + "). Überspringen schließt die Einrichtung ohne sie ab.");
            return false;
        }
        form = withSpeech;
        return true;
    }

    // --- Netzwerk

    private void editNetwork() {
        SettingsForm edited = setup.editNetwork(this, collect());
        if (edited != null) {
            form = edited;
            if (step == STEP_SERVICE) {
                testService();
            } else if (step == STEP_CONFLUENCE) {
                clearError(STEP_CONFLUENCE);
                updateButtons();
            }
        }
    }

    // ------------------------------------------------------------------ Entwurf

    private void load(SettingsForm initial) {
        keePassHost.setText(initial.keePassHost());
        keePassPort.setText(initial.keePassPort());
        keePassClient.setText(initial.keePassClientDisplayName());
        busy = true;
        try {
            setText(serviceEntry, stripPrefix(initial.chatApiKeyRef()));
            setText(chatModel, initial.chatModel());
            setText(embeddingModel, initial.embeddingModel());
            embeddingDimension.setText(initial.embeddingDimension());
        } finally {
            busy = false;
        }
        certificateLabel.setText("kein Zertifikat");
    }

    /** Der Entwurf mit den Werten der Schritte; Pairing-Schlüssel immer dauerhaft in der Datei, KeePass an. */
    private SettingsForm collect() {
        SettingsForm.Builder b = form.toBuilder()
                .keePassEnabled(true)
                .keePassHost(keePassHost.getText())
                .keePassPort(keePassPort.getText())
                .keePassClientDisplayName(keePassClient.getText())
                .keePassPairingKeyStore(SettingsForm.PAIRING_KEY_STORE_FILE);
        String entry = text(serviceEntry);
        if (!entry.isEmpty()) {
            b.chatApiKeyRef(KEEPASS_PREFIX + entry);
        }
        b.chatModel(text(chatModel)).embeddingModel(text(embeddingModel))
                .embeddingDimension(embeddingDimension.getText());
        return b.build();
    }

    private static SettingsForm withKeePassDefaults(SettingsForm initial) {
        SettingsForm.Builder b = initial.toBuilder();
        if (initial.keePassHost().isEmpty()) {
            b.keePassHost("127.0.0.1");
        }
        if (initial.keePassPort().isEmpty()) {
            b.keePassPort("12546");
        }
        if (initial.keePassClientDisplayName().isEmpty()) {
            b.keePassClientDisplayName("Enterprise AI Client");
        }
        return b.build();
    }

    /** Für den Verbindungstest vor der Modellwahl: Platzhalter, damit der Entwurf lädt (wie die Modellabfrage). */
    private static SettingsForm probeForm(SettingsForm form) {
        SettingsForm.Builder probe = form.toBuilder();
        if (form.chatModel().isEmpty()) {
            probe.chatModel(MODEL_PLACEHOLDER);
        }
        if (form.embeddingModel().isEmpty()) {
            probe.embeddingModel(MODEL_PLACEHOLDER);
        }
        if (!positive(form.embeddingDimension())) {
            probe.embeddingDimension("1");
        }
        return probe.build();
    }

    private PasswordStore store() {
        return settings.passwordStore(collect());
    }

    private void setBusy(boolean value) {
        busy = value;
        setCursor(value ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR) : null);
        updateButtons();
    }

    private boolean titleExists(String title) {
        for (PasswordEntry entry : entries) {
            if (entry.title().equals(title)) {
                return true;
            }
        }
        return false;
    }

    private void addEntry(PasswordEntry entry) {
        List<PasswordEntry> updated = new ArrayList<PasswordEntry>();
        for (PasswordEntry existing : entries) {
            if (!existing.title().equals(entry.title())) {
                updated.add(existing);
            }
        }
        updated.add(entry);
        entries = updated;
        busy = true;
        try {
            fillEntryChoices();
        } finally {
            busy = false;
        }
    }

    /** Ein eindeutiger Titel, der {@code needle} enthält, sonst {@code null}. */
    private String unique(String needle) {
        String found = null;
        for (PasswordEntry entry : entries) {
            if (contains(entry.title(), needle)) {
                if (found != null) {
                    return null;
                }
                found = entry.title();
            }
        }
        return found;
    }

    private static boolean contains(String text, String needle) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String stripPrefix(String ref) {
        String value = ref == null ? "" : ref.trim();
        return value.toLowerCase(Locale.ROOT).startsWith(KEEPASS_PREFIX) ? value.substring(KEEPASS_PREFIX.length()).trim()
                : value.toLowerCase(Locale.ROOT).startsWith("ask:") ? "" : value;
    }

    static boolean positive(String text) {
        try {
            return text != null && Integer.parseInt(text.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }

    private static String text(JComboBox<String> combo) {
        Object item = combo.isEditable() ? combo.getEditor().getItem() : combo.getSelectedItem();
        return item == null ? "" : item.toString().trim();
    }

    private static void setText(JComboBox<String> combo, String value) {
        String text = value == null ? "" : value;
        combo.getEditor().setItem(text);
        combo.setSelectedItem(text);
    }

    private void setChoices(JComboBox<String> combo, List<String> items, String selected) {
        boolean was = busy;
        busy = true;
        try {
            List<String> values = new ArrayList<String>(items);
            if (selected != null && !selected.isEmpty() && !values.contains(selected)) {
                values.add(0, selected);
            }
            combo.setModel(new DefaultComboBoxModel<String>(values.toArray(new String[0])));
            setText(combo, selected);
        } finally {
            busy = was;
        }
    }

    private static void wipe(char[] secret) {
        if (secret != null) {
            Arrays.fill(secret, '\0');
        }
    }

    private static String shorten(String text) {
        String line = text.replace('\n', ' ').trim();
        return line.length() > 120 ? line.substring(0, 119) + "…" : line;
    }

    String finishStatusText() {
        return finishStatus.getText();
    }

    String confluenceEntryStatusText() {
        return confluenceEntryStatus.getText();
    }

    boolean confluenceFieldsShown() {
        return confluenceManual.isVisible();
    }

    /** Ob die Feldzeile (0 URL, 1 Benutzer, 2 Passwort) gerade angeboten wird. */
    boolean confluenceFieldShown(int row) {
        return confluenceManual.isVisible() && manualRows.get(row).get(0).isVisible();
    }

    boolean confluenceSummaryShown() {
        return confluenceSummary.isVisible();
    }

    String confluenceSummaryText() {
        return confluenceHasUrl.getText() + "\n" + confluenceHasUser.getText() + "\n" + confluenceHasSecret.getText();
    }

    ComposerButton confluenceChangeButton() {
        return confluenceChange;
    }

    private JLabel statusLabel() {
        JLabel label = new JLabel(" ");
        label.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
        label.setFont(ResearchUiTypography.regular(12f));
        return label;
    }

    private JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(palette.getInk());
        label.setFont(ResearchUiTypography.regular(12.5f));
        return label;
    }

    private static JPanel transparent(java.awt.LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    private static JPanel buttonRow(JComponent button) {
        JPanel row = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        row.add(button);
        return row;
    }

    // --- Testzugänge

    JTextField keePassHostField() {
        return keePassHost;
    }

    ComposerButton pairButton() {
        return pairButton;
    }

    JComboBox<String> serviceEntryChoice() {
        return serviceEntry;
    }

    JTextField serviceUrlField() {
        return serviceUrl;
    }

    JPasswordField serviceKeyField() {
        return serviceKey;
    }

    ComposerButton serviceSaveButton() {
        return serviceSave;
    }

    JComboBox<String> chatModelChoice() {
        return chatModel;
    }

    JComboBox<String> embeddingModelChoice() {
        return embeddingModel;
    }

    JTextField embeddingDimensionField() {
        return embeddingDimension;
    }

    JComboBox<String> confluenceEntryChoice() {
        return confluenceEntry;
    }

    JTextField confluenceUrlField() {
        return confluenceUrl;
    }

    JTextField confluenceUserField() {
        return confluenceUser;
    }

    JPasswordField confluencePasswordField() {
        return confluencePassword;
    }

    JCheckBox withoutCertificateBox() {
        return withoutCertificate;
    }

    ComposerButton confluenceTestButton() {
        return confluenceTest;
    }

    ComposerButton nextButton() {
        return nextButton;
    }

    ComposerButton backButton() {
        return backButton;
    }

    ComposerButton finishButton() {
        return finishButton;
    }

    ComposerButton cancelButton() {
        return cancelButton;
    }

    ComposerButton skipButton() {
        return skipButton;
    }

    JComboBox<String> speechModelChoice() {
        return speechModel;
    }

    ComposerButton speechInstallButton() {
        return speechInstall;
    }

    ComposerButton speechRetryButton() {
        return speechRetry;
    }

    JProgressBar speechProgressBar() {
        return speechProgress;
    }

    String speechStatusText() {
        return speechStatus.getText();
    }

    ComposerButton networkButton() {
        return networkButton;
    }

    String certificateAlias() {
        return certificateAlias;
    }

    /** Das Bild des Zauberers eines Schritts, relativ zu {@link SetupWizardMascot}. */
    static String mascotResource(int index) {
        return STEP_MASCOTS[index];
    }

    SetupStepTrail trail() {
        return trail;
    }

    /** Der Zauberer des Schritts (in der Reihenfolge der Schritte). */
    SetupWizardMascot mascotOf(int index) {
        return mascots.get(index);
    }

    // ------------------------------------------------------------------ Hilfsklassen

    /** Zeilen eines Schritts: Überschrift, Hinweis, dann Beschriftung und Feld. */
    private final class Rows {
        private final JPanel panel = transparent(new GridBagLayout());
        private int row;

        Rows(String heading) {
            panel.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            JLabel title = new JLabel(heading);
            title.setFont(ResearchUiTypography.semiBold(15f));
            title.setForeground(palette.getInk());
            GridBagConstraints c = constraints(0);
            c.gridwidth = 2;
            c.insets = new Insets(0, 0, 6, 0);
            panel.add(title, c);
            row++;
        }

        void note(String text) {
            JTextArea note = new JTextArea(text);
            note.setEditable(false);
            note.setFocusable(false);
            note.setLineWrap(true);
            note.setWrapStyleWord(true);
            note.setOpaque(false);
            note.setForeground(ResearchUiPalette.LIGHT_CONTROL_TEXT);
            note.setFont(ResearchUiTypography.regular(12f));
            GridBagConstraints c = constraints(0);
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 8, 0);
            panel.add(note, c);
            row++;
        }

        void field(String label, JComponent component) {
            GridBagConstraints c = constraints(0);
            c.insets = new Insets(3, 0, 3, 10);
            if (!label.isEmpty()) {
                panel.add(fieldLabel(label), c);
            }
            c = constraints(1);
            c.insets = new Insets(3, 0, 3, 0);
            c.fill = component instanceof JTextField || component instanceof JComboBox
                    ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            c.weightx = 1;
            panel.add(component, c);
            row++;
        }

        void wide(JComponent component) {
            GridBagConstraints c = constraints(0);
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(3, 0, 3, 0);
            panel.add(component, c);
            row++;
        }

        private GridBagConstraints constraints(int column) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = column;
            c.gridy = row;
            c.anchor = GridBagConstraints.WEST;
            return c;
        }

        JPanel panel() {
            JPanel page = transparent(new BorderLayout());
            page.add(panel, BorderLayout.NORTH);
            int index = mascots.size();
            SetupWizardMascot.Art stepArt = SetupWizardMascot.Art.load(STEP_MASCOTS[index]);
            SetupWizardMascot mascot = new SetupWizardMascot(stepArt != null ? stepArt : SetupWizardMascot.Art.load(),
                    MASCOT_HEIGHTS[index], MASCOT_PLACEMENTS[index], SetupWizardMascot.DEFAULT_GAP,
                    SetupWizardMascot.DEFAULT_SIDE_INSET, palette);
            mascot.setOnFigureClicked(point -> merlinMenu().show(mascot, point.x, point.y));
            mascots.add(mascot);
            page.add(mascot, BorderLayout.CENTER);
            return page;
        }
    }

    /** Haken der Abschlussprüfung (Java2D, kein Schriftzeichen). */
    private static final class CheckGlyph extends StrokeIcon {
        @Override
        protected void paint(Graphics2D g2) {
            g2.drawPolyline(new int[] {2, 6, 13}, new int[] {8, 12, 3}, 3);
        }
    }

    /** Kreuz der Abschlussprüfung (Java2D, kein Schriftzeichen). */
    private static final class CrossGlyph extends StrokeIcon {
        @Override
        protected void paint(Graphics2D g2) {
            g2.drawLine(3, 3, 12, 12);
            g2.drawLine(12, 3, 3, 12);
        }
    }

    /** Ein DocumentListener, der bei jeder Änderung dieselbe Aktion ausführt. */
    private static final class SimpleDocumentListener implements javax.swing.event.DocumentListener {
        private final Runnable action;

        SimpleDocumentListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void insertUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void removeUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }

        @Override
        public void changedUpdate(javax.swing.event.DocumentEvent e) {
            action.run();
        }
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.DownloadProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceOffersCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.NlpModelOffer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicComboBoxUI;
import com.aresstack.enterpriseai.ui.comic.control.ComicSectionPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;
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
import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.text.View;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
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
            "Lokale KI"};
    static final int STEP_KEEPASS = 0;
    static final int STEP_SERVICE = 1;
    static final int STEP_MODELS = 2;
    static final int STEP_CONFLUENCE = 3;
    static final int STEP_FINISH = 4;
    /**
     * Optional nach der bestandenen Abschlussprüfung: lokale KI- und Sprachressourcen, also ein Modell für die
     * Spracheingabe (STT) und die OpenNLP-Modelle der Text- und Sprachanalyse.
     */
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
    private static final int[] MASCOT_HEIGHTS = {230, 200, 170, 110, 140, 230};

    /** Der Standardeintrag des KI-Dienstes in KeePass. */
    static final String DEFAULT_SERVICE_ENTRY = "Enterprise AI API";
    static final String DEFAULT_CONFLUENCE_ENTRY = "Confluence";
    static final String ENTRIES_REFRESH_HINT = "KeePass-Einträge neu laden";
    /** Hint for an incomplete existing entry; the wizard never completes it itself. */
    static final String INCOMPLETE_HINT = " Bitte den Eintrag direkt in KeePass ergänzen und dann die Einträge neu "
            + "laden (⟳), oder einen neuen Titel eintragen.";
    static final String NEW_ENTRY_HINT = "(neuer Eintrag)";
    private static final String KEEPASS_PREFIX = "keepass:";
    /** Zugang wie im Einstellungen-Dialog ({@code VaultAccess}): KeePass oder Manuell. */
    static final String MODE_KEEPASS = "KeePass";
    static final String MODE_MANUAL = "Manuell";
    /** Die manuelle Referenz des KI-Dienstes, dieselbe wie im Reiter „KI-Dienst“ der Einstellungen. */
    static final String MANUAL_SERVICE_REF = "ask:KI-Dienst";
    static final String SAVE_API_KEY = "API-Key speichern";
    static final String REMEMBER_USER = "Benutzername merken";
    /** Ohne Stimme bleibt eine Zeile der Übergabe so lange stehen: je Zeichen, mindestens {@link #MIN_READING_MILLIS}. */
    private static final int READING_MILLIS_PER_CHARACTER = 60;
    private static final int MIN_READING_MILLIS = 1800;
    private static final String SPEECH_CANCEL_HINT = "Download abbrechen";
    static final String SPEECH_CANCELLED = "Download abgebrochen. Das Modell wurde nicht installiert.";
    private static final String SKIP_STEP_HINT = "Ohne Prüfung zum nächsten Schritt; Eingaben bleiben erhalten";
    static final String CONFLUENCE_SKIPPED = "Betriebsbereit. Confluence ist übersprungen und lässt sich später "
            + "in der Seitenleiste unter Wissensquellen einrichten.";
    static final String NLP_INSTALLED = "installiert";
    static final String NLP_IN_ARCHIVE = "im Offline-Paket";
    static final String NLP_DOWNLOAD = "Download";
    static final String NLP_FAILED = "fehlgeschlagen";
    /** „Ausgewählte installieren“: nur die angehakten, noch fehlenden Ressourcen. */
    static final String INSTALL_SELECTED = "Ausgewählte installieren";
    private static final String INSTALL_SELECTED_HINT = "Installiert nur die angehakten, noch fehlenden Ressourcen: "
            + "aus dem Offline-Paket neben dem Jar, sonst per Download";
    static final String NLP_STOPPED = "Installation angehalten; bereits installierte Modelle bleiben.";
    private static final String INSTALL_ALL_HINT = "Installiert alle fehlenden Ressourcen dieses Schritts: zuerst "
            + "aus dem Offline-Paket neben dem Jar, sonst per Download. Von der Spracheingabe nur das gewählte Modell, "
            + "unabhängig von den Häkchen";
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
    private final JTextArea errorText = new WrappedErrorText();
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
    /** Counts entry list reads; a response only counts if no newer read was requested since. */
    private int entriesGeneration;
    private final ComposerButton serviceEntryRefresh =
            ComposerButton.iconButton(ComposerIcons.refresh(), ENTRIES_REFRESH_HINT);
    private final ComposerButton confluenceEntryRefresh =
            ComposerButton.iconButton(ComposerIcons.refresh(), ENTRIES_REFRESH_HINT);
    /** The title whose status the service and Confluence steps currently show. */
    private String serviceShownTitle;
    private String confluenceShownTitle;

    // Schritt 2: KI-Dienst
    private final JComboBox<String> serviceAccess = accessChoice();
    /** Beschriftung und Felder des Zugangs „KeePass“ bzw. „Manuell“, je Modus ein- und ausgeblendet. */
    private final List<JComponent> serviceKeePassParts = new ArrayList<JComponent>();
    private final List<JComponent> serviceManualParts = new ArrayList<JComponent>();
    private final JTextField manualServiceUrl = new JTextField(28);
    private final JPasswordField manualServiceKey = new JPasswordField(28);
    private final JCheckBox saveApiKey = new JCheckBox(SAVE_API_KEY);
    private final JLabel saveApiKeyNote = statusLabel();
    /** Die Basis-URL, die beim Zugang „KeePass“ die des Eintrags überschreibt (aus den Einstellungen übernommen). */
    private String keePassBaseUrl = "";
    /** KeePass wurde in diesem Assistenten gekoppelt bzw. gelesen: dann bleibt es eingeschaltet. */
    private boolean keePassUsed;
    private final JComboBox<String> serviceEntry = comic(new JComboBox<String>());
    private final JLabel serviceEntryStatus = statusLabel();
    private final JTextField serviceUrl = new JTextField(28);
    private final JPasswordField serviceKey = new JPasswordField(28);
    private final JPanel serviceMissing = transparent(new GridBagLayout());
    private final ComposerButton serviceSave;
    private final ComposerButton serviceTest;
    private final JLabel serviceStatus = statusLabel();
    private PasswordEntry serviceRead;
    private boolean serviceEntryComplete;
    /**
     * Zählt Verbindungstests des KI-Dienstes; ein Ergebnis gilt nur, wenn seit seinem Start kein neuerer Test, kein
     * anderer Eintrag und kein Schließen dazwischenkam. So überschreibt eine verspätete Antwort nie einen neueren Stand.
     */
    private int serviceCheckGeneration;
    /** Ein Verbindungstest des KI-Dienstes läuft gerade (von Hand oder als Wiederprüfung). */
    private boolean serviceChecking;
    /** Die geplante Wiederprüfung nach einem vorübergehenden Ausfall, {@code null}: keine. */
    private Timer serviceRecheck;
    /** Wiederprüfungen seit dem letzten Test von Hand bzw. dem Betreten des Schritts. */
    private int serviceRechecks;
    /** Der letzte Fehlschlag betraf Proxy, TLS oder HTTP (dann hilft „Netzwerkeinstellungen…“), nicht nur KeePass. */
    private boolean serviceNetworkProblem;

    // Schritt 3: Modelle
    private final JComboBox<String> chatModel = comic(new JComboBox<String>());
    private final JComboBox<String> embeddingModel = comic(new JComboBox<String>());
    /** The chat's speech output ({@code model.tts}); never Merlin's own voice ({@code setup.merlin.*}). */
    private final JComboBox<String> speechOutputModel = comic(new JComboBox<String>());
    private final JComboBox<String> visionModel = comic(new JComboBox<String>());
    /** Optional re-ranker ({@code model.rerank}); its use in the RAG pipeline is separate (#87). */
    private final JComboBox<String> rerankModel = comic(new JComboBox<String>());
    private final JTextField embeddingDimension = new JTextField(8);
    private final JLabel modelsStatus = statusLabel();
    private final ComposerButton modelsRefresh;
    private final ComposerButton dimensionMeasure;
    private boolean modelsLoaded;
    /** Die Modellabfrage läuft schon im Hintergrund (nach dem erfolgreichen Verbindungstest). */
    private boolean modelsPreparing;
    private String measuredFor = "";

    // Schritt 4: Confluence
    private final JComboBox<String> confluenceAccess = accessChoice();
    private final List<JComponent> confluenceKeePassParts = new ArrayList<JComponent>();
    private final JCheckBox rememberUser = new JCheckBox(REMEMBER_USER);
    /** Zählt Confluence-Tests; ein Ergebnis gilt nur, wenn seither kein Wechsel des Zugangs kam. */
    private int confluenceGeneration;
    private final JComboBox<String> confluenceEntry = comic(new JComboBox<String>());
    private final JLabel confluenceEntryStatus = statusLabel();
    /** Stand eines vollständigen Eintrags (ohne Secrets) und „Zugangsdaten ändern“. */
    private final JPanel confluenceSummary = transparent(new GridBagLayout());
    private final JLabel confluenceHasUrl = statusLabel();
    private final JLabel confluenceHasUser = statusLabel();
    private final JLabel confluenceHasSecret = statusLabel();
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
    private String certificateAlias = "";
    private boolean confluenceOk;
    private ConfluenceSetup confluence;

    // Schritt 5: Abschluss
    private final JPanel checkRows = transparent(new GridBagLayout());
    private final JLabel finishStatus = statusLabel();
    private final ComposerButton finishRetry;
    private List<SetupCheck> lastChecks = Collections.emptyList();

    // Schritt 6: Spracheingabe (optional)
    private final JComboBox<String> speechModel = comic(new JComboBox<String>());
    private final ComposerButton speechInstall;
    /** Lädt die Angebote der Spracheingabe neu, wenn sie sich nicht ermitteln ließen (keine Installation). */
    private final ComposerButton speechRetry;
    /** Häkchen rechts neben der Spracheingabe: gilt immer nur für das gerade gewählte Modell. */
    private final JCheckBox speechCheck = comicCheck("speech-check");
    /** Das Modell, für das das Häkchen der Spracheingabe gesetzt wurde; {@code null}: keines. */
    private String speechCheckedId;
    /** Angehakte OpenNLP-Modelle (Kennungen). */
    private final java.util.Set<String> nlpChecked = new java.util.LinkedHashSet<String>();
    /** Thin bar with the stop cross and the file line; shown only while a download runs. */
    private final DownloadProgress speechProgress = new DownloadProgress(SPEECH_CANCEL_HINT);
    /** The running download, {@code null} when none runs; stopping it cancels the real transfer. */
    private InstallCancellation speechDownload;
    /** Set once the wizard closes; late callbacks of a stopped download must not touch it any more. */
    private boolean closed;
    private final JLabel speechStatus = statusLabel();
    private List<LocalVoiceOffer> speechOffers = Collections.emptyList();
    private boolean speechLoaded;
    private boolean speechDownloading;
    /** Die Angebote ließen sich nicht ermitteln (nicht dasselbe wie „keine Angebote“). */
    private boolean speechLoadFailed;
    /** Die OpenNLP-Modelle der Text- und Sprachanalyse, je Modell eine Zeile mit Stand. */
    private final JPanel nlpLines = transparent(new GridBagLayout());
    private List<NlpModelOffer> nlpOffers = Collections.emptyList();
    private final Map<String, String> nlpProblems = new LinkedHashMap<String, String>();
    private final ComposerButton installAll;
    /** OpenNLP-Modelle werden gerade nacheinander installiert. */
    private boolean nlpInstalling;
    /** Das X hat „Alles installieren“ angehalten: nach dem laufenden Modell kommt keines mehr. */
    private boolean nlpStopRequested;
    /** Nach den OpenNLP-Modellen noch das gewählte Spracheingabe-Modell installieren. */
    private boolean speechAfterNlp;

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
        this.serviceSave = new ComposerButton(null, "Neu in KeePass anlegen", false,
                "Legt einen neuen KeePass-Eintrag unter diesem Titel an; bestehende Einträge bleiben unverändert");
        this.serviceTest = ComposerButton.primary(null, "Verbindung testen", ResearchUiPalette.ACCENT_BLUE, null);
        this.modelsRefresh = new ComposerButton(null, "Modelle neu laden", false);
        this.dimensionMeasure = ComposerButton.iconButton(ComposerIcons.refresh(), "Dimension neu ermitteln");
        this.certificateChoose = new ComposerButton(null, "Zertifikat wählen…", false);
        this.confluenceTest = ComposerButton.primary(null, "Verbindung testen", ResearchUiPalette.ACCENT_BLUE, null);
        this.finishRetry = new ComposerButton(null, "Erneut prüfen", false);
        this.skipButton = new ComposerButton(null, "Überspringen", false, SKIP_STEP_HINT);
        this.speechInstall = ComposerButton.primary(null, INSTALL_SELECTED, ResearchUiPalette.ACCENT_BLUE,
                INSTALL_SELECTED_HINT);
        this.speechRetry = new ComposerButton(null, "Angebote neu laden", false,
                "Die Modelle für die Spracheingabe erneut ermitteln");
        this.installAll = ComposerButton.primary(null, "Alles installieren", ResearchUiPalette.ACCENT_BLUE,
                INSTALL_ALL_HINT);
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

    /** Title choice or new title input, with the "reload KeePass entries" button to its right. */
    private static JPanel entryRow(JComboBox<String> combo, ComposerButton refresh) {
        JPanel row = transparent(new BorderLayout(4, 0));
        row.add(combo, BorderLayout.CENTER);
        row.add(refresh, BorderLayout.EAST);
        return row;
    }

    private JPanel serviceStep() {
        Rows rows = new Rows("KI-Dienst");
        rows.note("KeePass: Der Eintrag des KI-Dienstes trägt den API-Key im Passwortfeld und die Basis-URL im "
                + "URL-Feld. Manuell: Basis-URL und API-Key hier eingeben. Der API-Key wird nie in die "
                + "Konfiguration geschrieben.");
        rows.field("Zugang", serviceAccess);
        serviceEntry.setEditable(true);
        serviceKeePassParts.addAll(rows.field("KeePass-Eintrag", entryRow(serviceEntry, serviceEntryRefresh)));
        serviceKeePassParts.addAll(rows.field("", serviceEntryStatus));
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
        manualServiceUrl.setToolTipText("Absolute http(s)-URL ohne Zugangsdaten, Query oder Fragment; meist bis /v1");
        serviceManualParts.addAll(rows.field("Basis-URL", manualServiceUrl));
        serviceManualParts.addAll(rows.field("API-Key", manualServiceKey));
        comicCheckBox(saveApiKey);
        serviceManualParts.addAll(rows.field("", saveApiKey));
        serviceManualParts.addAll(rows.field("", saveApiKeyNote));
        rows.field("", buttonRow(serviceTest));
        rows.field("", serviceStatus);
        return rows.panel();
    }

    private JPanel modelsStep() {
        Rows rows = new Rows("Modelle");
        rows.note("Die passenden Modelle wurden vom KI-Dienst vorausgewählt. Chat, Embeddings und Dimension sind "
                + "erforderlich; Sprachausgabe, Bildverständnis und Re-Ranker sind optional (Re-Ranker empfohlen).");
        chatModel.setEditable(true);
        embeddingModel.setEditable(true);
        speechOutputModel.setEditable(true);
        visionModel.setEditable(true);
        rerankModel.setEditable(true);
        embeddingDimension.setColumns(7);
        embeddingDimension.setToolTipText("Wird automatisch aus dem Embedding-Modell ermittelt; bei Bedarf editieren.");
        rows.wide(modelGrid());
        JPanel reload = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        reload.add(modelsRefresh);
        reload.add(modelsStatus);
        rows.wide(reload);
        return rows.panel();
    }

    private JPanel modelGrid() {
        JPanel grid = transparent(new GridBagLayout());
        addModelCell(grid, 0, 0, "Chat-Modell", chatModel);
        addModelCell(grid, 1, 0, "Sprachausgabe", speechOutputModel);
        addModelCell(grid, 0, 1, "Embedding-Modell", embeddingModel);
        addModelCell(grid, 1, 1, "Bildverständnis", visionModel);
        JPanel dimension = transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        dimension.add(embeddingDimension);
        dimension.add(javax.swing.Box.createHorizontalStrut(4));
        dimension.add(dimensionMeasure);
        addModelCell(grid, 0, 2, "Dimension", dimension);
        addModelCell(grid, 1, 2, "Re-Ranker", rerankModel);
        return grid;
    }

    private void addModelCell(JPanel grid, int column, int row, String label, JComponent component) {
        JPanel cell = transparent(new BorderLayout(0, 3));
        cell.add(fieldLabel(label), BorderLayout.NORTH);
        cell.add(component, BorderLayout.CENTER);
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = column;
        c.gridy = row;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.insets = new Insets(3, column == 0 ? 0 : 8, 3, column == 0 ? 8 : 0);
        grid.add(cell, c);
    }

    private JPanel confluenceStep() {
        Rows rows = new Rows("Confluence");
        rows.note("Confluence wird als Wissensquelle eingerichtet; alle Spaces stehen danach unter Wissensquellen "
                + "zur Auswahl. Mit Benutzername meldet sich der Client mit Passwort an, ohne Benutzername mit "
                + "einem persönlichen Zugriffstoken.");
        confluenceAccess.setToolTipText("Manuell: Passwort bzw. Token gilt nur für diese Sitzung und wird nie "
                + "gespeichert; der Benutzername darf vorgemerkt werden.");
        // Zugang und KeePass-Eintrag in einer Zeile, damit der Schritt nicht höher wird als ohne Wahl.
        confluenceEntry.setEditable(true);
        JPanel access = transparent(new GridBagLayout());
        GridBagConstraints at = new GridBagConstraints();
        at.anchor = GridBagConstraints.WEST;
        access.add(confluenceAccess, at);
        JLabel entryCaption = fieldLabel("Eintrag");
        at.insets = new Insets(0, 10, 0, 6);
        access.add(entryCaption, at);
        at.insets = new Insets(0, 0, 0, 0);
        at.fill = GridBagConstraints.HORIZONTAL;
        at.weightx = 1;
        access.add(confluenceEntry, at);
        at.insets = new Insets(0, 4, 0, 0);
        at.fill = GridBagConstraints.NONE;
        at.weightx = 0;
        access.add(confluenceEntryRefresh, at);
        confluenceKeePassParts.add(entryCaption);
        confluenceKeePassParts.add(confluenceEntry);
        confluenceKeePassParts.add(confluenceEntryRefresh);
        rows.field("Zugang", access);
        confluenceKeePassParts.addAll(rows.field("", confluenceEntryStatus));
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
        confluenceSummary.setVisible(false);
        rows.field("", confluenceSummary);
        manualRow(0, "Confluence-URL", confluenceUrl);
        manualRow(1, "Benutzername", confluenceUser);
        manualRow(2, "Passwort oder Token", confluencePassword);
        manualRow(3, "", confluencePasswordHint);
        confluenceManual.setVisible(false);
        rows.wide(confluenceManual);
        comicCheckBox(rememberUser);
        rememberUser.setSelected(true);
        rememberUser.setToolTipText("Nur der Benutzername wird bei der Quelle gespeichert und beim nächsten Start "
                + "vorbelegt; Passwort bzw. Token nie");
        rows.field("", rememberUser);
        JPanel certificate = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        certificate.add(certificateLabel);
        certificate.add(certificateChoose);
        rows.field("Client-Zertifikat", certificate);
        comicCheckBox(withoutCertificate);
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
        Rows rows = new Rows("Lokale KI- und Sprachressourcen");
        rows.note("Optional. Spracheingabe ohne Cloud-STT und Text- und Sprachanalyse mit OpenNLP laufen im Client. "
                + "Liegt ein Offline-Paket neben dem Jar, wird daraus installiert, sonst heruntergeladen. "
                + "Später unter Einstellungen → Modelle → Lokale Modelle.");
        speechCheck.setToolTipText("Das gewählte Modell der Spracheingabe zum Installieren vormerken");
        JPanel speechRow = transparent(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 10);
        speechRow.add(speechModel, c);
        c = new GridBagConstraints();
        c.gridx = 1;
        c.anchor = GridBagConstraints.EAST;
        speechRow.add(speechCheck, c);
        rows.stretched("Spracheingabe", speechRow);
        rows.stretched("Textanalyse", nlpLines);
        JPanel actions = transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(speechInstall);
        actions.add(installAll);
        actions.add(speechRetry);
        rows.field("", actions);
        speechProgress.setVisible(false);
        rows.stretched("", speechProgress);
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
        onTyped(serviceEntry, () -> {
            if (!text(serviceEntry).equals(serviceShownTitle)) {
                readServiceEntry();
            }
        });
        serviceEntryRefresh.addActionListener(e -> refreshEntries());
        serviceSave.addActionListener(e -> saveServiceEntry());
        serviceAccess.addActionListener(e -> {
            if (!busy) {
                serviceModeChanged();
            }
        });
        SimpleDocumentListener manualServiceEdited = new SimpleDocumentListener(() -> {
            if (!busy && manualService()) {
                invalidateServiceCheck();
                updateButtons();
            }
        });
        manualServiceUrl.getDocument().addDocumentListener(manualServiceEdited);
        manualServiceKey.getDocument().addDocumentListener(manualServiceEdited);
        saveApiKey.addActionListener(e -> showServiceMode());
        confluenceAccess.addActionListener(e -> {
            if (!busy) {
                confluenceModeChanged();
            }
        });
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
        onTyped(confluenceEntry, () -> {
            if (!text(confluenceEntry).equals(confluenceShownTitle)) {
                readConfluenceEntry();
            }
        });
        confluenceEntryRefresh.addActionListener(e -> refreshEntries());
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
        speechCheck.addActionListener(e -> {
            LocalVoiceOffer chosen = selectedSpeechOffer();
            speechCheckedId = speechCheck.isSelected() && chosen != null && !chosen.isInstalled() ? chosen.id() : null;
            updateButtons();
        });
        speechInstall.addActionListener(e -> installSelected());
        installAll.addActionListener(e -> installAll());
        speechProgress.cancelButton().addActionListener(e -> stopSpeechDownload());
        speechRetry.addActionListener(e -> loadSpeechModels(null));
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
        close();
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
        if (busy || installing() || index == step) {
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
        if (index == STEP_SERVICE && manualService()) {
            showServiceMode();
        } else if (index == STEP_SERVICE && serviceRead == null && !done[STEP_SERVICE]) {
            withEntries(this::preselectServiceEntry);
        } else if (index == STEP_SERVICE && !done[STEP_SERVICE] && serviceEntryComplete && !serviceChecking
                && !busy) {
            testService(); // der letzte Test ist nicht bestanden: neu prüfen statt den alten Fehler stehen zu lassen
        } else if (index == STEP_MODELS && !modelsLoaded && !modelsPreparing) {
            refreshModels();
        } else if (index == STEP_CONFLUENCE && manualConfluence()) {
            showConfluenceCredentials();
        } else if (index == STEP_CONFLUENCE && confluenceRead == null && !confluenceOk) {
            withEntries(this::preselectConfluenceEntry);
        } else if (index == STEP_FINISH) {
            runFinalCheck();
        } else if (index == STEP_SPEECH && !speechLoaded) {
            loadSpeechModels(null);
            loadNlpModels();
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
        backButton.setEnabled(step > 0 && !busy && !installing());
        serviceEntryRefresh.setEnabled(!busy);
        confluenceEntryRefresh.setEnabled(!busy);
        serviceAccess.setEnabled(!busy);
        confluenceAccess.setEnabled(!busy);
        nextButton.setVisible(!speech);
        finishButton.setVisible(speech);
        nextButton.setEnabled(!speech && done[step] && !busy);
        // Der Schritt „Spracheingabe“ blockiert nie: Überspringen geht immer, auch während eines Downloads.
        finishButton.setEnabled(speech && done[STEP_FINISH] && done[STEP_SPEECH] && !busy && !installing());
        cancelButton.setVisible(!speech);
        // „Überspringen“ in jedem Schritt: weiter ohne Prüfung; im letzten Schritt abschließen ohne Spracheingabe,
        // das geht erst nach bestandener Abschlussprüfung.
        skipButton.setEnabled(speech ? done[STEP_FINISH] : !busy);
        skipButton.setToolTipText(speech ? SKIP_SPEECH_HINT : SKIP_STEP_HINT);
        trail.setNavigable(!busy && !installing() && !handingOff);
        if (handingOff) { // die Übergabe läuft bis zum Ende, dann schließt der Assistent selbst
            backButton.setEnabled(false);
            nextButton.setEnabled(false);
            finishButton.setEnabled(false);
            skipButton.setEnabled(false);
        }
        LocalVoiceOffer chosen = selectedSpeechOffer();
        boolean installable = !missingNlpOffers().isEmpty() || (chosen != null && !chosen.isInstalled());
        // Selection and installation state are separate: an installed model shows a ticked, locked box.
        speechCheck.setVisible(chosen != null);
        speechCheck.setSelected(chosen != null && (chosen.isInstalled() || chosen.id().equals(speechCheckedId)));
        speechCheck.setEnabled(chosen != null && !chosen.isInstalled() && !busy && !installing());
        for (Component part : nlpLines.getComponents()) {
            if (part instanceof JCheckBox && part.getName() != null && part.getName().startsWith("nlp-check-")) {
                NlpModelOffer offer = nlpOffer(part.getName().substring("nlp-check-".length()));
                part.setEnabled(offer != null && !offer.isInstalled() && !busy && !installing());
            }
        }
        speechInstall.setVisible(installable);
        speechInstall.setEnabled(!busy && !installing()
                && (!selectedMissingNlp().isEmpty() || speechSelectedForInstall()));
        speechRetry.setVisible(speechLoadFailed);
        speechRetry.setEnabled(!busy && !installing());
        speechModel.setEnabled(!installing());
        installAll.setVisible(installable);
        installAll.setEnabled(!busy && !installing());
        speechProgress.setVisible(installing());
        String error = errors[step];
        errorPlate.setVisible(error != null && !error.isEmpty());
        errorText.setText(error == null ? "" : error);
        networkButton.setVisible((step == STEP_SERVICE && serviceNetworkProblem || step == STEP_CONFLUENCE)
                && error != null && !error.isEmpty());
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
        final int generation = ++entriesGeneration;
        store().list((list, error) -> {
            if (generation != entriesGeneration) {
                return;
            }
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
        final int generation = ++entriesGeneration;
        store().list((list, error) -> {
            if (generation != entriesGeneration) {
                return;
            }
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

    /**
     * "Reload KeePass entries" on the service and Confluence steps: always re-reads the list, refills both choices
     * without losing the typed title or form input, and re-reads the chosen entry (metadata and whether it still
     * exists). If reading fails, the old list no longer counts as the KeePass state; no title is called "new" and
     * the wizard creates nothing. Only the response to the newest read counts.
     */
    private void refreshEntries() {
        form = collect();
        final int generation = ++entriesGeneration;
        final int on = step;
        JLabel status = on == STEP_SERVICE ? serviceEntryStatus : on == STEP_CONFLUENCE ? confluenceEntryStatus : null;
        if (status != null) {
            status.setText("Lade die KeePass-Einträge neu …");
        }
        setBusy(true);
        store().list((list, error) -> {
            if (generation != entriesGeneration) {
                return;
            }
            setBusy(false);
            if (error != null) {
                entries = Collections.<PasswordEntry>emptyList();
                entriesLoaded = false;
                entriesProblem = error;
                done[STEP_KEEPASS] = false;
                keePassStatus.setText("Nicht verbunden.");
            } else {
                acceptEntries(list);
            }
            if (step != on) {
                return;
            }
            if (on == STEP_SERVICE) {
                readServiceEntry();
            } else if (on == STEP_CONFLUENCE) {
                readConfluenceEntry();
            }
        });
    }

    private void acceptEntries(List<PasswordEntry> list) {
        entries = list == null ? Collections.<PasswordEntry>emptyList() : list;
        entriesLoaded = true;
        entriesProblem = null;
        keePassUsed = true;
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
                + "; bitte KeePass prüfen und die Einträge neu laden (⟳) oder im Schritt „KeePass“ verbinden.";
    }

    private void fillEntryChoices() {
        List<String> titles = new ArrayList<String>();
        for (PasswordEntry entry : entries) {
            titles.add(entry.title());
        }
        setEntryChoices(serviceEntry, titles);
        setEntryChoices(confluenceEntry, titles);
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
        boolean wasBusy = busy;
        busy = true; // the selection listener must not start a second read and test of the same entry
        try {
            setText(serviceEntry, chosen);
        } finally {
            busy = wasBusy;
        }
        readServiceEntry();
    }

    private void readServiceEntry() {
        final String title = text(serviceEntry);
        boolean wasExisting = serviceRead != null;
        serviceShownTitle = title;
        stopServiceRecheck();
        serviceCheckGeneration++;
        serviceChecking = false;
        serviceRechecks = 0;
        serviceNetworkProblem = false;
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
        final String stored = storedTitle(title);
        if (stored == null) {
            serviceEntryStatus.setText("Neuer Eintrag „" + title + "“: bitte Basis-URL und API-Key angeben und "
                    + "neu in KeePass anlegen.");
            serviceMissing.setVisible(true);
            if (wasExisting) { // values of the previously chosen entry do not belong to the new one
                serviceUrl.setText("");
                serviceKey.setText("");
            }
            updateButtons();
            return;
        }
        serviceEntryStatus.setText("Lese den Eintrag …");
        setBusy(true);
        store().read(stored, (entry, error) -> {
            setBusy(false);
            if (!title.equals(text(serviceEntry))) { // the user kept typing meanwhile: the newer title wins
                readServiceEntry();
                return;
            }
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
                    + (hasUrl ? entry.url() : "fehlt") + "." + (serviceEntryComplete ? "" : INCOMPLETE_HINT));
            serviceMissing.setVisible(false);
            serviceUrl.setText(entry.url());
            serviceKey.setText("");
            updateButtons();
            if (serviceEntryComplete) {
                testService();
            }
        });
    }

    /**
     * Creates the AI service entry (create-only, never change): an existing title is rejected, also when it differs
     * only in case; the create path re-checks the current KeePass state itself.
     */
    private void saveServiceEntry() {
        final String title = text(serviceEntry);
        String url = serviceUrl.getText().trim();
        char[] password = serviceKey.getPassword();
        String problem = newEntryProblem(title);
        if (problem == null && (url.isEmpty() || password.length == 0)) {
            problem = "Für den neuen Eintrag werden Basis-URL und API-Key gebraucht.";
        }
        if (problem != null) {
            fail(STEP_SERVICE, problem);
            wipe(password);
            return;
        }
        final PasswordEntry entry = new PasswordEntry(title, "", url, Collections.<String, String>emptyMap(),
                password);
        wipe(password);
        serviceEntryStatus.setText("Lege den Eintrag in KeePass an …");
        setBusy(true);
        store().create(entry, (ignored, error) -> {
            entry.wipe();
            setBusy(false);
            if (error != null) {
                serviceEntryStatus.setText("Nicht angelegt.");
                fail(STEP_SERVICE, "Der Eintrag „" + title + "“ wurde nicht angelegt: " + error);
                return;
            }
            addEntry(new PasswordEntry(title, entry.userName(), entry.url(), entry.fields(), null));
            serviceKey.setText("");
            readServiceEntry();
        });
    }

    /** „Verbindung testen“ und jeder neue Anlauf von Hand: zählt die Wiederprüfungen von vorn. */
    private void testService() {
        stopServiceRecheck();
        serviceRechecks = 0;
        if (manualService() && !takeManualServiceKey()) {
            return;
        }
        runServiceCheck(false);
    }

    // --- Schritt 2, Zugang „Manuell“

    private boolean manualService() {
        return MODE_MANUAL.equals(serviceAccess.getSelectedItem());
    }

    /** Ein neuer Test ist nötig: laufende und geplante Prüfungen zählen nicht mehr. */
    private void invalidateServiceCheck() {
        stopServiceRecheck();
        serviceCheckGeneration++;
        serviceChecking = false;
        serviceRechecks = 0;
        serviceNetworkProblem = false;
        done[STEP_SERVICE] = false;
    }

    /**
     * Wechsel KeePass ↔ Manuell: nichts vom alten Zugang bleibt stehen (Test, Fehler, eingegebener Key); KeePass liest
     * danach seinen Eintrag, Manuell zeigt URL und API-Key.
     */
    private void serviceModeChanged() {
        invalidateServiceCheck();
        serviceEntryComplete = false;
        serviceRead = null;
        clearError(STEP_SERVICE);
        serviceStatus.setText("");
        serviceKey.setText("");
        manualServiceKey.setText("");
        setup.forgetManual(MANUAL_SERVICE_REF);
        showServiceMode();
        if (!manualService()) {
            withEntries(this::preselectServiceEntry);
        }
        updateButtons();
    }

    /** Blendet die Felder des gewählten Zugangs ein; Manuell zeigt, ob und wie der API-Key gespeichert würde. */
    private void showServiceMode() {
        boolean manual = manualService();
        for (JComponent part : serviceKeePassParts) {
            part.setVisible(!manual);
        }
        if (manual) {
            serviceMissing.setVisible(false);
        }
        for (JComponent part : serviceManualParts) {
            part.setVisible(manual);
        }
        boolean storable = setup.canStoreManualKey();
        saveApiKey.setEnabled(storable && !busy);
        if (!storable) {
            saveApiKey.setSelected(false);
        }
        saveApiKeyNote.setText(!storable ? setup.manualKeyStorage()
                : saveApiKey.isSelected() ? setup.manualKeyStorage() + " Gespeichert wird erst nach der bestandenen "
                + "Abschlussprüfung."
                : setup.manualKeyStored(MANUAL_SERVICE_REF) ? "Ein API-Key ist gespeichert; ohne Häkchen wird er "
                + "beim Abschluss entfernt."
                : "Ohne Häkchen gilt der API-Key nur für diese Sitzung; nach einem Neustart wird er erneut abgefragt.");
        revalidate();
        repaint();
    }

    /**
     * Übernimmt den eingegebenen API-Key in die Sitzung (das Feld wird geleert, nichts wird gespeichert); ohne Eingabe
     * genügt ein Key, der in dieser Sitzung schon eingegeben oder ausdrücklich gespeichert wurde.
     */
    private boolean takeManualServiceKey() {
        clearError(STEP_SERVICE);
        if (manualServiceUrl.getText().trim().isEmpty()) {
            fail(STEP_SERVICE, "Bitte die Basis-URL des KI-Dienstes eintragen.");
            return false;
        }
        char[] typed = manualServiceKey.getPassword();
        try {
            if (typed.length > 0) {
                setup.rememberManual(MANUAL_SERVICE_REF, "", typed);
                boolean was = busy;
                busy = true;
                try {
                    manualServiceKey.setText("");
                } finally {
                    busy = was;
                }
            } else if (!setup.knowsManual(MANUAL_SERVICE_REF) && !setup.manualKeyStored(MANUAL_SERVICE_REF)) {
                fail(STEP_SERVICE, "Bitte den API-Key eingeben.");
                return false;
            }
        } finally {
            wipe(typed);
        }
        serviceEntryComplete = true;
        return true;
    }

    /**
     * Prüft KeePass-Eintrag und KI-Dienst über den Verbindungstest. Von Hand blockiert der Test den Assistenten wie
     * bisher; eine Wiederprüfung läuft still im Hintergrund, der Assistent bleibt bedienbar. Ein Ergebnis zählt nur,
     * wenn es zum neuesten Test gehört.
     */
    private void runServiceCheck(final boolean automatic) {
        if (!serviceEntryComplete) {
            fail(STEP_SERVICE, manualService() ? "Bitte Basis-URL und API-Key eingeben."
                    : "Im KeePass-Eintrag fehlen noch API-Key oder Basis-URL.");
            return;
        }
        form = collect();
        final int generation = ++serviceCheckGeneration;
        serviceChecking = true;
        clearError(STEP_SERVICE);
        serviceNetworkProblem = false;
        done[STEP_SERVICE] = false;
        serviceStatus.setText(automatic ? "Prüfe erneut (" + serviceRechecks + ". Wiederprüfung) …"
                : "Teste die Verbindung zum KI-Dienst …");
        if (automatic) {
            updateButtons();
        } else {
            setBusy(true);
        }
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
                if (!automatic) {
                    setBusy(false);
                }
                if (closed || generation != serviceCheckGeneration) {
                    return; // ein neuerer Test, ein anderer Eintrag oder das Schließen kam dazwischen
                }
                serviceChecking = false;
                serviceFinished(success && failures.isEmpty(), failures, automatic);
            }
        });
    }

    private void serviceFinished(boolean success, List<ConnectionCheckStep> failures, boolean automatic) {
        if (success) {
            stopServiceRecheck();
            serviceRechecks = 0;
            serviceNetworkProblem = false;
            clearError(STEP_SERVICE);
            done[STEP_SERVICE] = true;
            serviceStatus.setText(manualService() ? "Der KI-Dienst ist erreichbar; der API-Key gilt für diese Sitzung."
                    : "Der KI-Dienst ist erreichbar; der API-Key wurde gelesen.");
            updateButtons();
            prepareModelsAndWakeMerlin();
            return;
        }
        boolean temporary = !failures.isEmpty();
        StringBuilder message = new StringBuilder();
        for (ConnectionCheckStep failure : failures) {
            message.append(failure.title()).append(": ").append(failure.detail()).append('\n');
            temporary &= failure.isTemporary();
            serviceNetworkProblem |= !PROBE_KEY.equals(failure.title());
        }
        if (message.length() == 0) {
            message.append("Der Verbindungstest ist fehlgeschlagen.");
        }
        RetryPolicy policy = recheckPolicy();
        if (temporary && serviceRechecks < policy.retries()) {
            long delay = policy.delayBeforeRetry(serviceRechecks + 1);
            serviceStatus.setText("Vorübergehend nicht erreichbar; prüfe in " + seconds(delay) + " erneut …");
            message.append("\nVorübergehende Störung: Der Assistent prüft in ").append(seconds(delay))
                    .append(" von selbst erneut.");
            scheduleServiceRecheck(delay);
        } else {
            serviceStatus.setText(temporary ? "Weiterhin nicht erreichbar; „Verbindung testen“ prüft erneut."
                    : "Verbindung fehlgeschlagen.");
        }
        if (manualService() && !temporary) {
            // ein abgelehnter Key bleibt nicht in der Sitzung; der nächste Test braucht eine neue Eingabe
            setup.forgetManual(MANUAL_SERVICE_REF);
            serviceEntryComplete = false;
        }
        if (automatic) { // Merlin meldet denselben Ausfall nicht bei jeder Wiederprüfung neu
            errors[STEP_SERVICE] = message.toString().trim();
            updateButtons();
        } else {
            fail(STEP_SERVICE, message.toString().trim());
        }
    }

    /** Die Regel der Wiederprüfungen ({@code security.keepass.setupRecheck*}); Unlesbares gilt als Standard. */
    private RetryPolicy recheckPolicy() {
        int count = number(form.keePassSetupRecheckCount(), KeePassConfig.DEFAULT_SETUP_RECHECK_COUNT, 0,
                RetryPolicy.MAX_RETRIES);
        int delay = number(form.keePassSetupRecheckDelayMillis(), KeePassConfig.DEFAULT_SETUP_RECHECK_DELAY_MILLIS,
                0, RetryPolicy.MAX_INITIAL_DELAY_MILLIS);
        return RetryPolicy.of(count, delay);
    }

    private static int number(String text, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(text == null ? "" : text.trim());
            return value < min || value > max ? fallback : value;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String seconds(long millis) {
        long rounded = Math.max(1L, (millis + 500L) / 1000L);
        return rounded + " s";
    }

    private void scheduleServiceRecheck(long delayMillis) {
        stopServiceRecheck();
        final int generation = serviceCheckGeneration;
        Timer timer = new Timer((int) Math.min(Integer.MAX_VALUE, delayMillis), e -> {
            serviceRecheck = null;
            if (closed || generation != serviceCheckGeneration || done[STEP_SERVICE] || busy) {
                return;
            }
            serviceRechecks++;
            runServiceCheck(true);
        });
        timer.setRepeats(false);
        serviceRecheck = timer;
        timer.start();
    }

    private void stopServiceRecheck() {
        if (serviceRecheck != null) {
            serviceRecheck.stop();
            serviceRecheck = null;
        }
    }

    /** Für Tests: eine Wiederprüfung ist geplant. */
    boolean isServiceRecheckPending() {
        return serviceRecheck != null;
    }

    /** Für Tests: die geplante Wiederprüfung sofort auslösen. */
    void fireServiceRecheck() {
        Timer timer = serviceRecheck;
        if (timer != null) {
            timer.stop();
            for (java.awt.event.ActionListener listener : timer.getActionListeners()) {
                listener.actionPerformed(new java.awt.event.ActionEvent(timer, 0, null));
            }
        }
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
        List<String> speech = models.speech();
        List<String> vision = models.vision();
        List<String> rerank = models.rerank();
        boolean wasBusy = busy;
        busy = true;
        try {
            // Keep a choice that is still offered; else pick the preferred model, silently falling back.
            setChoices(chatModel, chats, ModelPreselection.choose(chats, text(chatModel),
                    settings.preferredModel(ModelCategory.CHAT), settings.preferredModelMatch(ModelCategory.CHAT)));
            setChoices(embeddingModel, embeddings, ModelPreselection.choose(embeddings, text(embeddingModel),
                    settings.preferredModel(ModelCategory.EMBEDDING),
                    settings.preferredModelMatch(ModelCategory.EMBEDDING)));
            setChoices(speechOutputModel, speech, ModelPreselection.choose(speech, text(speechOutputModel),
                    settings.preferredModel(ModelCategory.TTS), settings.preferredModelMatch(ModelCategory.TTS)));
            setChoices(visionModel, vision, ModelPreselection.choose(vision, text(visionModel),
                    settings.preferredModel(ModelCategory.VISION),
                    settings.preferredModelMatch(ModelCategory.VISION)));
            setChoices(rerankModel, rerank, ModelPreselection.choose(rerank, text(rerankModel),
                    settings.preferredModel(ModelCategory.RERANK),
                    settings.preferredModelMatch(ModelCategory.RERANK)));
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
            modelsStatus.setText(chats.size() + " Chat · " + embeddings.size() + " Embedding · " + speech.size()
                    + " Sprache · " + vision.size() + " Bild · " + rerank.size() + " Re-Ranker");
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
        embeddingDimension.setToolTipText("Dimension wird automatisch ermittelt …");
        setBusy(true);
        setup.embeddingDimension(form, (dimension, error) -> {
            setBusy(false);
            if (!model.equals(text(embeddingModel))) {
                return;
            }
            if (dimension != null && dimension > 0) {
                embeddingDimension.setText(Integer.toString(dimension));
                embeddingDimension.setToolTipText("Automatisch ermittelt: " + dimension + " Werte je Vektor.");
            } else {
                measuredFor = "";
                embeddingDimension.setToolTipText("Nicht automatisch ermittelbar; bitte die Dimension eintragen.");
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
     * Reads the chosen entry. An existing entry is only read: the step shows what is present and tests with the
     * KeePass values; anything missing the user adds directly in KeePass and then reloads. The fields only appear
     * for a new title, under which the test creates the entry.
     */
    private void readConfluenceEntry() {
        final String title = text(confluenceEntry);
        boolean wasExisting = confluenceRead != null;
        confluenceShownTitle = title;
        confluenceRead = null;
        confluenceOk = false;
        confluenceReading = false;
        clearError(STEP_CONFLUENCE);
        confluenceStatus.setText("");
        final String stored = title.isEmpty() || !entriesLoaded ? null : storedTitle(title);
        if (stored == null) {
            busy = true;
            try {
                confluenceEntryStatus.setText(title.isEmpty()
                        ? "Bitte einen KeePass-Eintrag wählen oder einen Titel eintragen."
                        : !entriesLoaded ? entriesMissingHint()
                        : "Neuer Eintrag: URL und Passwort bzw. Token angeben; er wird beim Testen neu in KeePass "
                        + "angelegt.");
                confluencePasswordHint.setText(" ");
                if (wasExisting) { // values of the previously chosen entry do not belong to the new one
                    confluenceUrl.setText("");
                    confluenceUser.setText("");
                    confluencePassword.setText("");
                    certificateAlias = "";
                    certificateLabel.setText("kein Zertifikat");
                }
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
        store().read(stored, (entry, error) -> {
            confluenceReading = false;
            setBusy(false);
            if (!title.equals(text(confluenceEntry))) { // the user kept typing meanwhile: the newer title wins
                if (entry != null) {
                    entry.wipe();
                }
                readConfluenceEntry();
                return;
            }
            if (error != null || entry == null) {
                confluenceEntryStatus.setText("Nicht lesbar.");
                showConfluenceCredentials();
                fail(STEP_CONFLUENCE, "Der Eintrag „" + title + "“ ist nicht lesbar: " + error);
                return;
            }
            confluenceRead = entry;
            busy = true;
            try {
                confluenceUrl.setText("");
                confluenceUser.setText("");
                confluencePassword.setText("");
                confluencePasswordHint.setText(" ");
                String alias = entry.field(PasswordEntry.CERT_ALIAS);
                certificateAlias = alias == null ? "" : alias.trim();
                certificateLabel.setText(certificateAlias.isEmpty() ? "kein Zertifikat" : certificateAlias);
                withoutCertificate.setSelected(false);
                confluenceEntryStatus.setText(complete(entry) ? "Vorhandener Eintrag; die Zugangsdaten stehen in KeePass."
                        : "Im vorhandenen Eintrag fehlt noch etwas." + INCOMPLETE_HINT);
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
     * Status of a read entry (what is present, no secrets), or for a new title the fields of the new entry. The
     * wizard never changes an existing entry.
     */
    private void showConfluenceCredentials() {
        boolean manualAccess = manualConfluence();
        for (JComponent part : confluenceKeePassParts) {
            part.setVisible(!manualAccess);
        }
        rememberUser.setVisible(manualAccess);
        if (manualAccess) {
            for (int row = 0; row < manualRows.size(); row++) {
                manualRowVisible(row, true);
            }
            confluencePasswordHint.setText("Gilt nur für diese Sitzung und wird nie gespeichert.");
            confluenceSummary.setVisible(false);
            confluenceManual.setVisible(true);
            updateButtons();
            return;
        }
        String title = text(confluenceEntry);
        boolean existing = confluenceRead != null;
        boolean summary = existing && !confluenceReading;
        boolean manual = entriesLoaded && !title.isEmpty() && !confluenceReading && !existing
                && storedTitle(title) == null;
        if (summary) {
            boolean hasUrl = !confluenceRead.url().trim().isEmpty();
            state(confluenceHasUrl, hasUrl, hasUrl ? "URL vorhanden: " + confluenceRead.url()
                    : "URL fehlt: bitte in KeePass ergänzen");
            String user = confluenceRead.userName().trim();
            state(confluenceHasUser, true, user.isEmpty()
                    ? "Kein Benutzername: Anmeldung mit persönlichem Zugriffstoken" : "Benutzername vorhanden");
            boolean hasSecret = confluenceRead.hasPassword();
            state(confluenceHasSecret, hasSecret, !hasSecret
                    ? "Passwort bzw. Token fehlt: bitte in KeePass ergänzen"
                    : user.isEmpty() ? "Token vorhanden" : "Passwort bzw. Token vorhanden");
        }
        for (int row = 0; row < manualRows.size(); row++) {
            manualRowVisible(row, manual);
        }
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

    /**
     * An existing entry is only read and tested with its KeePass values. A new title is first created in KeePass
     * (create-only, never change) and tested only after the create succeeded.
     */
    private void testConfluence() {
        if (manualConfluence()) {
            testManualConfluence();
            return;
        }
        final String title = text(confluenceEntry);
        if (confluenceRead == null && title.isEmpty()) {
            fail(STEP_CONFLUENCE, "Für Confluence wird ein Titel für den KeePass-Eintrag gebraucht.");
            return;
        }
        if (confluenceRead != null && !complete(confluenceRead)) {
            fail(STEP_CONFLUENCE, "Im KeePass-Eintrag „" + confluenceRead.title() + "“ fehlen URL oder Passwort bzw. "
                    + "Token. Der Assistent ändert keine vorhandenen Einträge;" + INCOMPLETE_HINT);
            return;
        }
        if (certificateAlias.isEmpty() && !withoutCertificate.isSelected()) {
            fail(STEP_CONFLUENCE, "Bitte ein Client-Zertifikat wählen oder ausdrücklich ohne Zertifikat fortfahren.");
            return;
        }
        if (confluenceRead != null) {
            Map<String, String> fields = new LinkedHashMap<String, String>(confluenceRead.fields());
            if (!certificateAlias.isEmpty()) {
                fields.put(PasswordEntry.CERT_ALIAS, certificateAlias);
            } else {
                fields.remove(PasswordEntry.CERT_ALIAS);
            }
            char[] password = confluenceRead.copyPassword();
            PasswordEntry entry = new PasswordEntry(confluenceRead.title(), confluenceRead.userName(),
                    confluenceRead.url(), fields, password);
            wipe(password);
            clearError(STEP_CONFLUENCE);
            confluenceOk = false;
            setBusy(true);
            runConfluenceTest(entry);
            return;
        }
        final String url = confluenceUrl.getText().trim();
        final String user = confluenceUser.getText().trim();
        char[] password = confluencePassword.getPassword();
        String problem = newEntryProblem(title);
        if (problem == null && (url.isEmpty() || password.length == 0)) {
            problem = "Für den neuen Confluence-Eintrag werden URL und Passwort bzw. Token gebraucht.";
        }
        if (problem != null) {
            wipe(password);
            fail(STEP_CONFLUENCE, problem);
            return;
        }
        Map<String, String> fields = new LinkedHashMap<String, String>();
        fields.put(PasswordEntry.CATEGORY, "Confluence");
        if (!certificateAlias.isEmpty()) {
            fields.put(PasswordEntry.CERT_ALIAS, certificateAlias);
        }
        final PasswordEntry entry = new PasswordEntry(title, user, url, fields, password);
        wipe(password);
        clearError(STEP_CONFLUENCE);
        confluenceOk = false;
        setBusy(true);
        confluenceStatus.setText("Lege den Eintrag neu in KeePass an …");
        store().create(entry, (ignored, error) -> {
            if (error != null) {
                entry.wipe();
                setBusy(false);
                confluenceStatus.setText("Nicht angelegt.");
                fail(STEP_CONFLUENCE, "Der Eintrag „" + title + "“ wurde nicht angelegt: " + error);
                return;
            }
            addEntry(new PasswordEntry(title, user, url, entry.fields(), null));
            confluenceRead = new PasswordEntry(title, user, url, entry.fields(), entry.copyPassword());
            confluenceShownTitle = title;
            busy = true;
            confluencePassword.setText("");
            busy = false;
            showConfluenceCredentials();
            runConfluenceTest(entry);
        });
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

    // --- Schritt 4, Zugang „Manuell“

    private boolean manualConfluence() {
        return MODE_MANUAL.equals(confluenceAccess.getSelectedItem());
    }

    /**
     * Wechsel KeePass ↔ Manuell: Test, Ergebnis, Fehler und Passwort bzw. Token des alten Zugangs gelten nicht mehr;
     * URL und Benutzer (nicht geheim) bleiben stehen. KeePass liest danach seinen Eintrag.
     */
    private void confluenceModeChanged() {
        confluenceGeneration++;
        confluenceOk = false;
        confluence = null;
        confluenceRead = null;
        clearError(STEP_CONFLUENCE);
        confluenceStatus.setText("");
        boolean was = busy;
        busy = true;
        try {
            confluencePassword.setText("");
        } finally {
            busy = was;
        }
        String url = confluenceUrl.getText().trim();
        if (!url.isEmpty()) {
            setup.forgetManual(ConfluenceSetup.manualRef(url));
        }
        if (manualConfluence()) {
            showConfluenceCredentials();
        } else {
            withEntries(this::preselectConfluenceEntry);
        }
        updateButtons();
    }

    /**
     * Test mit den eingegebenen Werten, ohne KeePass: Spaces abrufen wie beim KeePass-Weg. Passwort bzw. Token kommt
     * in die Sitzung (für Abschlussprüfung und Anwendung), nie in eine Datei; scheitert der Test, wird es vergessen.
     */
    private void testManualConfluence() {
        final String url = confluenceUrl.getText().trim();
        final String user = confluenceUser.getText().trim();
        char[] password = confluencePassword.getPassword();
        if (url.isEmpty() || password.length == 0) {
            wipe(password);
            fail(STEP_CONFLUENCE, "Für Confluence werden die URL und Passwort bzw. Token gebraucht; der "
                    + "Benutzername ist optional (ohne: Anmeldung mit Token).");
            return;
        }
        if (certificateAlias.isEmpty() && !withoutCertificate.isSelected()) {
            wipe(password);
            fail(STEP_CONFLUENCE, "Bitte ein Client-Zertifikat wählen oder ausdrücklich ohne Zertifikat fortfahren.");
            return;
        }
        final String ref = ConfluenceSetup.manualRef(url);
        final String alias = certificateAlias;
        final int generation = ++confluenceGeneration;
        clearError(STEP_CONFLUENCE);
        confluenceOk = false;
        confluence = null;
        form = collect();
        confluenceStatus.setText("Teste Confluence (Spaces abrufen) …");
        setBusy(true);
        try {
            setup.rememberManual(ref, user, password);
            setup.testConfluence(form, url, user, password, alias, (message, error) -> {
                setBusy(false);
                if (closed || generation != confluenceGeneration) {
                    return; // Zugang gewechselt oder neuer Test
                }
                if (error != null) {
                    setup.forgetManual(ref);
                    confluenceStatus.setText("Verbindung fehlgeschlagen.");
                    fail(STEP_CONFLUENCE, error);
                    return;
                }
                confluenceOk = true;
                confluence = ConfluenceSetup.manual(url, user, alias, rememberUser.isSelected());
                boolean was = busy;
                busy = true;
                try {
                    confluencePassword.setText("");
                } finally {
                    busy = was;
                }
                confluenceStatus.setText(message == null ? "Verbindung erfolgreich." : message);
                updateButtons();
            });
        } finally {
            wipe(password);
        }
    }

    // --- Schritt 5: Abschluss

    private void runFinalCheck() {
        done[STEP_FINISH] = false;
        clearError(STEP_FINISH);
        checkRows.removeAll();
        // Confluence ist optional: übersprungen ({@code null}) prüft der Abschluss es nicht und legt keine Quelle an.
        final boolean withoutConfluence = confluence == null;
        ConfluenceSetup chosen = confluence;
        if (chosen != null && chosen.isManual()) { // „Benutzername merken“ zählt, wie es beim Abschluss steht
            chosen = ConfluenceSetup.manual(chosen.baseUrl(), chosen.userName(), chosen.certificateAlias(),
                    rememberUser.isSelected());
        }
        form = collect();
        finishStatus.setText("Speichere und prüfe …");
        setBusy(true);
        setup.finish(form, chosen, checks -> {
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
            } else if (manualService()) {
                keepManualKey();
            } else {
                updateButtons();
            }
        });
    }

    /**
     * „API-Key speichern“ erst nach bestandener Abschlussprüfung: mit Häkchen geschützt speichern, ohne ein früher
     * gespeichertes gezielt entfernen. Ein Fehler steht beim Abschluss, die Einrichtung bleibt gültig (der Key gilt
     * dann nur für diese Sitzung).
     */
    private void keepManualKey() {
        final boolean keep = saveApiKey.isSelected() && setup.canStoreManualKey();
        setBusy(true);
        setup.storeManualKey(MANUAL_SERVICE_REF, keep, error -> {
            setBusy(false);
            if (error != null) {
                errors[STEP_FINISH] = (keep ? "Der API-Key wurde nicht gespeichert: "
                        : "Der gespeicherte API-Key ließ sich nicht entfernen: ") + error;
            } else if (keep) {
                finishStatus.setText(finishStatus.getText() + " Der API-Key ist geschützt gespeichert.");
            }
            showServiceMode();
            updateButtons();
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
        close();
        complete();
    }

    // --- Schritt 6: Spracheingabe (optional)

    /** Fertig ohne lokale Spracheingabe; die Grundeinrichtung ist mit der Abschlussprüfung schon gespeichert. */
    private void skipSpeech() {
        if (!done[STEP_FINISH]) {
            return;
        }
        close();
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
        if (prefer != null) {
            announceResourcesIfComplete();
        }
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
        return offer.displayName() + (offer.isInstalled() ? " (installiert)"
                : offer.isInOfflinePackage() ? " (im Offline-Paket)" : "");
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
        } else if (chosen.isInOfflinePackage()) {
            speechStatus.setText("Im Offline-Paket; anhaken und „" + INSTALL_SELECTED + "“ entpackt es ohne "
                    + "Download.");
        } else {
            speechStatus.setText("Noch nicht installiert; anhaken und „" + INSTALL_SELECTED + "“ lädt es herunter. "
                    + "Bitte kurz warten oder überspringen.");
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
        final InstallCancellation download = new InstallCancellation();
        speechDownload = download;
        speechDownloading = true;
        speechProgress.reset("Vorbereiten …");
        speechStatus.setText("Installiere " + chosen.displayName() + " …");
        updateButtons();
        settings.installLocalVoice(collect(), chosen.id(), download, new LocalVoiceInstallProgress() {
            /** Ignore callbacks of a stopped or replaced download and of a closed wizard. */
            private boolean stale() {
                return closed || speechDownload != download;
            }

            @Override
            public void progress(String line) {
                if (stale() || download.isCancelled()) {
                    return;
                }
                speechProgress.preparing(line);
            }

            @Override
            public void file(String name, int number, int count, long done, long total) {
                if (stale() || download.isCancelled()) {
                    return;
                }
                speechProgress.file(name, number, count, done, total);
            }

            @Override
            public void finished(boolean installed, String message) {
                if (stale()) {
                    return;
                }
                speechDownload = null;
                speechDownloading = false;
                if (!installed) {
                    speechStatus.setText("Installation fehlgeschlagen.");
                    fail(STEP_SPEECH, "Das Modell ließ sich nicht installieren: "
                            + (message == null || message.isEmpty() ? "unbekannter Grund." : message)
                            + " Es bleibt angehakt: „" + INSTALL_SELECTED + "“ versucht es erneut; Überspringen geht "
                            + "immer.");
                    return;
                }
                speechStatus.setText(chosen.displayName() + " ist installiert.");
                loadSpeechModels(before);
            }

            @Override
            public void cancelled(String message) {
                if (stale()) {
                    return;
                }
                speechDownload = null;
                speechDownloading = false;
                speechStatus.setText(SPEECH_CANCELLED);
                updateButtons();
            }
        });
    }

    /**
     * Stop the running download for real: the transfer ends at its next data block and the partial files stay for a
     * later resume. The wizard stays usable; the result arrives as {@code cancelled}, or as {@code finished} when the
     * installation was already complete.
     */
    void stopSpeechDownload() {
        if (nlpInstalling) {
            nlpStopRequested = true;
            speechAfterNlp = false;
            speechProgress.cancelling();
            updateButtons();
            return;
        }
        InstallCancellation download = speechDownload;
        if (download == null || !download.cancel()) {
            return;
        }
        speechProgress.cancelling();
        updateButtons();
    }

    /** Stop a running download before the wizard goes away and ignore everything it reports afterwards. */
    private void close() {
        InstallCancellation download = speechDownload;
        if (download != null) {
            download.cancel();
        }
        closed = true;
        stopServiceRecheck();
        serviceCheckGeneration++;
        speechDownload = null;
        speechDownloading = false;
        nlpStopRequested = true;
        speechAfterNlp = false;
    }

    boolean isSpeechDownloading() {
        return speechDownloading;
    }

    /** A download of the speech model or an installation of OpenNLP models is running. */
    private boolean installing() {
        return speechDownloading || nlpInstalling;
    }

    // --- Schritt 6: Text- und Sprachanalyse (OpenNLP)

    /** Die OpenNLP-Modelle mit Stand wie im Reiter „Lokale Modelle“; Ergebnis auf dem EDT. */
    private void loadNlpModels() {
        settings.nlpModels(offers -> {
            if (closed) {
                return;
            }
            nlpOffers = offers == null ? Collections.<NlpModelOffer>emptyList()
                    : new ArrayList<NlpModelOffer>(offers);
            showNlpOffers();
        });
    }

    private void showNlpOffers() {
        nlpLines.removeAll();
        int row = 0;
        for (NlpModelOffer offer : nlpOffers) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.anchor = GridBagConstraints.WEST;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 2, 10);
            JLabel name = new JLabel(shortNlpName(offer.name()));
            name.putClientProperty("html.disable", Boolean.TRUE);
            name.setForeground(palette.getInk());
            name.setFont(ResearchUiTypography.regular(12.5f));
            name.setToolTipText(offer.name());
            nlpLines.add(name, c);
            c = new GridBagConstraints();
            c.gridx = 1;
            c.gridy = row;
            c.anchor = GridBagConstraints.EAST;
            c.insets = new Insets(0, 0, 2, 0);
            String problem = nlpProblems.get(offer.id());
            JLabel state = new JLabel(offer.isInstalled() ? NLP_INSTALLED
                    : problem != null ? NLP_FAILED : offer.isInArchive() ? NLP_IN_ARCHIVE : NLP_DOWNLOAD);
            state.setFont(ResearchUiTypography.regular(12f));
            state.setForeground(offer.isInstalled() ? ResearchUiPalette.WAVE_GREEN
                    : problem != null ? ResearchUiPalette.DANGER_RED : ResearchUiPalette.LIGHT_CONTROL_TEXT);
            state.setName("nlp-state-" + offer.id());
            nlpLines.add(state, c);
            // The box column sits flush right, exactly below the box of the speech input.
            c = new GridBagConstraints();
            c.gridx = 2;
            c.gridy = row;
            c.anchor = GridBagConstraints.EAST;
            c.insets = new Insets(0, 10, 2, 0);
            final JCheckBox check = comicCheck("nlp-check-" + offer.id());
            check.setSelected(offer.isInstalled() || nlpChecked.contains(offer.id()));
            check.setToolTipText(offer.isInstalled() ? "Bereits installiert" : "Zum Installieren vormerken");
            check.addActionListener(e -> {
                if (check.isSelected()) {
                    nlpChecked.add(offer.id());
                } else {
                    nlpChecked.remove(offer.id());
                }
                updateButtons();
            });
            nlpLines.add(check, c);
            row++;
            if (problem == null || offer.isInstalled()) {
                continue;
            }
            // The reason stays in its own line until the next attempt; the error plate belongs to the speech model.
            c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 3;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(0, 0, 4, 0);
            JTextArea reason = new WrappedErrorText(() -> nlpLines.getWidth() > 0 ? nlpLines.getWidth() : getWidth() / 2);
            reason.setText(problem.replaceFirst("^Installation fehlgeschlagen: ", ""));
            reason.setEditable(false);
            reason.setFocusable(false);
            reason.setLineWrap(true);
            reason.setWrapStyleWord(true);
            reason.setOpaque(false);
            reason.setForeground(ResearchUiPalette.DANGER_RED);
            reason.setFont(ResearchUiTypography.regular(11.5f));
            reason.setName("nlp-reason-" + offer.id());
            nlpLines.add(reason, c);
            row++;
        }
        String bundle = settings.resourceBundleNote();
        if (bundle != null && !bundle.isEmpty()) {
            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = row;
            c.gridwidth = 3;
            c.anchor = GridBagConstraints.WEST;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.insets = new Insets(2, 0, 0, 0);
            JLabel note = new JLabel(bundle);
            note.putClientProperty("html.disable", Boolean.TRUE);
            note.setName("nlp-note");
            note.setToolTipText(bundle);
            note.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
            note.setFont(ResearchUiTypography.regular(11.5f));
            note.setMinimumSize(new Dimension(0, note.getPreferredSize().height));
            note.setPreferredSize(new Dimension(0, note.getPreferredSize().height));
            nlpLines.add(note, c);
        }
        nlpLines.revalidate();
        nlpLines.repaint();
        updateButtons();
    }

    /** The archive note below the OpenNLP lines, or {@code null} without one. */
    String nlpNote() {
        return textOf("nlp-note");
    }

    /** The failure reason shown below an OpenNLP line, or {@code null} when it has none. */
    String nlpReason(String id) {
        return textOf("nlp-reason-" + id);
    }

    /** The check box of an OpenNLP line, or {@code null}. */
    JCheckBox nlpCheckBox(String id) {
        for (Component part : nlpLines.getComponents()) {
            if (("nlp-check-" + id).equals(part.getName())) {
                return (JCheckBox) part;
            }
        }
        return null;
    }

    private String textOf(String name) {
        for (Component part : nlpLines.getComponents()) {
            if (name.equals(part.getName())) {
                return part instanceof JLabel ? ((JLabel) part).getText() : ((JTextArea) part).getText();
            }
        }
        return null;
    }

    /** A comic check box (the {@link ResearchCheckIcon} of „Ohne Zertifikat fortfahren“) without text. */
    private JCheckBox comicCheck(String name) {
        JCheckBox box = new JCheckBox();
        box.setName(name);
        box.setOpaque(false);
        box.setIcon(new ResearchCheckIcon());
        box.setSelectedIcon(new ResearchCheckIcon());
        box.setDisabledIcon(new ResearchCheckIcon());
        box.setDisabledSelectedIcon(new ResearchCheckIcon());
        box.setRolloverEnabled(true);
        box.setFocusPainted(false);
        box.setBorder(BorderFactory.createEmptyBorder());
        box.setMargin(new Insets(0, 0, 0, 0));
        return box;
    }

    private NlpModelOffer nlpOffer(String id) {
        for (NlpModelOffer offer : nlpOffers) {
            if (offer.id().equals(id)) {
                return offer;
            }
        }
        return null;
    }

    /** Die angehakten, noch fehlenden OpenNLP-Modelle in der Reihenfolge der Liste. */
    private List<NlpModelOffer> selectedMissingNlp() {
        List<NlpModelOffer> selected = new ArrayList<NlpModelOffer>();
        for (NlpModelOffer offer : missingNlpOffers()) {
            if (nlpChecked.contains(offer.id())) {
                selected.add(offer);
            }
        }
        return selected;
    }

    /** Das Häkchen der Spracheingabe gilt dem gerade gewählten, noch fehlenden Modell. */
    private boolean speechSelectedForInstall() {
        LocalVoiceOffer chosen = selectedSpeechOffer();
        return chosen != null && !chosen.isInstalled() && chosen.id().equals(speechCheckedId);
    }

    /**
     * „Ausgewählte installieren“: genau die angehakten, noch fehlenden OpenNLP-Modelle nacheinander, danach das
     * angehakte Modell der Spracheingabe; über denselben Weg wie „Alles installieren“.
     */
    private void installSelected() {
        if (busy || installing()) {
            return;
        }
        clearError(STEP_SPEECH);
        speechAfterNlp = speechSelectedForInstall();
        List<NlpModelOffer> queue = selectedMissingNlp();
        if (queue.isEmpty()) {
            continueWithSpeech();
            return;
        }
        for (NlpModelOffer offer : queue) {
            nlpProblems.remove(offer.id());
        }
        nlpInstalling = true;
        nlpStopRequested = false;
        speechProgress.reset("Vorbereiten …");
        showNlpOffers();
        installNlp(queue, 0);
    }

    /** Der Name ohne den Dateinamen in Klammern, damit die Zeile in die Breite des Assistenten passt. */
    private static String shortNlpName(String name) {
        int bracket = name.lastIndexOf(" (");
        return bracket > 0 && name.endsWith(")") ? name.substring(0, bracket) : name;
    }

    private List<NlpModelOffer> missingNlpOffers() {
        List<NlpModelOffer> missing = new ArrayList<NlpModelOffer>();
        for (NlpModelOffer offer : nlpOffers) {
            if (!offer.isInstalled()) {
                missing.add(offer);
            }
        }
        return missing;
    }

    /**
     * „Alles installieren“: die fehlenden OpenNLP-Modelle nacheinander (aus dem Offline-Paket, sonst Download),
     * danach das gewählte Spracheingabe-Modell. Ein Fehler hält nichts auf; das X hält nach dem laufenden Modell an.
     */
    private void installAll() {
        if (busy || installing()) {
            return;
        }
        clearError(STEP_SPEECH);
        LocalVoiceOffer chosen = selectedSpeechOffer();
        speechAfterNlp = chosen != null && !chosen.isInstalled();
        List<NlpModelOffer> missing = missingNlpOffers();
        if (missing.isEmpty()) {
            continueWithSpeech();
            return;
        }
        nlpProblems.clear();
        nlpInstalling = true;
        nlpStopRequested = false;
        speechProgress.reset("Vorbereiten …");
        installNlp(missing, 0);
    }

    private void installNlp(final List<NlpModelOffer> queue, final int index) {
        if (closed) {
            return;
        }
        if (index >= queue.size() || nlpStopRequested) {
            boolean stopped = nlpStopRequested && index < queue.size();
            nlpInstalling = false;
            nlpStopRequested = false;
            loadNlpModels();
            if (stopped) {
                speechStatus.setText(NLP_STOPPED);
                updateButtons();
                return;
            }
            reportNlpProblems();
            continueWithSpeech();
            return;
        }
        final NlpModelOffer offer = queue.get(index);
        String text = "Modell " + (index + 1) + " von " + queue.size() + ": " + shortNlpName(offer.name())
                + (offer.isInArchive() ? " aus dem Offline-Paket …" : " wird geladen …");
        speechProgress.preparing(text);
        speechStatus.setText(text);
        updateButtons();
        settings.installNlpModel(collect(), offer.id(), (ok, message) -> {
            if (closed) {
                return;
            }
            if (!Boolean.TRUE.equals(ok)) {
                nlpProblems.put(offer.id(), message == null || message.isEmpty() ? "unbekannter Grund" : message);
                nlpChecked.add(offer.id()); // stays ticked: „Ausgewählte installieren“ tries it again
            }
            installNlp(queue, index + 1);
        });
    }

    private void reportNlpProblems() {
        speechStatus.setText(nlpProblems.isEmpty() ? "Text- und Sprachanalyse ist installiert."
                : "Nicht alle OpenNLP-Modelle ließen sich installieren; der Grund steht unter dem Modell.");
    }

    /**
     * After an installation in step 6: once nothing is missing any more, Merlin says so, replacing an earlier
     * failure line of this step.
     */
    private void announceResourcesIfComplete() {
        LocalVoiceOffer chosen = selectedSpeechOffer();
        if (step != STEP_SPEECH || installing() || !nlpProblems.isEmpty() || !missingNlpOffers().isEmpty()
                || (chosen != null && !chosen.isInstalled())) {
            return;
        }
        narrator.say(WizardGuide.RESOURCES_OK);
    }

    /** Nach den OpenNLP-Modellen das gewählte Spracheingabe-Modell, sofern „Alles installieren“ es mitnimmt. */
    private void continueWithSpeech() {
        boolean speech = speechAfterNlp;
        speechAfterNlp = false;
        if (speech) {
            installSpeech();
        } else {
            updateButtons();
            announceResourcesIfComplete();
        }
    }

    JButton speechCancelButton() {
        return speechProgress.cancelButton();
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
            boolean manual = isManualRef(initial.chatApiKeyRef());
            serviceAccess.setSelectedItem(manual ? MODE_MANUAL : MODE_KEEPASS);
            manualServiceUrl.setText(manual ? initial.chatBaseUrl() : "");
            keePassBaseUrl = manual ? "" : initial.chatBaseUrl();
            saveApiKey.setSelected(manual && setup.canStoreManualKey() && setup.manualKeyStored(MANUAL_SERVICE_REF));
            setText(serviceEntry, stripPrefix(initial.chatApiKeyRef()));
            setText(chatModel, initial.chatModel());
            setText(embeddingModel, initial.embeddingModel());
            setText(speechOutputModel, initial.modelSelection(ModelCategory.TTS.key()));
            setText(visionModel, initial.modelSelection(ModelCategory.VISION.key()));
            setText(rerankModel, initial.modelSelection(ModelCategory.RERANK.key()));
            embeddingDimension.setText(initial.embeddingDimension());
        } finally {
            busy = false;
        }
        certificateLabel.setText("kein Zertifikat");
        showServiceMode();
        showConfluenceCredentials();
    }

    /**
     * Der Entwurf mit den Werten der Schritte; Pairing-Schlüssel immer dauerhaft in der Datei. KeePass bleibt an, wenn
     * ein gewählter Zugang es braucht oder es hier gekoppelt wurde; ein vollständig manueller Zugang verlangt es nicht.
     */
    private SettingsForm collect() {
        boolean keePass = form.keePassEnabled() || !manualService() || !manualConfluence() || keePassUsed;
        SettingsForm.Builder b = form.toBuilder()
                .keePassEnabled(keePass)
                .keePassHost(keePassHost.getText())
                .keePassPort(keePassPort.getText())
                .keePassClientDisplayName(keePassClient.getText())
                .keePassPairingKeyStore(SettingsForm.PAIRING_KEY_STORE_FILE);
        String entry = text(serviceEntry);
        if (manualService()) {
            b.chatApiKeyRef(MANUAL_SERVICE_REF).chatBaseUrl(manualServiceUrl.getText().trim());
        } else {
            if (!entry.isEmpty()) {
                b.chatApiKeyRef(KEEPASS_PREFIX + entry);
            }
            b.chatBaseUrl(keePassBaseUrl);
        }
        b.chatModel(text(chatModel)).embeddingModel(text(embeddingModel))
                .embeddingDimension(embeddingDimension.getText());
        String speechOutput = text(speechOutputModel);
        if (!speechOutput.isEmpty()) {
            b.modelSelection(ModelCategory.TTS.key(), speechOutput);
        }
        String vision = text(visionModel);
        if (!vision.isEmpty()) {
            b.modelSelection(ModelCategory.VISION.key(), vision);
        }
        // Empty removes model.rerank instead of silently keeping an earlier re-ranker.
        b.modelSelection(ModelCategory.RERANK.key(), text(rerankModel));
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
        return storedTitle(title) != null;
    }

    /**
     * The title of the read entry matching {@code title}, trimmed and case-insensitive like KeePassRPC;
     * {@code null} = none.
     */
    private String storedTitle(String title) {
        String wanted = title == null ? "" : title.trim();
        if (wanted.isEmpty()) {
            return null;
        }
        for (PasswordEntry entry : entries) {
            if (entry.title().trim().equalsIgnoreCase(wanted)) {
                return entry.title();
            }
        }
        return null;
    }

    /**
     * Why no new entry may be created under this title ({@code null}: it may). The list is only a pre-check; the
     * create path checks the current KeePass state itself.
     */
    private String newEntryProblem(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "Bitte einen Titel für den neuen KeePass-Eintrag eintragen.";
        }
        if (!entriesLoaded) {
            return entriesMissingHint();
        }
        String stored = storedTitle(title);
        if (stored != null) {
            return "In KeePass gibt es bereits den Eintrag „" + stored + "“; er bleibt unverändert. Bitte einen "
                    + "anderen Titel wählen oder den Eintrag direkt in KeePass bearbeiten.";
        }
        return null;
    }

    private void addEntry(PasswordEntry entry) {
        List<PasswordEntry> updated = new ArrayList<PasswordEntry>();
        for (PasswordEntry existing : entries) {
            if (!existing.title().trim().equalsIgnoreCase(entry.title().trim())) {
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

    /** {@code true} für eine manuelle Referenz ({@code ask:…}), wie {@code VaultAccess.isManual}. */
    static boolean isManualRef(String ref) {
        return ref != null && ref.trim().regionMatches(true, 0, "ask:", 0, "ask:".length());
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

    /**
     * Fills an entry choice with the read titles; the typed title stays as editor text even if it does not exist
     * (any more), but is then not listed as an item.
     */
    private void setEntryChoices(JComboBox<String> combo, List<String> titles) {
        boolean was = busy;
        busy = true;
        try {
            String typed = text(combo);
            combo.setModel(new DefaultComboBoxModel<String>(titles.toArray(new String[0])));
            setText(combo, typed);
        } finally {
            busy = was;
        }
    }

    /** Runs {@code action} on every edit in an editable combo box editor, not only on Enter. */
    private void onTyped(final JComboBox<String> combo, final Runnable action) {
        final SimpleDocumentListener typed = new SimpleDocumentListener(() -> {
            if (!busy) {
                action.run();
            }
        });
        listenToEditor(combo, typed);
        // A look-and-feel change (comic UI) installs a new editor; the listener moves along.
        combo.addPropertyChangeListener("editor", e -> listenToEditor(combo, typed));
    }

    private static void listenToEditor(JComboBox<String> combo, SimpleDocumentListener listener) {
        java.awt.Component editor = combo.getEditor() == null ? null : combo.getEditor().getEditorComponent();
        if (editor instanceof javax.swing.text.JTextComponent) {
            javax.swing.text.Document document = ((javax.swing.text.JTextComponent) editor).getDocument();
            document.removeDocumentListener(listener);
            document.addDocumentListener(listener);
        }
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

    /** Eine Auswahl im Comic-Look wie die Spracheingabe des letzten Schritts; editierbar bleibt sie eintippbar. */
    private static JComboBox<String> comic(JComboBox<String> combo) {
        return ComicComboBoxUI.install(combo);
    }

    private static JComboBox<String> accessChoice() {
        JComboBox<String> combo = comic(new JComboBox<String>(new String[] {MODE_KEEPASS, MODE_MANUAL}));
        combo.setToolTipText("KeePass: Zugangsdaten aus dem KeePass-Eintrag. Manuell: hier eingeben, ohne KeePass.");
        return combo;
    }

    private void comicCheckBox(JCheckBox box) {
        box.setOpaque(false);
        box.setIcon(new ResearchCheckIcon());
        box.setSelectedIcon(new ResearchCheckIcon());
        box.setForeground(palette.getInk());
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

    String serviceEntryStatusText() {
        return serviceEntryStatus.getText();
    }

    boolean serviceFieldsShown() {
        return serviceMissing.isVisible();
    }

    ComposerButton serviceEntryRefreshButton() {
        return serviceEntryRefresh;
    }

    ComposerButton confluenceEntryRefreshButton() {
        return confluenceEntryRefresh;
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

    String serviceStatusText() {
        return serviceStatus.getText();
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

    JComboBox<String> speechOutputModelChoice() {
        return speechOutputModel;
    }

    JComboBox<String> rerankModelChoice() {
        return rerankModel;
    }

    JComboBox<String> visionModelChoice() {
        return visionModel;
    }

    JComboBox<String> embeddingModelChoice() {
        return embeddingModel;
    }

    String modelsStatusText() {
        return modelsStatus.getText();
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

    JCheckBox speechCheckBox() {
        return speechCheck;
    }

    ComposerButton speechRetryButton() {
        return speechRetry;
    }

    DownloadProgress speechProgress() {
        return speechProgress;
    }

    ComposerButton installAllButton() {
        return installAll;
    }

    /** The state text of an OpenNLP model line or {@code null} when the step lists no such model. */
    String nlpState(String id) {
        for (Component part : nlpLines.getComponents()) {
            if (("nlp-state-" + id).equals(part.getName())) {
                return ((JLabel) part).getText();
            }
            if (part instanceof Container) {
                for (Component inner : ((Container) part).getComponents()) {
                    if (("nlp-state-" + id).equals(inner.getName())) {
                        return ((JLabel) inner).getText();
                    }
                }
            }
        }
        return null;
    }

    String speechStatusText() {
        return speechStatus.getText();
    }

    JComboBox<String> serviceAccessChoice() {
        return serviceAccess;
    }

    JComboBox<String> confluenceAccessChoice() {
        return confluenceAccess;
    }

    JTextField manualServiceUrlField() {
        return manualServiceUrl;
    }

    JPasswordField manualServiceKeyField() {
        return manualServiceKey;
    }

    JCheckBox saveApiKeyBox() {
        return saveApiKey;
    }

    String saveApiKeyNoteText() {
        return saveApiKeyNote.getText();
    }

    JCheckBox rememberUserBox() {
        return rememberUser;
    }

    /** Für Tests: ob die Felder des Zugangs „KeePass“ im Schritt KI-Dienst sichtbar sind. */
    boolean serviceKeePassShown() {
        return serviceEntry.isVisible();
    }

    boolean serviceManualShown() {
        return manualServiceUrl.isVisible();
    }

    boolean confluenceKeePassShown() {
        return confluenceEntry.isVisible();
    }

        ComposerButton serviceTestButton() {
        return serviceTest;
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

        /** Beschriftung (falls vorhanden) und Feld, damit ein Schritt sie zusammen ein- und ausblenden kann. */
        List<JComponent> field(String label, JComponent component) {
            List<JComponent> parts = new ArrayList<JComponent>();
            parts.add(component);
            GridBagConstraints c = constraints(0);
            c.insets = new Insets(3, 0, 3, 10);
            if (!label.isEmpty()) {
                JLabel caption = fieldLabel(label);
                parts.add(caption);
                panel.add(caption, c);
            }
            c = constraints(1);
            c.insets = new Insets(3, 0, 3, 0);
            c.fill = component instanceof JTextField || component instanceof JComboBox
                    ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
            c.weightx = 1;
            panel.add(component, c);
            row++;
            return parts;
        }

        /** Like {@link #field}, but the component fills the field column, flush with the fields above it. */
        void stretched(String label, JComponent component) {
            field(label, component);
            GridBagLayout layout = (GridBagLayout) panel.getLayout();
            GridBagConstraints c = layout.getConstraints(component);
            c.fill = GridBagConstraints.HORIZONTAL;
            layout.setConstraints(component, c);
        }

        List<JComponent> wide(JComponent component) {
            GridBagConstraints c = constraints(0);
            c.gridwidth = 2;
            c.fill = GridBagConstraints.HORIZONTAL;
            c.weightx = 1;
            c.insets = new Insets(3, 0, 3, 0);
            panel.add(component, c);
            row++;
            return Collections.<JComponent>singletonList(component);
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

    /**
     * Fehlertext, der schon vor seiner ersten Anordnung in der Breite des Assistenten umbricht. Ein umbrechendes
     * {@link JTextArea} ohne eigene Breite meldet sonst ein Wort je Zeile als Wunschhöhe; die Fehlerplatte nähme
     * beim ersten Fehler eines Schritts die ganze Seite ein und verdrängte Felder und Merlin.
     */
    private final class WrappedErrorText extends JTextArea {
        /** The width the text will get before its first layout; {@code null} = the error plate's. */
        private final java.util.function.IntSupplier width;

        WrappedErrorText() {
            this(null);
        }

        WrappedErrorText(java.util.function.IntSupplier width) {
            this.width = width;
        }

        @Override
        public Dimension getPreferredSize() {
            int width = getWidth() > 0 ? getWidth() : expectedWidth();
            if (width <= 0 || !getLineWrap()) {
                return super.getPreferredSize();
            }
            // Measure the wrapped height for the width the text will get, not for its current zero width.
            Insets own = getInsets();
            View root = getUI().getRootView(this);
            root.setSize(Math.max(1, width - own.left - own.right), Integer.MAX_VALUE);
            int height = (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + own.top + own.bottom;
            return new Dimension(width, height);
        }

        private int expectedWidth() {
            if (this.width != null) {
                return this.width.getAsInt();
            }
            Insets wizard = SetupWizardPanel.this.getInsets();
            Insets plate = errorPlate.getInsets();
            int width = SetupWizardPanel.this.getWidth() - wizard.left - wizard.right - plate.left - plate.right;
            BorderLayout layout = (BorderLayout) errorPlate.getLayout();
            Component east = layout.getLayoutComponent(BorderLayout.EAST);
            if (east != null && east.isVisible()) {
                width -= east.getPreferredSize().width + layout.getHgap();
            }
            return width;
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

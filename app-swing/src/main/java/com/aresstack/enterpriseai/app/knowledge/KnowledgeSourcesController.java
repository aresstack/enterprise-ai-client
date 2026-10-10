package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;
import com.aresstack.enterpriseai.app.ui.settings.SourcePanel;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceActions;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Die Anbindung des Drawer-Reiters „Wissensquellen“ ({@link KnowledgeSourceActions}): Häkchen, „Jetzt
 * indexieren“, Hinzufügen, Bearbeiten und Entfernen.
 *
 * <ul>
 *   <li><b>Häkchen</b> wirken sofort: die {@link KnowledgeSourceSelection} entscheidet, was RAG durchsucht und was
 *       die Start-Indexierung überspringt; dauerhaft steht es über {@link SourceActions#setEnabled} in der Datei.</li>
 *   <li><b>Hinzufügen und Bearbeiten</b> laufen über den Quellen-Dialog und schreiben die Datei. Mit einer
 *       {@code sourceFactory} wird die gespeicherte Quelle sofort angebunden und indexiert; ohne sie gilt die
 *       Änderung nach dem nächsten Start, und die Zeile sagt das.</li>
 *   <li><b>Indexstand</b>: Seiten im Index je Quelle (auf dem Arbeits-Executor gezählt), dazu das Ergebnis des
 *       letzten Laufs dieser Sitzung; die {@link KnowledgeIndexingBinding} meldet Beginn und Ende jedes Laufs.</li>
 *   <li>Eine Quelle der Datei, die nicht angebunden ist, zeigt ihr erstes Problem oder „gilt nach dem Neustart“.</li>
 * </ul>
 *
 * <p>Alle öffentlichen Methoden auf dem UI-Thread; die Liste geht über {@code view} an die Oberfläche.
 */
public final class KnowledgeSourcesController implements KnowledgeSourceActions {

    private static final Logger LOG = Logger.getLogger(KnowledgeSourcesController.class.getName());
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("HH:mm");

    static final String RUNNING_TEXT = "Wird indexiert …";
    static final String NOT_INDEXED_TEXT = "Noch nicht indexiert";
    static final String DISABLED_TEXT = "Abgewählt: wird nicht indexiert und nicht durchsucht";
    static final String RESTART_TEXT = "Gespeichert; gilt nach dem nächsten Start";
    static final String COUNTING_TEXT = "Indexstand wird gelesen …";

    /** Öffnet den Quellen-Dialog und liefert, wie er endete (produktiv modal über {@code SourceDialog}). */
    public interface SourceEditorLauncher {
        Result edit(SourceForm initial, String originalId, SourceActions actions);

        /** Wie der Dialog endete und, nach dem Speichern, die gespeicherte Quelle. */
        final class Result {
            private final SourcePanel.Outcome outcome;
            private final SourceForm source;

            public Result(SourcePanel.Outcome outcome, SourceForm source) {
                this.outcome = outcome;
                this.source = source;
            }

            public SourcePanel.Outcome outcome() {
                return outcome;
            }

            public SourceForm source() {
                return source;
            }
        }
    }

    /** Liest eine gespeicherte Quelle streng aus der Datei (produktiv {@code FileSourceActions#sourceConfig}). */
    public interface SourceResolver {
        SourceConfig resolve(String id) throws IOException;
    }

    private final List<SourceConfig> startupSources;
    private final Map<String, KnowledgeSourceRegistration> live = new LinkedHashMap<String, KnowledgeSourceRegistration>();
    private final Set<String> pendingRestart = new HashSet<String>();
    private final Map<String, Integer> counts = new HashMap<String, Integer>();
    private final Map<String, String> lastRun = new HashMap<String, String>();
    private final Set<String> failedRuns = new HashSet<String>();
    private final Map<String, String> writeProblems = new HashMap<String, String>();
    private final KnowledgeSourceSelection selection;
    private final KnowledgeIndexingBinding binding;
    private final Function<KnowledgeSourceId, Integer> indexCount;
    private final Function<SourceConfig, KnowledgeSourcePort> sourceFactory;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    private Consumer<List<KnowledgeSourceItem>> view;
    private SourceActions file;
    private SourceResolver resolver;
    private SourceEditorLauncher launcher;
    private String running;

    /**
     * @param startupSources die beim Start geladenen Quellen (Anzeige, solange keine Datei angeschlossen ist)
     * @param catalog        die beim Start angebundenen Quellen
     * @param indexCount     Seiten im Index je Quelle; blockiert, läuft auf {@code workExecutor} ({@code null}: keine)
     * @param sourceFactory  baut neu gespeicherte Quellen ohne Neustart ({@code null}: Änderungen nach dem Neustart)
     */
    public KnowledgeSourcesController(List<SourceConfig> startupSources, KnowledgeSourceCatalog catalog,
                                      KnowledgeSourceSelection selection, KnowledgeIndexingBinding binding,
                                      Function<KnowledgeSourceId, Integer> indexCount,
                                      Function<SourceConfig, KnowledgeSourcePort> sourceFactory,
                                      Executor uiExecutor, Executor workExecutor, LongSupplier clock, ZoneId zone) {
        if (startupSources == null || catalog == null || selection == null || binding == null || uiExecutor == null
                || workExecutor == null || clock == null || zone == null) {
            throw new IllegalArgumentException("only indexCount and sourceFactory may be null");
        }
        this.startupSources = new ArrayList<SourceConfig>(startupSources);
        for (KnowledgeSourceRegistration registration : catalog.registrations()) {
            live.put(registration.sourceId().value(), registration);
        }
        this.selection = selection;
        this.binding = binding;
        this.indexCount = indexCount;
        this.sourceFactory = sourceFactory;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
        this.clock = clock;
        this.zone = zone;
        binding.addRunListener(new KnowledgeIndexingBinding.RunListener() {
            @Override
            public void started(KnowledgeSourceId sourceId) {
                running = sourceId.value();
                publish();
            }

            @Override
            public void finished(IndexingReport report) {
                running = null;
                record(report);
                count(report.sourceId().value());
                publish();
            }
        });
    }

    /** Wohin die Liste geht (auf dem UI-Thread); zählt danach einmal den Index aller angebundenen Quellen. */
    public void attach(Consumer<List<KnowledgeSourceItem>> target) {
        this.view = target;
        for (String id : live.keySet()) {
            count(id);
        }
        publish();
    }

    /** Die Konfigurationsdatei und der Dialog; ohne sie lässt sich nur an- und abwählen und indexieren. */
    public void setEditing(SourceActions sourceFile, SourceResolver sourceResolver, SourceEditorLauncher editor) {
        this.file = sourceFile;
        this.resolver = sourceResolver;
        this.launcher = editor;
        publish();
    }

    // ------------------------------------------------------------------ Aktionen des Reiters

    @Override
    public void enabledChanged(String sourceId, boolean enabled) {
        selection.setEnabled(KnowledgeSourceId.of(sourceId), enabled);
        writeProblems.remove(sourceId);
        if (file != null) {
            try {
                file.setEnabled(sourceId, enabled);
            } catch (IOException | RuntimeException e) {
                LOG.log(Level.WARNING, "Häkchen der Quelle " + sourceId + " nicht gespeichert", e);
                writeProblems.put(sourceId, "Häkchen nicht gespeichert (" + e.getClass().getSimpleName()
                        + "); gilt bis zum Beenden");
            }
        }
        publish();
    }

    @Override
    public void indexRequested(String sourceId) {
        KnowledgeSourceRegistration registration = live.get(sourceId);
        if (registration == null || pendingRestart.contains(sourceId)
                || !selection.isEnabled(registration.sourceId())) {
            return;
        }
        try {
            binding.indexSource(registration.port(), registration.scope(), null);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Indexierung von " + sourceId + " nicht gestartet", e);
        }
    }

    @Override
    public void editRequested(String sourceId) {
        if (!canAdd()) {
            return;
        }
        SourceForm current = null;
        for (SourceForm source : fileSources()) {
            if (source.id().equals(sourceId)) {
                current = source;
            }
        }
        if (current == null) {
            return;
        }
        SourceEditorLauncher.Result result = launcher.edit(current, sourceId, file);
        if (result == null) {
            return;
        }
        if (result.outcome() == SourcePanel.Outcome.REMOVED) {
            detach(sourceId);
        } else if (result.outcome() == SourcePanel.Outcome.SAVED && result.source() != null) {
            if (!result.source().id().equals(sourceId)) {
                detach(sourceId);
            }
            connect(result.source().id());
        }
        publish();
    }

    @Override
    public void addRequested(String type) {
        if (!canAdd()) {
            return;
        }
        String kind = SourceForm.TYPE_CONFLUENCE.equals(type) ? SourceForm.TYPE_CONFLUENCE : SourceForm.TYPE_MEDIAWIKI;
        SourceEditorLauncher.Result result = launcher.edit(SourceForm.builder(freeId(kind), kind).build(), null, file);
        if (result != null && result.outcome() == SourcePanel.Outcome.SAVED && result.source() != null) {
            connect(result.source().id());
        }
        publish();
    }

    @Override
    public boolean canAdd() {
        return file != null && launcher != null;
    }

    // ------------------------------------------------------------------ Anbinden

    /** Bindet die gespeicherte Quelle an (ersetzt eine alte) und indexiert sie, wenn sie angehakt ist. */
    private void connect(String id) {
        writeProblems.remove(id);
        if (sourceFactory == null || resolver == null) {
            pendingRestart.add(id);
            return;
        }
        final SourceConfig config;
        final KnowledgeSourcePort port;
        try {
            config = resolver.resolve(id);
            port = sourceFactory.apply(config);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Quelle " + id + " nicht angebunden; gilt nach dem nächsten Start", e);
            pendingRestart.add(id);
            return;
        }
        pendingRestart.remove(id);
        lastRun.remove(id);
        failedRuns.remove(id);
        counts.remove(id);
        KnowledgeSourceRegistration registration = new KnowledgeSourceRegistration(port, config.scope());
        live.put(id, registration);
        selection.register(registration.sourceId(), config.enabled());
        if (config.enabled() && !binding.isRunning()) {
            indexRequested(id);
        } else {
            count(id);
        }
    }

    /** Die Quelle ist nicht mehr konfiguriert: nicht mehr anbieten und nicht mehr durchsuchen. */
    private void detach(String id) {
        KnowledgeSourceRegistration registration = live.remove(id);
        if (registration != null) {
            selection.unregister(registration.sourceId());
        }
        pendingRestart.remove(id);
        counts.remove(id);
        lastRun.remove(id);
        failedRuns.remove(id);
        writeProblems.remove(id);
    }

    // ------------------------------------------------------------------ Indexstand

    private void record(IndexingReport report) {
        String id = report.sourceId().value();
        String time = STAMP.format(Instant.ofEpochMilli(clock.getAsLong()).atZone(zone));
        if (report.discoveryFailed()) {
            failedRuns.add(id);
            lastRun.put(id, "Indexierung " + time + " fehlgeschlagen: Quelle nicht lesbar (Protokoll)");
            return;
        }
        int failed = report.count(IndexingStatus.FAILED);
        if (report.isCancelled()) {
            failedRuns.remove(id);
            lastRun.put(id, "abgebrochen " + time);
        } else if (failed > 0) {
            failedRuns.add(id);
            lastRun.put(id, "Stand " + time + ", " + failed + " fehlgeschlagen (Protokoll)");
        } else {
            failedRuns.remove(id);
            lastRun.put(id, "Stand " + time);
        }
    }

    /** Zählt die Seiten einer Quelle im Index auf dem Arbeits-Executor und meldet das Ergebnis auf dem UI-Thread. */
    private void count(final String id) {
        if (indexCount == null) {
            return;
        }
        final KnowledgeSourceId sourceId = KnowledgeSourceId.of(id);
        try {
            workExecutor.execute(() -> {
                Integer n;
                try {
                    n = indexCount.apply(sourceId);
                } catch (RuntimeException e) {
                    LOG.log(Level.FINE, "Indexstand von " + id + " nicht lesbar", e);
                    n = null;
                }
                final Integer result = n;
                uiExecutor.execute(() -> {
                    if (result != null && live.containsKey(id)) {
                        counts.put(id, result);
                        publish();
                    }
                });
            });
        } catch (RuntimeException rejected) {
            // Beim Beenden nimmt der Executor nichts mehr an; der Stand bleibt dann unbekannt.
        }
    }

    // ------------------------------------------------------------------ Liste

    private List<SourceForm> fileSources() {
        if (file == null) {
            return Collections.emptyList();
        }
        try {
            return file.sources();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Wissensquellen der Konfigurationsdatei nicht lesbar", e);
            return Collections.emptyList();
        }
    }

    /** Die Zeilen in Dateireihenfolge (ohne Datei: die beim Start geladenen Quellen). */
    public List<KnowledgeSourceItem> items() {
        List<KnowledgeSourceItem> items = new ArrayList<KnowledgeSourceItem>();
        if (file == null) {
            for (SourceConfig source : startupSources) {
                String id = source.sourceId().value();
                items.add(item(id, label(source.type()), String.join(", ", source.scope().startPoints()),
                        null));
            }
            return items;
        }
        for (SourceForm source : fileSources()) {
            if (source.id().isEmpty()) {
                continue;
            }
            items.add(item(source.id(), label(source.type()), source.startPoints(), source));
        }
        return items;
    }

    private KnowledgeSourceItem item(String id, String kind, String scope, SourceForm form) {
        KnowledgeSourceRegistration registration = live.get(id);
        boolean connected = registration != null && !pendingRestart.contains(id);
        boolean enabled = connected ? selection.isEnabled(registration.sourceId()) : form == null || form.enabled();
        boolean editable = canAdd() && form != null;
        String status;
        KnowledgeSourceItem.State state = KnowledgeSourceItem.State.IDLE;
        if (writeProblems.containsKey(id)) {
            status = writeProblems.get(id);
            state = KnowledgeSourceItem.State.PROBLEM;
        } else if (!connected) {
            List<String> problems = form == null || file == null ? Collections.<String>emptyList()
                    : file.validate(form, id);
            if (!problems.isEmpty()) {
                status = "Fehlerhaft: " + problems.get(0);
                state = KnowledgeSourceItem.State.PROBLEM;
            } else {
                status = RESTART_TEXT;
            }
        } else if (id.equals(running)) {
            status = RUNNING_TEXT;
            state = KnowledgeSourceItem.State.RUNNING;
        } else if (!enabled) {
            status = DISABLED_TEXT;
        } else {
            status = indexText(id);
            if (failedRuns.contains(id)) {
                state = KnowledgeSourceItem.State.PROBLEM;
            }
        }
        boolean indexable = connected && enabled && !binding.isRunning();
        return new KnowledgeSourceItem(id, kind, scope, enabled, status, state, indexable, editable);
    }

    private String indexText(String id) {
        Integer count = counts.get(id);
        String run = lastRun.get(id);
        String pages = count == null ? (indexCount == null ? "" : COUNTING_TEXT)
                : count == 0 ? NOT_INDEXED_TEXT : count == 1 ? "1 Seite im Index" : count + " Seiten im Index";
        if (run == null) {
            return pages.isEmpty() ? NOT_INDEXED_TEXT : pages;
        }
        if (failedRuns.contains(id) && run.startsWith("Indexierung")) {
            return run;
        }
        return pages.isEmpty() || pages.equals(COUNTING_TEXT) ? run : pages + " · " + run;
    }

    private static String label(String type) {
        return SourceForm.TYPE_CONFLUENCE.equalsIgnoreCase(type) ? "Confluence" : "MediaWiki";
    }

    private String freeId(String type) {
        Set<String> taken = new HashSet<String>(live.keySet());
        for (SourceForm source : fileSources()) {
            taken.add(source.id());
        }
        String base = SourceForm.TYPE_CONFLUENCE.equals(type) ? "confluence" : "wiki";
        String candidate = base;
        int n = 2;
        while (taken.contains(candidate)) {
            candidate = base + n++;
        }
        return candidate;
    }

    private void publish() {
        if (view != null) {
            view.accept(items());
        }
    }
}

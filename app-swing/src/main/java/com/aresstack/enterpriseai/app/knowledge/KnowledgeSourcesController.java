package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceActions;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.application.knowledge.IndexingReport;
import com.aresstack.enterpriseai.application.knowledge.IndexingStatus;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;

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
 *       die Start-Indexierung überspringt; dauerhaft steht es über den Use Case in der Datei.</li>
 *   <li><b>„+ Quelle“ und Bearbeiten</b> laufen über den Quellen-Dialog; welche Quelltypen es gibt und welche
 *       Felder sie haben, liefert {@link KnowledgeSourceManagement} aus den registrierten Adaptern. Die gespeicherte
 *       Quelle wird sofort angebunden und indexiert; bietet kein Adapter ihren Typ an, gilt die Änderung nach dem
 *       nächsten Start, und die Zeile sagt das.</li>
 *   <li><b>Entfernen</b> fragt kurz nach, nimmt die Zeile sofort aus der Liste und lässt den Use Case die Quelle
 *       aus der Datei nehmen und ihren Index zurückziehen (auf dem Arbeits-Executor).</li>
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

    /** Öffnet den Quellen-Dialog und die Rückfrage vor dem Entfernen (produktiv modal über {@code SourceDialog}). */
    public interface SourceEditorLauncher {
        /** Die gespeicherte Quelle oder {@code null}, wenn abgebrochen. */
        SourceDefinition edit(SourceDefinition initial, String originalId, List<KnowledgeSourceType> types,
                              SourceActions actions);

        /** {@code true}: wirklich entfernen. */
        boolean confirmRemove(String sourceId, String typeName);
    }

    private final List<SourceDefinition> startupSources;
    private final Map<String, KnowledgeSourceRegistration> live = new LinkedHashMap<String, KnowledgeSourceRegistration>();
    private final Set<String> pendingRestart = new HashSet<String>();
    private final Map<String, Integer> counts = new HashMap<String, Integer>();
    private final Map<String, String> lastRun = new HashMap<String, String>();
    private final Set<String> failedRuns = new HashSet<String>();
    private final Map<String, String> writeProblems = new HashMap<String, String>();
    private final Set<String> removing = new HashSet<String>();
    private final KnowledgeSourceSelection selection;
    private final KnowledgeIndexingBinding binding;
    private final Function<KnowledgeSourceId, Integer> indexCount;
    private final KnowledgeSourceManagement management;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private final LongSupplier clock;
    private final ZoneId zone;
    private Consumer<List<KnowledgeSourceItem>> view;
    private KnowledgeSourceManagement editing;
    private SourceEditorLauncher launcher;
    private String running;

    /**
     * @param startupSources die beim Start geladenen Quellen (Anzeige, solange keine Datei angeschlossen ist)
     * @param catalog        die beim Start angebundenen Quellen
     * @param management     Quelltypen, Prüfen, Speichern, Entfernen und Anbinden (Use Case; ohne Ablage)
     * @param indexCount     Seiten im Index je Quelle; blockiert, läuft auf {@code workExecutor} ({@code null}: keine)
     */
    public KnowledgeSourcesController(List<SourceDefinition> startupSources, KnowledgeSourceCatalog catalog,
                                      KnowledgeSourceSelection selection, KnowledgeIndexingBinding binding,
                                      Function<KnowledgeSourceId, Integer> indexCount,
                                      KnowledgeSourceManagement management,
                                      Executor uiExecutor, Executor workExecutor, LongSupplier clock, ZoneId zone) {
        if (startupSources == null || catalog == null || selection == null || binding == null || management == null
                || uiExecutor == null || workExecutor == null || clock == null || zone == null) {
            throw new IllegalArgumentException("only indexCount may be null");
        }
        this.startupSources = new ArrayList<SourceDefinition>(startupSources);
        for (KnowledgeSourceRegistration registration : catalog.registrations()) {
            live.put(registration.sourceId().value(), registration);
        }
        this.selection = selection;
        this.binding = binding;
        this.indexCount = indexCount;
        this.management = management;
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

    /** Die Ablage (Konfigurationsdatei) und der Dialog; ohne sie lässt sich nur an- und abwählen und indexieren. */
    public void setEditing(SourceDefinitionStore store, SourceEditorLauncher editor) {
        this.editing = store == null ? null : management.withStore(store);
        this.launcher = editor;
        publish();
    }

    // ------------------------------------------------------------------ Aktionen des Reiters

    @Override
    public void enabledChanged(String sourceId, boolean enabled) {
        selection.setEnabled(KnowledgeSourceId.of(sourceId), enabled);
        writeProblems.remove(sourceId);
        if (editing != null) {
            try {
                editing.setEnabled(sourceId, enabled);
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
        if (!canAdd() || removing.contains(sourceId)) {
            return;
        }
        SourceDefinition current = null;
        for (SourceDefinition source : fileSources()) {
            if (source.id().equals(sourceId)) {
                current = source;
            }
        }
        if (current == null) {
            return;
        }
        KnowledgeSourceType type = management.type(current.typeId());
        List<KnowledgeSourceType> types = type == null ? Collections.<KnowledgeSourceType>emptyList()
                : Collections.singletonList(type);
        SourceDefinition saved = launcher.edit(current, sourceId, types, dialogActions());
        if (saved != null) {
            if (!saved.id().equals(sourceId)) {
                detach(sourceId);
            }
            connect(saved.id());
        }
        publish();
    }

    @Override
    public void addRequested() {
        List<KnowledgeSourceType> types = management.types();
        if (!canAdd() || types.isEmpty()) {
            return;
        }
        SourceDefinition initial = editing.draft(types.get(0).id(), taken());
        SourceDefinition saved = launcher.edit(initial, null, types, dialogActions());
        if (saved != null) {
            connect(saved.id());
        }
        publish();
    }

    @Override
    public void removeRequested(final String sourceId) {
        if (!canAdd() || removing.contains(sourceId) || sourceId.equals(running)) {
            return;
        }
        SourceDefinition current = null;
        for (SourceDefinition source : fileSources()) {
            if (source.id().equals(sourceId)) {
                current = source;
            }
        }
        if (current == null || !launcher.confirmRemove(sourceId, label(current.typeId()))) {
            return;
        }
        // Sofort aus der Liste und aus Chat/Indexierung; Datei und Index räumt der Use Case im Hintergrund.
        removing.add(sourceId);
        detach(sourceId);
        publish();
        final KnowledgeSourceManagement target = editing;
        try {
            workExecutor.execute(() -> {
                Exception failure = null;
                try {
                    target.remove(sourceId);
                } catch (IOException | RuntimeException e) {
                    failure = e;
                }
                final Exception problem = failure;
                uiExecutor.execute(() -> {
                    removing.remove(sourceId);
                    if (problem != null) {
                        LOG.log(Level.WARNING, "Quelle " + sourceId + " nicht vollständig entfernt", problem);
                        writeProblems.put(sourceId, "Entfernen fehlgeschlagen ("
                                + problem.getClass().getSimpleName() + "); bitte erneut versuchen");
                    }
                    publish();
                });
            });
        } catch (RuntimeException rejected) {
            removing.remove(sourceId);
            writeProblems.put(sourceId, "Entfernen fehlgeschlagen; bitte erneut versuchen");
            publish();
        }
    }

    @Override
    public boolean canAdd() {
        return editing != null && launcher != null;
    }

    /** Was der Dialog braucht, über dem Use Case (Typwechsel holt einen frischen Entwurf). */
    private SourceActions dialogActions() {
        final KnowledgeSourceManagement target = editing;
        return new SourceActions() {
            @Override
            public List<String> validate(SourceDefinition draft, String originalId) {
                return target.validate(draft, originalId);
            }

            @Override
            public void save(SourceDefinition draft, String originalId) throws IOException {
                target.save(draft, originalId);
            }

            @Override
            public SourceDefinition draft(String typeId) {
                return target.draft(typeId, taken());
            }
        };
    }

    private Set<String> taken() {
        Set<String> taken = new HashSet<String>(live.keySet());
        for (SourceDefinition source : fileSources()) {
            taken.add(source.id());
        }
        return taken;
    }

    // ------------------------------------------------------------------ Anbinden

    /** Bindet die gespeicherte Quelle an (ersetzt eine alte) und indexiert sie, wenn sie angehakt ist. */
    private void connect(String id) {
        writeProblems.remove(id);
        final SourceDefinition config;
        final KnowledgeSourceRegistration registration;
        try {
            config = editing.find(id);
            if (config == null || management.type(config.typeId()) == null) {
                pendingRestart.add(id);
                return;
            }
            registration = editing.open(config);
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Quelle " + id + " nicht angebunden; gilt nach dem nächsten Start", e);
            pendingRestart.add(id);
            return;
        }
        pendingRestart.remove(id);
        lastRun.remove(id);
        failedRuns.remove(id);
        counts.remove(id);
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

    private List<SourceDefinition> fileSources() {
        if (editing == null) {
            return Collections.emptyList();
        }
        try {
            return editing.definitions();
        } catch (IOException | RuntimeException e) {
            LOG.log(Level.WARNING, "Wissensquellen der Konfigurationsdatei nicht lesbar", e);
            return Collections.emptyList();
        }
    }

    /** Die Zeilen in Dateireihenfolge (ohne Datei: die beim Start geladenen Quellen). */
    public List<KnowledgeSourceItem> items() {
        List<KnowledgeSourceItem> items = new ArrayList<KnowledgeSourceItem>();
        if (editing == null) {
            for (SourceDefinition source : startupSources) {
                items.add(item(source.id(), label(source.typeId()), summary(source), null));
            }
            return items;
        }
        for (SourceDefinition source : fileSources()) {
            if (source.id().isEmpty() || removing.contains(source.id())) {
                continue;
            }
            items.add(item(source.id(), label(source.typeId()), summary(source), source));
        }
        return items;
    }

    private KnowledgeSourceItem item(String id, String kind, String scope, SourceDefinition form) {
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
            List<String> problems = form == null || editing == null ? Collections.<String>emptyList()
                    : editing.validate(form, id);
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
        boolean removable = editable && !id.equals(running);
        return new KnowledgeSourceItem(id, kind, scope, enabled, status, state, indexable, editable, removable);
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

    /** Typ für die Anzeige: der Name, den der Adapter beschreibt, sonst die Typ-ID aus der Datei. */
    private String label(String typeId) {
        KnowledgeSourceType type = management.type(typeId);
        return type == null ? typeId : type.displayName();
    }

    /** Umfang für die Anzeige: der Schlüssel, den der Adapter als Zusammenfassung nennt, sonst die Startpunkte. */
    private String summary(SourceDefinition source) {
        KnowledgeSourceType type = management.type(source.typeId());
        String key = type == null || type.summaryKey().isEmpty() ? "startPoints" : type.summaryKey();
        String value = source.settings().get(key);
        return value.isEmpty() ? source.settings().get("startPoints") : value;
    }

    private void publish() {
        if (view != null) {
            view.accept(items());
        }
    }
}

package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;
import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeFenceView;
import com.aresstack.enterpriseai.research.domain.scope.ScopeFacet;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatch;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatchOperations;
import com.aresstack.enterpriseai.research.domain.scope.UnresolvedScopeIssue;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommand;
import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.domain.state.oo.OoResearchStateMachine;
import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateIds;
import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateMemento;
import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateTransitionResult;
import com.aresstack.enterpriseai.research.api.ResearchScopeDraftStore;
import com.aresstack.enterpriseai.research.scope.ResearchScopeCoordinator;
import com.aresstack.enterpriseai.research.scope.ScopeUpdateResult;
import com.aresstack.enterpriseai.research.store.ScopeUpdateWireCodec;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Die Host-Seite des Recherche-Agenten für EIN Research-Projekt: Besitzer des Weidezauns (Scope-Entwurf samt
 * Blacklist), aus askai-java8 (arch) {@code ResearchAgentSession} übernommen, soweit es den Weidezaun betrifft:
 * {@code currentScopeDraft}, {@code addScopeExclusion}/{@code removeScopeExclusion} (der Wolkenhimmel),
 * {@code applyScopeUpdate} (was ein Scoping-Turn vorschlägt) und die Zaun-Ansicht, die das Modell vor jedem Turn
 * sieht ({@code publishScopeFence}). In askai liefen Agent und Host in zwei Prozessen über ACP/MCP; hier laufen
 * beide im Client, der Host wird direkt gerufen.
 *
 * <p>Ein Projekt ist ein Verzeichnis ({@code scope-draft.json} usw.) und gehört genau einem Chat (askai arch: die
 * Projekt-Kennung einer Research-Session ist der Scope ihres Chat-Tabs). Welches Projekt gilt, bestimmt allein
 * {@link #openChat}: Chat öffnen lädt dessen Projekt (Phase, Konzept, Quellen, Gliederung, Dokument), ein neuer
 * Chat beginnt ein leeres, {@link #deleteChatProject} löscht es mit dem Chat. Einen globalen Zeiger auf ein
 * „aktuelles“ Projekt gibt es nicht; ohne {@link #openChat} arbeitet der Host in einem eigenen, frischen Projekt.
 */
public final class ResearchAgentHost {

    private static final Logger LOG = Logger.getLogger(ResearchAgentHost.class.getName());
    private static final String GREETED_MARKER = "greeted";
    private static final String CONCEPT_DIRECTORY = "concept";
    private static final String STATE_FILE = "research-state";
    private static final String SOURCES_FILE = "sources.tsv";
    private static final String PARKED_FILE = "parked.tsv";
    private static final String IMAGES_DIRECTORY = "images";
    private static final String RUN_FILE = "run-status";
    private static final String OUTLINE_FILE = "outline.md";
    private static final String DOCUMENT_FILE = "document.json";

    private final File projectsRoot;
    private final ResearchProjects projects;
    /** Der Chat, dem das Projekt gehört; {@code null}, bis {@link #openChat} oder der erste Zugriff ihn setzt. */
    private String chatId;
    private final Function<File, ResearchScopeDraftStore> stores;
    private final Function<File, ConceptBranchService> concepts;
    private final List<Runnable> stateListeners = new CopyOnWriteArrayList<Runnable>();
    private volatile Consumer<String> problemSink;
    /** Wer nach einer neuen Quelle weiterarbeitet (Bilder auswerten); bekommt die Dokument-ID. */
    private volatile Consumer<String> sourceAcceptedListener;
    private volatile Function<String, String> passageReader;
    private volatile Function<String, String[]> resourceReferences;
    private volatile CorpusSearch corpusSearch;
    private File projectDirectory;
    private ResearchScopeCoordinator coordinator;
    private ResearchConceptHost conceptHost;
    /** Die Zustandsmaschine der Phasen aus askai arch (Konzept → Quellen → Gliederung → Dokument). */
    private OoResearchStateMachine machine;
    private ResearchStateMemento state;
    /** Der Stand des autonomen Laufs der Phase Quellen (askai ResearchRunProgress/Activity/StopReason). */
    private volatile ResearchRunStatus runStatus = ResearchRunStatus.idle();
    private final ResearchScopeControl scopeControl = new ResearchScopeControl(this);

    /**
     * @param projectsRoot Wurzel der Research-Projekte (je Projekt ein Unterverzeichnis)
     * @param stores       Ablage des Scope-Entwurfs je Projektverzeichnis (Adapter aus der Composition Root)
     * @param concepts     Konzeptpapier (Konzeptbaum mit Inseln) je Konzeptverzeichnis (Adapter aus der Composition Root)
     */
    public ResearchAgentHost(File projectsRoot, Function<File, ResearchScopeDraftStore> stores,
                             Function<File, ConceptBranchService> concepts) {
        if (projectsRoot == null || stores == null || concepts == null) {
            throw new IllegalArgumentException("projectsRoot, stores and concepts must not be null");
        }
        this.projectsRoot = projectsRoot;
        this.projects = new ResearchProjects(projectsRoot);
        this.stores = stores;
        this.concepts = concepts;
    }

    /** Öffnet das Projekt des Chats erst bei Bedarf; ohne Chat ein eigenes, frisches Projekt. */
    private void ensureOpen() {
        if (coordinator == null) {
            if (chatId == null) {
                chatId = newProjectDirectory().getName();
            }
            openProject(projects.directoryFor(chatId));
        }
    }

    // ------------------------------------------------------------------ project (je Chat genau eines)

    /**
     * Bindet den Host an das Research-Projekt eines Chats: das bisherige Projekt wird geschlossen, das des Chats
     * beim nächsten Zugriff geladen (oder, für einen neuen Chat, angelegt). Für denselben Chat ohne Wirkung.
     */
    public void openChat(String chatId) {
        ResearchProjects.folderName(chatId); // prüft die Kennung
        synchronized (this) {
            if (chatId.equals(this.chatId)) {
                return;
            }
            closeProject();
            this.chatId = chatId;
        }
        fireStateChanged();
    }

    /** Der Chat, dem das offene Projekt gehört, oder {@code null}. */
    public synchronized String chatId() {
        return chatId;
    }

    /**
     * Löscht das Research-Projekt eines Chats. Ist es das offene, ist der Host danach an keinen Chat gebunden; der
     * Aufrufer öffnet den Chat, der stattdessen gilt.
     */
    public void deleteChatProject(String chatId) {
        boolean current;
        synchronized (this) {
            current = chatId.equals(this.chatId);
            if (current) {
                closeProject();
                this.chatId = null;
            }
            projects.delete(chatId);
        }
        if (current) {
            fireStateChanged();
        }
    }

    /**
     * Verwirft alle Projekte ohne gespeicherten Chat (Reste nie gespeicherter Chats und der früheren Ablage mit
     * {@code current-project}); das offene Projekt bleibt immer.
     */
    public void retainProjectsOf(java.util.Collection<String> savedChatIds) {
        synchronized (this) {
            List<String> keep = new ArrayList<String>(savedChatIds);
            if (chatId != null) {
                keep.add(chatId);
            }
            projects.retainOnly(keep);
        }
    }

    private void closeProject() {
        projectDirectory = null;
        coordinator = null;
        conceptHost = null;
        machine = null;
        state = null;
        runStatus = ResearchRunStatus.idle();
        scopeControl.clear(); // Such-Handles des alten Zauns gelten im neuen Projekt nicht
    }

    public synchronized File projectDirectory() {
        ensureOpen();
        return projectDirectory;
    }

    private void openProject(File directory) {
        if (!directory.isDirectory() && !directory.mkdirs()) {
            LOG.warning("Research-Projekt nicht anlegbar: " + directory);
        }
        projectDirectory = directory;
        coordinator = new ResearchScopeCoordinator(stores.apply(directory));
        if (!coordinator.isUsable()) {
            LOG.warning("Scope-Entwurf unbrauchbar: " + coordinator.unusableReason());
        }
        conceptHost = new ResearchConceptHost(concepts.apply(new File(directory, CONCEPT_DIRECTORY)),
                new ResearchConceptHost.Owner() {
                    @Override
                    public ResearchScopeCoordinator scopeCoordinator() {
                        return ResearchAgentHost.this.scopeCoordinator();
                    }

                    @Override
                    public void stateChanged() {
                        fireStateChanged();
                    }

                    @Override
                    public boolean isGerman() {
                        return true;
                    }

                    @Override
                    public boolean scopingWritable() {
                        return ResearchAgentHost.this.isWritable(ResearchStateIds.SCOPING);
                    }

                    @Override
                    public String stateDescription() {
                        return ResearchAgentHost.this.phaseId() + "/" + ResearchAgentHost.this.stateId();
                    }
                });
        machine = new OoResearchStateMachine(directory.getName());
        state = readState(directory);
        runStatus = readRunStatus(directory);
    }

    private File newProjectDirectory() {
        String base = "research-" + System.currentTimeMillis();
        File candidate = new File(projectsRoot, base);
        int suffix = 1;
        while (candidate.exists()) {
            candidate = new File(projectsRoot, base + "-" + suffix++);
        }
        return candidate;
    }

    /** Das Projektverzeichnis des gebundenen Chats, falls es schon existiert (legt nichts an). */
    private File existingProject() {
        return chatId != null && projects.exists(chatId) ? projects.directoryFor(chatId) : null;
    }

    /** Ob der Agent dieses Projekt schon begrüßt hat (askai: Zustand scoping/new → weiter). */
    public synchronized boolean isGreeted() {
        if (coordinator == null && existingProject() == null) {
            return false;
        }
        ensureOpen();
        return new File(projectDirectory, GREETED_MARKER).isFile();
    }

    /** askai {@code handleGreetingDone}: die Begrüßung wird nie wiederholt; der Zustand geht scoping/new → running. */
    public void markGreeted() {
        synchronized (this) {
            ensureOpen();
            try {
                File marker = new File(projectDirectory, GREETED_MARKER);
                if (!marker.isFile()) {
                    java.nio.file.Files.write(marker.toPath(), new byte[0]);
                }
            } catch (IOException notWritten) {
                LOG.warning("Begrüßung nicht vermerkt: " + notWritten.getMessage());
            }
        }
        if (ResearchStateIds.NEW.equals(stateId()) && ResearchStateIds.SCOPING.equals(phaseId())) {
            dispatch(ResearchCommandType.START);
        }
    }

    // ------------------------------------------------------------------ phases (askai arch ResearchStateGraph)

    /** Die aktuelle Phase ({@code scoping}, {@code research}, {@code outline}, {@code draft}). */
    public synchronized String phaseId() {
        return peekState().getPhaseId();
    }

    /** Der Laufzustand in der Phase ({@code new}, {@code running}, {@code waiting}, …). */
    public synchronized String stateId() {
        return peekState().getStateId();
    }

    /** askai {@code ResearchToolPolicy.writable}: Schreibwerkzeuge einer Phase nur in ihr und nur „running“. */
    public synchronized boolean isWritable(String requiredPhaseId) {
        ResearchStateMemento current = peekState();
        return requiredPhaseId.equals(current.getPhaseId()) && ResearchStateIds.RUNNING.equals(current.getStateId());
    }

    /**
     * Der Zustand, ohne ein Projekt anzulegen: ein Chat ohne Projekt steht am Anfang (Konzept/neu). Angelegt wird
     * das Projekt erst, wenn der Agent-Modus schreibt (Begrüßung, Übergang, Werkzeug), nicht schon durch Anzeigen.
     */
    private ResearchStateMemento peekState() {
        if (coordinator == null && existingProject() == null) {
            return new OoResearchStateMachine("unopened").initialMemento();
        }
        ensureOpen();
        return state;
    }

    /**
     * Ein Übergang der Zustandsmaschine; nur der Host (Nutzer und Host-Logik) löst ihn aus, nie das Modell
     * (askai: kein advance_phase-Werkzeug). Ändert sich der Zustand, hören Werkzeugsatz und Ansicht mit.
     *
     * @return {@code null} bei Erfolg, sonst der Ablehnungsgrund
     */
    public String dispatch(ResearchCommandType command) {
        String rejection;
        synchronized (this) {
            ensureOpen();
            ResearchStateTransitionResult result = machine.dispatch(state,
                    ResearchCommand.of(command, java.util.UUID.randomUUID().toString()));
            if (result.isAccepted()) {
                state = result.getNextMemento();
                writeState(projectDirectory, state);
                LOG.info("[research-state] " + command + " -> " + state.getPhaseId() + "/" + state.getStateId());
                rejection = null;
            } else {
                rejection = result.getRejectionReason();
                LOG.info("[research-state] " + command + " abgelehnt: " + rejection);
            }
        }
        if (rejection == null) {
            fireStateChanged();
        }
        return rejection;
    }

    // ------------------------------------------------------------------ sources (askai source_accept/source_park)

    /**
     * Übernimmt eine gelesene Seite als Quelle des Projekts (askai {@code source_accept}); nur in Phase Quellen
     * und „running“. Eine Seite zählt einmal. Gespeichert wird wie in corenth nur die Referenz
     * ({@code id, Titel, URL, Bookmark, Revision, Inhalts-Hash}), nie der Seitentext; der liegt im Wissensindex.
     *
     * @return {@code source_id=…}, bei Wiederholung mit {@code ALREADY_ACCEPTED}
     */
    public synchronized String acceptSource(String captureId, String title, String url) {
        ensureOpen();
        String id = clean(captureId);
        List<String> lines = readLines(new File(projectDirectory, SOURCES_FILE));
        for (String line : lines) {
            if (line.startsWith(id + "\t")) {
                return "ALREADY_ACCEPTED source_id=" + id;
            }
        }
        Function<String, String[]> references = resourceReferences;
        String[] reference = references == null ? null : references.apply(id);
        if (reference == null || reference.length < 3) {
            reference = new String[]{"", "", ""};
        }
        appendLine(new File(projectDirectory, SOURCES_FILE), id + "\t" + clean(title) + "\t" + clean(url)
                + "\t" + clean(reference[0]) + "\t" + clean(reference[1]) + "\t" + clean(reference[2]));
        LOG.info("[research-sources] accepted " + id + " " + clean(title));
        fireStateChangedLater();
        Consumer<String> listener = sourceAcceptedListener;
        if (listener != null) {
            listener.accept(id);
        }
        return "source_id=" + id;
    }

    /** Wird nach jeder neu übernommenen Quelle mit ihrer Dokument-ID gerufen (darf nicht blockieren). */
    public void setSourceAcceptedListener(Consumer<String> listener) {
        this.sourceAcceptedListener = listener;
    }

    // ------------------------------------------------------------------ images of sources (OCR + Beschreibung)

    /** Ablage der Bildauswertungen im aktuellen Projekt, je Bild und Version eine Datei. */
    public com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysisCache imageCache() {
        return new com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysisCache() {
            @Override
            public com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis get(String cacheKey) {
                List<String> lines = readLines(imageFile("cache", cacheKey));
                return lines.size() < 3 ? null : parseAnalysis(lines);
            }

            @Override
            public void put(String cacheKey, com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis analysis) {
                writeText(imageFile("cache", cacheKey), renderAnalysis(analysis));
            }
        };
    }

    /** Speichert die Bildauswertungen einer Quelle (OCR und Beschreibung getrennt) für {@code source_images}. */
    public void saveImageAnalyses(String sourceId,
                                  List<com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis> analyses) {
        StringBuilder text = new StringBuilder();
        for (com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis analysis : analyses) {
            text.append(renderAnalysis(analysis)).append("\n");
        }
        writeText(imageFile("source", sourceId), text.toString());
        LOG.info("[research-images] " + analyses.size() + " Bilder ausgewertet für " + sourceId);
    }

    /** Die Bildauswertungen einer Quelle als Text; leer, solange keine vorliegen. */
    public String imageAnalysesOf(String sourceId) {
        List<String> lines = readLines(imageFile("source", sourceId));
        StringBuilder text = new StringBuilder();
        for (String line : lines) {
            text.append(line).append('\n');
        }
        return text.toString().trim();
    }

    // ------------------------------------------------------------------ passages of sources (aus dem Index)

    /** Die Passagen einer Quelle aus dem Wissensindex (mit Passage-ID für Belege); leer, solange keine vorliegen. */
    public String passagesOf(String sourceId) {
        Function<String, String> reader = passageReader;
        return reader == null ? "" : reader.apply(sourceId);
    }

    /** Liest die Passagen einer Ressource aus dem Wissensindex; das Projekt hält keine Kopie. */
    public void setPassageReader(Function<String, String> reader) {
        this.passageReader = reader;
    }

    /** Liefert zu einer Ressourcen-ID {@code Bookmark, Revision, Inhalts-Hash} aus dem Index. */
    public void setResourceReferences(Function<String, String[]> references) {
        this.resourceReferences = references;
    }

    private synchronized File imageFile(String kind, String key) {
        ensureOpen();
        File directory = new File(new File(projectDirectory, IMAGES_DIRECTORY), kind);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            LOG.warning("Bildordner nicht anlegbar: " + directory);
        }
        return new File(directory, sha256(key) + ".txt");
    }

    private static String renderAnalysis(com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis analysis) {
        return "image: " + clean(analysis.imageId()) + "\n" + "title: " + clean(analysis.title()) + "\n"
                + "version: " + clean(analysis.version()) + "\n" + "--- OCR ---\n" + analysis.ocrText().trim()
                + "\n--- Beschreibung ---\n" + analysis.description().trim() + "\n";
    }

    private static com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis parseAnalysis(List<String> lines) {
        String id = valueOf(lines.get(0), "image: ");
        String title = valueOf(lines.get(1), "title: ");
        String version = valueOf(lines.get(2), "version: ");
        StringBuilder ocr = new StringBuilder();
        StringBuilder description = new StringBuilder();
        StringBuilder current = null;
        for (int i = 3; i < lines.size(); i++) {
            String line = lines.get(i);
            if ("--- OCR ---".equals(line)) {
                current = ocr;
            } else if ("--- Beschreibung ---".equals(line)) {
                current = description;
            } else if (current != null) {
                current.append(line).append('\n');
            }
        }
        return new com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis(id, title, version,
                ocr.toString().trim(), description.toString().trim());
    }

    private static String valueOf(String line, String prefix) {
        return line.startsWith(prefix) ? line.substring(prefix.length()) : "";
    }

    private static String sha256(String key) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format("%02x", digest[i] & 0xff));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            return Integer.toHexString(key.hashCode());
        }
    }

    private static void writeText(File file, String text) {
        try {
            java.nio.file.Files.write(file.toPath(), text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException notWritten) {
            LOG.warning("Nicht gespeichert: " + file.getName() + ": " + notWritten.getMessage());
        }
    }

    /** Merkt eine nicht gelesene oder nicht passende Seite vor (askai {@code source_park}). */
    public synchronized void parkSource(String url, String title, String excerpt, String score) {
        ensureOpen();
        appendLine(new File(projectDirectory, PARKED_FILE),
                clean(url) + "\t" + clean(title) + "\t" + clean(score) + "\t" + clean(excerpt));
    }

    /** Die übernommenen Quellen des Projekts als {@code id, Titel, URL}. */
    public synchronized List<String[]> acceptedSources() {
        if (projectDirectory == null && existingProject() == null) {
            return Collections.emptyList();
        }
        ensureOpen();
        List<String[]> sources = new ArrayList<String[]>();
        for (String line : readLines(new File(projectDirectory, SOURCES_FILE))) {
            String[] parts = line.split("\t", -1);
            sources.add(new String[]{parts[0], parts.length > 1 ? parts[1] : "", parts.length > 2 ? parts[2] : ""});
        }
        return sources;
    }

    /** Die übernommenen Quellen mit allen Feldern aus {@code sources.tsv} (Reiter „Quellen“). */
    public synchronized List<ResearchSourceRef> sourceRefs() {
        if (projectDirectory == null && existingProject() == null) {
            return Collections.emptyList();
        }
        ensureOpen();
        List<ResearchSourceRef> sources = new ArrayList<ResearchSourceRef>();
        for (String line : readLines(new File(projectDirectory, SOURCES_FILE))) {
            if (!line.trim().isEmpty()) {
                sources.add(ResearchSourceRef.parse(line));
            }
        }
        return sources;
    }

    /** Wie viele Treffer vorgemerkt, aber nicht übernommen wurden ({@code parked.tsv}). */
    public synchronized int parkedCount() {
        if (projectDirectory == null && existingProject() == null) {
            return 0;
        }
        ensureOpen();
        return readLines(new File(projectDirectory, PARKED_FILE)).size();
    }

    // ------------------------------------------------------------------ run progress (Phase Quellen)

    /** Der Stand des Recherche-Laufs; nach einem Neustart der zuletzt gespeicherte (nie „aktiv“). */
    public ResearchRunStatus runStatus() {
        synchronized (this) {
            if (projectDirectory == null && existingProject() != null) {
                ensureOpen();
            }
        }
        return runStatus;
    }

    /**
     * Der Agentenprozess meldet eine Tätigkeit des Laufs ({@code research_progress}): Suche, gelesene Seite,
     * übernommene Quelle, Zähler. Die Oberfläche hört über den Zustandshörer mit.
     */
    public void reportProgress(String activity, String query, String title, String url, int pages, int accepted) {
        synchronized (this) {
            ensureOpen();
            runStatus = runStatus.next(activity, query, title, url, pages, accepted);
            writeRunStatus(projectDirectory, runStatus);
        }
        fireStateChangedLater();
    }

    /**
     * Ende des Laufs ({@code research_run_finished}): der Host vermerkt Grund und Zähler und geht in den
     * Freigabe-Übergang der Phase Quellen (RESEARCH/RUNNING → REQUEST_EVIDENCE_REVIEW → WAITING_APPROVAL).
     *
     * @return die Zusammenfassung der gefundenen Quellen für den Chat
     */
    public String reportRunFinished(String stopReason, int pages, int accepted) {
        synchronized (this) {
            ensureOpen();
            runStatus = runStatus.finished(stopReason, pages, accepted);
            writeRunStatus(projectDirectory, runStatus);
        }
        if (isWritable(ResearchStateIds.RESEARCH)) {
            dispatch(ResearchCommandType.REQUEST_EVIDENCE_REVIEW);
        } else {
            fireStateChangedLater();
        }
        return evidenceSummary();
    }

    /** Die verständliche Zusammenfassung der Phase Quellen (Punkt 8 der Anforderung). */
    public String evidenceSummary() {
        List<ResearchSourceRef> sources = sourceRefs();
        ResearchRunStatus run = runStatus();
        StringBuilder text = new StringBuilder();
        String why = ResearchRunStatus.reasonText(run.stopReason());
        text.append("Die Quellensuche ist abgeschlossen").append(why.isEmpty() ? "" : " (" + why + ")").append(": ")
                .append(run.pagesVisited()).append(run.pagesVisited() == 1 ? " Seite" : " Seiten")
                .append(" gelesen, ").append(sources.size()).append(sources.size() == 1 ? " Quelle" : " Quellen")
                .append(" im Projekt.");
        if (sources.isEmpty()) {
            text.append("\n\nEs wurde noch keine passende Quelle übernommen. Mit „Weiter recherchieren“ sucht der "
                    + "Agent erneut; schärfe dazu gern vorher das Konzept oder den Weidezaun.");
            return text.toString();
        }
        text.append("\n");
        for (ResearchSourceRef source : sources) {
            text.append("\n- ").append(source.title());
        }
        text.append("\n\nDie Seiten stehen im Reiter „Quellen“. Mit „Quellen freigeben“ erstelle ich daraus die "
                + "Gliederung, mit „Weiter recherchieren“ suche ich weitere Seiten.");
        return text.toString();
    }

    private static ResearchRunStatus readRunStatus(File directory) {
        List<String> lines = readLines(new File(directory, RUN_FILE));
        if (lines.size() < 8) {
            return ResearchRunStatus.idle();
        }
        try {
            // Nach einem Neustart läuft nichts mehr: ein unterbrochener Lauf erscheint als beendet ohne Grund.
            return new ResearchRunStatus(false, ResearchRunStatus.FINISHED, lines.get(1), lines.get(2),
                    lines.get(3), Integer.parseInt(lines.get(4).trim()), Integer.parseInt(lines.get(5).trim()),
                    lines.get(6), lines.get(7));
        } catch (NumberFormatException unreadable) {
            return ResearchRunStatus.idle();
        }
    }

    private static void writeRunStatus(File directory, ResearchRunStatus run) {
        writeText(new File(directory, RUN_FILE), run.activity() + "\n" + run.query() + "\n" + run.pageTitle()
                + "\n" + run.url() + "\n" + run.pagesVisited() + "\n" + run.acceptedSources() + "\n"
                + run.stopReason() + "\n" + run.lastAccepted() + "\n");
    }

    // ------------------------------------------------------------------ Gliederung (Phase Outline)

    /** Die Gliederung des Projekts ({@code outline.md}); leer, solange keine erstellt ist. */
    public synchronized String outline() {
        if (projectDirectory == null && existingProject() == null) {
            return "";
        }
        ensureOpen();
        return readText(new File(projectDirectory, OUTLINE_FILE));
    }

    public boolean hasOutline() {
        return !outline().trim().isEmpty();
    }

    /**
     * Der Agent legt die Gliederung vor (nur in Phase Gliederung/„running“): gespeichert, danach
     * PROPOSE_OUTLINE → Freigabe.
     *
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String proposeOutline(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "die Gliederung ist leer";
        }
        if (!isWritable(ResearchStateIds.OUTLINE)) {
            return "Not allowed in the current state (" + phaseId() + "/" + stateId() + ").";
        }
        synchronized (this) {
            writeText(new File(projectDirectory, OUTLINE_FILE), text.trim() + "\n");
        }
        LOG.info("[research-outline] proposed");
        return dispatch(ResearchCommandType.PROPOSE_OUTLINE);
    }

    /**
     * Der Nutzer korrigiert die Gliederung im Reiter (solange die Phase Gliederung läuft oder auf Freigabe
     * wartet); kein Übergang.
     *
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String saveOutline(String text) {
        synchronized (this) {
            ensureOpen();
            if (!ResearchStateIds.OUTLINE.equals(state.getPhaseId())) {
                return "Die Gliederung ist nur in der Phase Gliederung änderbar.";
            }
            writeText(new File(projectDirectory, OUTLINE_FILE), (text == null ? "" : text.trim()) + "\n");
        }
        LOG.info("[research-outline] edited by user");
        fireStateChanged();
        return null;
    }

    // ------------------------------------------------------------------ Dokument (Phase Document)

    /** Das Research-Dokument; {@code null}, solange keins geschrieben ist oder es unlesbar ist. */
    public ResearchDocument document() {
        String json;
        synchronized (this) {
            if (projectDirectory == null && existingProject() == null) {
                return null;
            }
            ensureOpen();
            json = readText(new File(projectDirectory, DOCUMENT_FILE));
        }
        if (json.trim().isEmpty()) {
            return null;
        }
        try {
            return ResearchDocument.fromJson(json);
        } catch (RuntimeException unreadable) {
            LOG.warning("Research-Dokument nicht lesbar: " + unreadable.getMessage());
            return null;
        }
    }

    public boolean hasDocument() {
        return document() != null;
    }

    /**
     * Der Agent liefert das Dokument (nur in Phase Dokument/„running“): Belege werden auf die übernommenen Quellen
     * nummeriert und als Aussagen gespeichert, danach REQUEST_DRAFT_REVIEW.
     *
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String saveDocument(String title, List<ResearchDocument.Section> sections,
                               java.util.Map<String, String> resourceByPassage) {
        if (sections == null || sections.isEmpty()) {
            return "das Dokument ist leer";
        }
        if (!isWritable(ResearchStateIds.DRAFT)) {
            return "Not allowed in the current state (" + phaseId() + "/" + stateId() + ").";
        }
        ResearchDocument document = ResearchDocument.fromDraft(title, outline(), sections, resourceByPassage,
                sourceRefs());
        synchronized (this) {
            writeText(new File(projectDirectory, DOCUMENT_FILE), document.toJson());
        }
        LOG.info("[research-document] saved: " + document.sections().size() + " sections, "
                + document.sources().size() + " sources, " + document.claims().size() + " claims");
        return dispatch(ResearchCommandType.REQUEST_DRAFT_REVIEW);
    }

    // ------------------------------------------------------------------ Projektsuche (Nachfragen)

    /** Sucht in den Passagen der übernommenen Quellen (hybrid, aus dem Wissensindex); leer ohne Quellen. */
    public String projectSearch(String query, int limit) {
        CorpusSearch search = corpusSearch;
        List<String> ids = new ArrayList<String>();
        for (ResearchSourceRef source : sourceRefs()) {
            ids.add(source.resourceId());
        }
        if (search == null || ids.isEmpty() || query == null || query.trim().isEmpty()) {
            return "";
        }
        String result = search.search(ids, query.trim(), Math.max(1, limit));
        return result == null ? "" : result;
    }

    /** Die Suche über einen festen Korpus von Ressourcen (Composition Root, über den Wissensindex). */
    public interface CorpusSearch {
        /** Treffer als Text: je Passage {@code passage:}, {@code resource:}, {@code title:} und der Passagentext. */
        String search(List<String> resourceIds, String query, int limit);
    }

    public void setCorpusSearch(CorpusSearch search) {
        this.corpusSearch = search;
    }

    /**
     * Eine bei einer Nachfrage gefundene weitere Seite kommt ins Projekt (Phase Dokument); dieselbe Ablage wie
     * {@code source_accept}.
     */
    public String addFollowUpSource(String captureId, String title, String url) {
        // Konzeptphase: der direkte Suchauftrag notiert seine gelesenen Seiten; Dokument: Nachfragen.
        if (!ResearchStateIds.DRAFT.equals(phaseId()) && !ResearchStateIds.SCOPING.equals(phaseId())) {
            return "Not allowed in the current state (" + phaseId() + "/" + stateId() + ").";
        }
        return acceptSource(captureId, title, url);
    }

    private static String readText(File file) {
        if (!file.isFile()) {
            return "";
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            LOG.warning("Nicht lesbar: " + file.getName() + ": " + unreadable.getMessage());
            return "";
        }
    }

    // ------------------------------------------------------------------ Weidezaun als Filter (SC1)

    /** Der Weidezaun-Filter des autonomen Laufs ({@code search_scope_begin/evaluate/end}). */
    ResearchScopeControl scopeControl() {
        return scopeControl;
    }

    /** Embedding für den Weidezaun-Filter; ohne Embedding-Modell begrenzen nur IN-freie Läufe nicht. */
    public void setScopeEmbedder(ResearchScopeControl.Embedder embedder) {
        scopeControl.setEmbedder(embedder);
    }

    /** Die Suchen des nächsten Laufs aus Konzept, Auftrag und Ausschlüssen ({@link ResearchSearchPlan}). */
    public List<String> searchPlan() {
        List<String> excluded = new ArrayList<String>(exclusionsProjection());
        excluded.addAll(blacklistTermsForDisplay());
        return ResearchSearchPlan.queries(conceptDocumentJson(), currentScopeDraft().getMission(), excluded);
    }

    /** Das Konzeptpapier des aktuellen Projekts als JSON (leer, wenn es noch keins gibt). */
    String conceptDocumentJson() {
        ConceptBranchService service = existingConceptService();
        if (service == null) {
            return null;
        }
        try {
            return service.snapshot().getDocumentJson();
        } catch (RuntimeException unreadable) {
            LOG.warning("Konzept nicht lesbar: " + unreadable.getMessage());
            return null;
        }
    }

    private void fireStateChangedLater() {
        // Nie unter dem Monitor des Hosts benachrichtigen: Hörer lesen den Host wieder.
        new Thread(new Runnable() {
            @Override
            public void run() {
                fireStateChanged();
            }
        }, "research-state-changed").start();
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').trim();
    }

    private static List<String> readLines(File file) {
        if (!file.isFile()) {
            return Collections.emptyList();
        }
        try {
            return java.nio.file.Files.readAllLines(file.toPath(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException unreadable) {
            LOG.warning("Nicht lesbar: " + file.getName() + ": " + unreadable.getMessage());
            return Collections.emptyList();
        }
    }

    private static void appendLine(File file, String line) {
        try {
            java.nio.file.Files.write(file.toPath(), (line + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException notWritten) {
            LOG.warning("Nicht gespeichert: " + file.getName() + ": " + notWritten.getMessage());
        }
    }

    private ResearchStateMemento readState(File directory) {
        File file = new File(directory, STATE_FILE);
        if (file.isFile()) {
            try {
                List<String> lines = java.nio.file.Files.readAllLines(file.toPath(),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (lines.size() >= 4) {
                    return new ResearchStateMemento(lines.get(0).trim(), lines.get(1).trim(),
                            emptyToNull(lines.get(2).trim()), Long.parseLong(lines.get(3).trim()),
                            lines.size() > 4 ? emptyToNull(lines.get(4).trim()) : null);
                }
            } catch (IOException | RuntimeException unreadable) {
                LOG.warning("Research-Zustand nicht lesbar, beginne beim Konzept: " + unreadable.getMessage());
            }
        }
        ResearchStateMemento initial = machine.initialMemento();
        if (new File(directory, GREETED_MARKER).isFile()) {
            // Projekte von vor der Zustandsmaschine: begrüßt heißt Konzept läuft
            ResearchStateTransitionResult started = machine.dispatch(initial,
                    ResearchCommand.of(ResearchCommandType.START, "migrated"));
            return started.isAccepted() ? started.getNextMemento() : initial;
        }
        return initial;
    }

    private static void writeState(File directory, ResearchStateMemento memento) {
        String text = memento.getPhaseId() + "\n" + memento.getStateId() + "\n"
                + nullToEmpty(memento.getContinuationStateId()) + "\n" + memento.getRevision() + "\n"
                + nullToEmpty(memento.getPendingApprovalId()) + "\n";
        try {
            java.nio.file.Files.write(new File(directory, STATE_FILE).toPath(),
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException notWritten) {
            LOG.warning("Research-Zustand nicht gespeichert: " + notWritten.getMessage());
        }
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    // ------------------------------------------------------------------ listeners

    public void addStateListener(Runnable listener) {
        stateListeners.add(listener);
    }

    public void removeStateListener(Runnable listener) {
        stateListeners.remove(listener);
    }

    /** Wo sichtbare Probleme landen (askai {@code sink.showProblem}); im Agent-Modus eine Hinweiszeile. */
    public void setProblemSink(Consumer<String> sink) {
        this.problemSink = sink;
    }

    private void fireStateChanged() {
        for (Runnable listener : stateListeners) {
            listener.run();
        }
    }

    // ------------------------------------------------------------------ scope (Weidezaun)

    /** Nur lesen: öffnet ein vorhandenes Projekt, legt aber keins an ({@code null}, solange es keins gibt). */
    private synchronized ResearchScopeCoordinator existingCoordinator() {
        if (coordinator == null && existingProject() != null) {
            ensureOpen();
        }
        return coordinator;
    }

    private synchronized ResearchScopeCoordinator scopeCoordinator() {
        ensureOpen();
        return coordinator;
    }

    /** Das Konzeptpapier des aktuellen Projekts (öffnet das Projekt bei Bedarf). */
    synchronized ResearchConceptHost conceptHost() {
        ensureOpen();
        return conceptHost;
    }

    /** Das Konzeptpapier, nur wenn schon ein Projekt existiert (für die Anzeige; legt nichts an). */
    public ConceptBranchService existingConceptService() {
        synchronized (this) {
            if (conceptHost == null && existingProject() != null) {
                ensureOpen();
            }
            return conceptHost == null ? null : conceptHost.service();
        }
    }

    /** askai {@code blacklistTermsForDisplay}: was der Konzeptbaum als ausgeschlossen markiert. */
    public List<String> blacklistTermsForDisplay() {
        ResearchConceptHost concept;
        synchronized (this) {
            if (conceptHost == null && existingProject() != null) {
                ensureOpen();
            }
            concept = conceptHost;
        }
        return concept == null ? Collections.<String>emptyList() : concept.currentBlacklistTerms();
    }

    /** One technical-log line for a tree-editor gesture (askai {@code logConceptUiAction}). */
    public void logConceptUiAction(String line) {
        LOG.info("[research-concept] concept USER_UI -> " + line);
    }

    /** The current persisted scope draft — the blacklist UI reads this. */
    public ResearchScopeDraft currentScopeDraft() {
        ResearchScopeCoordinator current = existingCoordinator();
        return current == null ? ResearchScopeDraft.empty() : current.current();
    }

    /**
     * Die Zaun-Ansicht, die das Modell vor jedem Turn als maßgeblichen Umfang sieht (askai
     * {@code publishScopeFence}); leer, solange der Entwurf unbrauchbar ist.
     */
    public String renderedFence() {
        ResearchScopeCoordinator current = existingCoordinator();
        if (current == null || !current.isUsable()) {
            return "";
        }
        ResearchConceptHost concept;
        synchronized (this) {
            concept = conceptHost;
        }
        return ResearchScopeFenceView.render(current.current()) + (concept == null ? "" : concept.fenceBlocks());
    }

    /** Exclusion strings + EXCLUDED facet labels, deduplicated, draft order (askai {@code ResearchPhaseAccessory}). */
    public List<String> exclusionsProjection() {
        ResearchScopeDraft draft = currentScopeDraft();
        LinkedHashSet<String> union = new LinkedHashSet<String>(draft.getExclusions());
        for (ScopeFacet facet : draft.excludedFacets()) {
            if (!facet.getLabel().trim().isEmpty()) {
                union.add(facet.getLabel().trim());
            }
        }
        return new ArrayList<String>(union);
    }

    /**
     * Record a user-worded exclusion ("+ Hinzufügen" on the blacklist) through the ONE scope path —
     * the same coordinator/patch/persist route a scoping turn takes, never a parallel store.
     *
     * @return {@code null} when recorded (or already present), else the honest rejection reason
     */
    public String addScopeExclusion(String text) {
        return applyExclusionPatch(text, true);
    }

    /**
     * Take a plain exclusion back out (the chip's ✕). Only the user-worded string list — EXCLUDED
     * facets stay on record, exactly like {@code ScopePatchOperations.removeExclusion} documents.
     *
     * @return {@code null} when removed (or already absent), else the honest rejection reason
     */
    public String removeScopeExclusion(String text) {
        return applyExclusionPatch(text, false);
    }

    private String applyExclusionPatch(String text, boolean add) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) {
            return "der Ausschluss ist leer";
        }
        ResearchScopeCoordinator coordinator = scopeCoordinator();
        if (coordinator == null) {
            return "kein Projektkontext — der Rechercheumfang wird nicht persistiert";
        }
        if (!add) {
            // The chip may project an EXCLUDED facet (not a string exclusion). Removing it means
            // the user LIFTS the exclusion. Two-artifact alignment: the facet returns as
            // PROVISIONAL bookkeeping (kept on record, no longer an OUT post) — NEVER as a
            // CONFIRMED positive post; the mindmap alone owns the positive space, and a lifted
            // exclusion must not spawn a negotiated IN anchor beside it.
            for (ScopeFacet facet : coordinator.current().excludedFacets()) {
                if (facet.getLabel().trim().equalsIgnoreCase(value)) {
                    ScopeUpdateResult lifted = coordinator.apply(
                            new ScopePatch(Collections.singletonList(ScopePatchOperations.addFacet(
                                    facet.getFacetId(), facet.getLabel(), "user lifted the exclusion"))),
                            Collections.<UnresolvedScopeIssue>emptyList());
                    if (lifted.getStatus() == ScopeUpdateResult.Status.REJECTED) {
                        return lifted.getReason();
                    }
                    if (lifted.isApplied()) {
                        fireStateChanged();
                    }
                    return null;
                }
            }
        }
        // A no-op must not burn a revision: adding an existing / removing an absent value returns quietly.
        boolean present = coordinator.current().getExclusions().contains(value);
        if (add == present) {
            return null;
        }
        ScopePatch patch = new ScopePatch(Collections.singletonList(add
                ? ScopePatchOperations.addExclusion(value)
                : ScopePatchOperations.removeExclusion(value)));
        ScopeUpdateResult result = coordinator.apply(patch, Collections.<UnresolvedScopeIssue>emptyList());
        if (result.getStatus() == ScopeUpdateResult.Status.REJECTED) {
            return result.getReason();
        }
        if (result.isApplied()) {
            fireStateChanged(); // the chips re-render through the ordinary state listener
        }
        return null;
    }

    /**
     * Apply what a scoping turn proposed. A failure here must NEVER look like success: if the assistant
     * says "ich habe AR als Nebenaspekt notiert" while the update was refused, the conversation and the
     * stored scope would silently diverge — so a rejection becomes a visible problem.
     */
    public void applyScopeUpdate(String documentJson) {
        ResearchScopeCoordinator coordinator = scopeCoordinator();
        if (coordinator == null) {
            return;
        }
        ScopeUpdateWireCodec.Result decoded = ScopeUpdateWireCodec.decode(documentJson);
        if (!decoded.isOk()) {
            LOG.info("scope update -> REJECTED: " + decoded.getError());
            reportScopeProblem("Der Rechercheumfang konnte nicht aktualisiert werden: " + decoded.getError());
            return;
        }
        ScopeUpdateResult applied = coordinator.apply(decoded.getTurn());
        if (applied.getStatus() == ScopeUpdateResult.Status.REJECTED) {
            LOG.info("scope update -> REJECTED: " + applied.getReason());
            reportScopeProblem("Der Rechercheumfang konnte nicht gespeichert werden: " + applied.getReason());
            return;
        }
        for (String change : applied.getChanges()) {
            LOG.info("[research-scope] " + change);
        }
        LOG.info(applied.isApplied()
                ? "scope update -> APPLIED: " + applied.getChanges()
                : "scope update -> NO-OP (nothing changed)");
        if (applied.isApplied()) {
            // Scope surfaces (the blacklist sky) refresh through the ordinary state listener —
            // a model-recorded exclusion must appear without a restart.
            fireStateChanged();
        }
    }

    /** The runtime rejected its own scope proposal: the answer stays valid, the scope did not change. */
    public void scopeUpdateRejected(String violations) {
        LOG.info("scope update -> REJECTED (runtime validation): " + violations);
        reportScopeProblem("Der Rechercheumfang wurde NICHT aktualisiert (fehlerhafter Vorschlag: "
                + violations + "). Die Antwort selbst bleibt gültig.");
    }

    private void reportScopeProblem(String message) {
        LOG.info("[research-scope] " + message);
        Consumer<String> sink = problemSink;
        if (sink != null) {
            sink.accept(message);
        }
    }
}

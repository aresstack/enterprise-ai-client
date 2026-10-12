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
    /** askai arch: die Phasenzuordnung der Chatnachrichten, neben dem Projekt (kein zweiter Verlauf). */
    private static final String JOURNAL_FILE = "phase-journal.json";

    private final File projectsRoot;
    private final ResearchProjects projects;
    /** Der Chat, dem das Projekt gehört; {@code null}, bis {@link #openChat} oder der erste Zugriff ihn setzt. */
    private String chatId;
    /** Welche Nachricht des Chats zu welcher Phase gehört (askai {@code ResearchPhaseJournal}). */
    private ResearchPhaseJournal journal = new ResearchPhaseJournal();
    /** Die gespeicherten Nachrichten je Chat (Host-Wahrheit für {@code chat_history}); {@code null} ohne Ablage. */
    private volatile ChatSessionHistoryReader chatHistoryReader;
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
            journal = readJournal(existingProject());
        }
        fireStateChanged();
    }

    /**
     * Der Projektordner eines gespeicherten Chats, nur zum Lesen (Chat-Export): bindet den Host nicht um, legt nichts
     * an und gilt auch für Chats, die gerade nicht offen sind; {@code null}, wenn der Chat kein Projekt hat.
     */
    public File existingProjectDirectoryOf(String chatId) {
        if (chatId == null || chatId.trim().isEmpty()) {
            return null;
        }
        return projects.exists(chatId) ? projects.directoryFor(chatId) : null;
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
        journal = new ResearchPhaseJournal();
    }

    // ------------------------------------------------------------------ chat_history (askai arch)

    /** Die Ablage der gespeicherten Chats (Composition Root); ohne sie meldet {@code chat_history} das ehrlich. */
    public void setChatHistoryReader(ChatSessionHistoryReader reader) {
        this.chatHistoryReader = reader;
    }

    /**
     * askai {@code attributeToCurrentPhase}: ordnet eine Nachricht des Chats der laufenden Phase zu. Legt kein
     * Projekt an; ohne Projekt wird die Zuordnung mit dem Anlegen gespeichert.
     */
    public void attributeMessage(String messageId) {
        synchronized (this) {
            if (journal.attribute(messageId, peekState().getPhaseId())) {
                File directory = existingProject();
                if (directory != null) {
                    writeText(new File(directory, JOURNAL_FILE), journal.toJson());
                }
            }
        }
    }

    /**
     * askai {@code describeChatHistory}: der GESPEICHERTE Verlauf des Chats, an den das Projekt gebunden ist (eine
     * Wahrheit, dieselbe, die der Nutzer sieht, auch von vor dem Start dieses Prozesses), nach Phasen gegliedert.
     *
     * @param raw {@code true} = jede Nachricht jeder Phase, sonst abgeschlossene Phasen als eine Zusammenfassung
     */
    public String describeChatHistory(boolean raw) {
        ChatSessionHistoryReader reader = chatHistoryReader;
        String chat;
        ResearchPhaseJournal attribution;
        String phase;
        synchronized (this) {
            chat = chatId;
            attribution = journal;
            phase = peekState().getPhaseId();
        }
        if (reader == null || chat == null) {
            return "(this host does not expose the persisted chat history to plugins)";
        }
        return ResearchChatHistoryProjection.render(reader.readMessages(chat), attribution, phase, raw);
    }

    private static ResearchPhaseJournal readJournal(File directory) {
        return directory == null ? new ResearchPhaseJournal()
                : ResearchPhaseJournal.fromJson(readText(new File(directory, JOURNAL_FILE)));
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
        if (!journal.isEmpty()) {
            writeText(new File(directory, JOURNAL_FILE), journal.toJson()); // vor dem Anlegen Zugeordnetes
        }
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
        if (rejection == null && command == ResearchCommandType.APPROVE_OUTLINE) {
            recordOutcome(ResearchStateIds.OUTLINE, outlineOutcome());
        }
        if (rejection == null) {
            fireStateChanged();
        }
        return rejection;
    }

    /**
     * Der Nutzer wählt die Phase (Dropdown im Composer). Nur er wechselt sie; jede Reihenfolge ist erlaubt
     * (Konzept → Quellen → Konzept → Dokument …), ohne Freigabeschritte. Die gewählte Phase läuft sofort
     * ({@code running}), damit ihr Werkzeugsatz gilt; bleibt sie dieselbe, ändert sich nichts. Ein Wechsel geht an
     * Werkzeugsatz und Ansicht.
     *
     * @return {@code null} bei Erfolg, sonst der Grund (unbekannte Phase)
     */
    public String selectPhase(String phaseId) {
        if (!ResearchWorkflow.phaseOrder().contains(phaseId)) {
            return "Unbekannte Phase: " + phaseId;
        }
        boolean leftOutline;
        synchronized (this) {
            ensureOpen();
            if (phaseId.equals(state.getPhaseId())) {
                return null;
            }
            leftOutline = ResearchStateIds.OUTLINE.equals(state.getPhaseId());
            setRunning(phaseId);
            LOG.info("[research-state] Phase vom Nutzer gewählt -> " + phaseId);
        }
        // Wer die Gliederung verlässt, nimmt sie mit: ihre Kapitel sind der Kontext der späteren Phasen.
        if (leftOutline && !outline().trim().isEmpty()) {
            recordOutcome(ResearchStateIds.OUTLINE, outlineOutcome());
        }
        fireStateChanged();
        return null;
    }

    /**
     * Vor der Play-Aktion einer Phase: die Phase bleibt, wie der Nutzer sie gewählt hat; nur ihr Laufzustand wird
     * wieder {@code running} (etwa nach einer vorgelegten Gliederung), damit die Werkzeuge der Phase schreiben dürfen.
     * Eine andere Phase lehnt die Aktion ab, ohne zu wechseln.
     *
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String preparePhaseAction(String phaseId) {
        boolean changed;
        synchronized (this) {
            ensureOpen();
            if (!phaseId.equals(state.getPhaseId())) {
                return "Phase „" + ResearchWorkflow.titleOf(phaseId) + "“ auswählen.";
            }
            changed = !ResearchStateIds.RUNNING.equals(state.getStateId());
            if (changed) {
                setRunning(phaseId);
            }
        }
        if (changed) {
            fireStateChanged();
        }
        return null;
    }

    /** Schreibt den Zustand {@code phaseId/running}; nur unter dem Monitor des Hosts. */
    private void setRunning(String phaseId) {
        if (ResearchStateIds.SCOPING.equals(phaseId) && !new File(projectDirectory, GREETED_MARKER).isFile()) {
            try {
                java.nio.file.Files.write(new File(projectDirectory, GREETED_MARKER).toPath(), new byte[0]);
            } catch (IOException notWritten) {
                LOG.warning("Beginn des Konzepts nicht vermerkt: " + notWritten.getMessage());
            }
        }
        state = new ResearchStateMemento(phaseId, ResearchStateIds.RUNNING, null, state.getRevision() + 1, null);
        writeState(projectDirectory, state);
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
     * Ende des Laufs ({@code research_run_finished}): der Host vermerkt Grund und Zähler. Nur ein regulär beendeter
     * Lauf geht in den Freigabe-Übergang der Phase Quellen (RESEARCH/RUNNING → REQUEST_EVIDENCE_REVIEW →
     * WAITING_APPROVAL); ein Abbruch durch den Nutzer pausiert die Phase (PAUSE, fortsetzbar), ein Fehler (Wissenssuche
     * nicht erreichbar) lässt den Zustand stehen, damit die Play-Aktion im Reiter „Quellen“ neu startet (askai arch
     * {@code ResearchAgentSession}: Stoppgrund entscheidet über den Übergang).
     *
     * @return die Zusammenfassung der gefundenen Quellen für den Chat
     */
    public String reportRunFinished(String stopReason, int pages, int accepted) {
        synchronized (this) {
            ensureOpen();
            runStatus = runStatus.finished(stopReason, pages, accepted);
            writeRunStatus(projectDirectory, runStatus);
        }
        boolean running = isWritable(ResearchStateIds.RESEARCH);
        if (running && USER_CANCELLED.equals(stopReason)) {
            dispatch(ResearchCommandType.PAUSE);
            return recordOutcome(ResearchStateIds.RESEARCH, "Die Quellensuche ist angehalten (" + pages
                    + (pages == 1 ? " Seite" : " Seiten") + " gelesen, " + sourceRefs().size()
                    + " Quellen im Projekt). Mit „Recherche starten“ im Reiter „Quellen“ geht sie weiter; Fragen beantworte ich "
                    + "auch so.");
        }
        if (running && FAILED_STOPS.contains(stopReason)) {
            fireStateChangedLater();
            return recordOutcome(ResearchStateIds.RESEARCH, "Die Quellensuche ist fehlgeschlagen ("
                    + ResearchRunStatus.reasonText(stopReason) + "). Die bisher übernommenen Quellen bleiben im "
                    + "Projekt; mit „Recherche starten“ im Reiter „Quellen“ starte ich neu.");
        }
        if (running) {
            dispatch(ResearchCommandType.REQUEST_EVIDENCE_REVIEW);
        } else {
            fireStateChangedLater();
        }
        // askai applyRunOutcome: die Erzählung zum Laufende IST die Zusammenfassung der Phase für chat_history.
        return recordOutcome(ResearchStateIds.RESEARCH, evidenceSummary());
    }

    /**
     * askai {@code journal.recordOutcome}: die EINE Ergebnis-Zusammenfassung einer Phase (die letzte gilt), die
     * {@code chat_history} für abgeschlossene Phasen statt der Einzelnachrichten zeigt.
     *
     * @return {@code summary}, damit der Aufrufer denselben Text in den Chat gibt
     */
    private String recordOutcome(String phaseId, String summary) {
        synchronized (this) {
            if (journal.recordOutcome(phaseId, summary)) {
                File directory = existingProject();
                if (directory != null) {
                    writeText(new File(directory, JOURNAL_FILE), journal.toJson());
                }
            }
        }
        return summary;
    }

    /** Die Zusammenfassung der freigegebenen Gliederung: Zahl und Titel der Kapitel. */
    private String outlineOutcome() {
        List<String> chapters = new ArrayList<String>();
        for (String line : outline().split("\n")) {
            if (line.startsWith("## ")) {
                chapters.add(line.substring(3).trim());
            }
        }
        StringBuilder text = new StringBuilder("Gliederung freigegeben (").append(chapters.size())
                .append(" Kapitel)");
        for (int i = 0; i < chapters.size(); i++) {
            text.append(i == 0 ? ": " : "; ").append(chapters.get(i));
        }
        return text.append('.').toString();
    }

    private static final String USER_CANCELLED = "USER_CANCELLED";
    /** Stoppgründe, nach denen nichts zur Freigabe vorliegt, weil der Lauf nicht arbeiten konnte. */
    private static final java.util.Set<String> FAILED_STOPS = new java.util.HashSet<String>(java.util.Arrays.asList(
            "MCP_UNAVAILABLE", "SEARCH_TECHNICAL_PROBLEM", "ERROR_BUDGET_EXHAUSTED", "SCOPE_CONTROL_UNAVAILABLE"));

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
            text.append("\n\nEs wurde noch keine passende Quelle übernommen. Mit „Recherche starten“ im Reiter „Quellen“ sucht der "
                    + "Agent erneut; schärfe dazu gern vorher das Konzept oder den Weidezaun.");
            return text.toString();
        }
        text.append("\n");
        for (ResearchSourceRef source : sources) {
            text.append("\n- ").append(source.title());
        }
        text.append("\n\nDie Seiten stehen im Reiter „Quellen“. Für die Gliederung wähle die Phase „Gliederung“ und "
                + "starte sie im Reiter „Gliederung“; „Recherche starten“ sucht weitere Seiten.");
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
            clearDocumentDraft(); // das fertige Dokument ersetzt den Zwischenstand
        }
        LOG.info("[research-document] saved: " + document.sections().size() + " sections, "
                + document.sources().size() + " sources, " + document.claims().size() + " claims");
        recordOutcome(ResearchStateIds.DRAFT, "Dokument „" + document.title() + "“ gespeichert: "
                + document.sections().size() + " Kapitel, " + document.sources().size()
                + (document.sources().size() == 1 ? " Quelle, " : " Quellen, ") + document.claims().size()
                + (document.claims().size() == 1 ? " belegte Aussage." : " belegte Aussagen."));
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
     * Eine beim direkten Suchauftrag oder bei einer Nachfrage gelesene weitere Seite kommt ins Projekt (alle Phasen);
     * dieselbe Ablage wie {@code source_accept}.
     */
    public String addFollowUpSource(String captureId, String title, String url) {
        // In jeder Phase: der direkte Suchauftrag (Konzept, Quellen) und jede Nachfrage (Quellen, Gliederung,
        // Dokument) übernimmt die gelesenen Seiten ins Projekt, wenn der Projektkorpus nicht reicht.
        return acceptSource(captureId, title, url);
    }

    // ------------------------------------------------------------------ Start der autonomen Recherche (Phase 2)

    /** Begrüßungen und Füllwörter sind kein Recherche-Auftrag. */
    private static final java.util.Set<String> NO_BRIEF = new java.util.HashSet<String>(java.util.Arrays.asList(
            "hallo", "hi", "hey", "moin", "servus", "guten", "tag", "morgen", "abend", "danke", "ok", "okay", "ja",
            "nein", "test", "hello", "thanks", "recherche", "starten", "research"));

    /**
     * Was für die autonome Recherche fehlt: {@code null}, wenn ein sinnvoller Auftrag vorliegt (mindestens eine
     * Konzeptkarte oder ein erfasster Auftrag, der mehr als eine Begrüßung ist), sonst der Hinweis für den Nutzer.
     */
    public String researchBriefGap() {
        if (!com.aresstack.enterpriseai.research.concept.ConceptTopicScanner
                .collectCardPaths(conceptDocumentJson()).isEmpty()) {
            return null;
        }
        String mission = currentScopeDraft().getMission();
        for (String word : (mission == null ? "" : mission).toLowerCase(java.util.Locale.ROOT)
                .split("[^\\p{L}\\p{N}]+")) {
            if (word.length() >= 2 && !NO_BRIEF.contains(word)) {
                return null;
            }
        }
        return "Für die autonome Recherche fehlt noch ein Auftrag. Beschreibe im Chat, was recherchiert werden soll, "
                + "oder lege im Konzept mindestens eine Karte an. Einzelne Fragen beantworte ich dir auch ohne "
                + "Konzept direkt aus den Wissensquellen.";
    }

    /**
     * Bereitet die autonome Recherche (Play-Aktion der Phase Quellen) vor: nur in der vom Nutzer gewählten Phase
     * Quellen und nur mit sinnvollem Auftrag. Die Phase wechselt dabei nie; der Laufzustand wird wieder
     * {@code running} (auch nach einem angehaltenen oder beendeten Lauf).
     *
     * @return {@code null} bei Erfolg, sonst der Grund (für den Nutzer)
     */
    public String startAutonomousResearch() {
        if (!ResearchStateIds.RESEARCH.equals(phaseId())) {
            return "Phase „Quellen“ auswählen, um die Recherche zu starten.";
        }
        String gap = researchBriefGap();
        if (gap != null) {
            LOG.info("[research-state] Recherche nicht gestartet: kein Auftrag");
            return gap;
        }
        return preparePhaseAction(ResearchStateIds.RESEARCH);
    }

    // ------------------------------------------------------------------ gelbe Suchvorschläge (offer_searches)

    private static final String SEARCHED_FILE = "searched-queries.tsv";

    /** Ein gelber Suchvorschlag mit seinem Stand. */
    public static final class SearchTag {
        /** Angeboten, noch nicht gesucht. */
        public static final String OFFERED = "OFFERED";
        /** Die Suche läuft. */
        public static final String RUNNING = "RUNNING";
        /** Schon gesucht (Haken). */
        public static final String DONE = "DONE";
        /** Die Suche ist fehlgeschlagen (Retry). */
        public static final String FAILED = "FAILED";

        private final String query;
        private final String purpose;
        private final String status;

        public SearchTag(String query, String purpose, String status) {
            this.query = query == null ? "" : query;
            this.purpose = purpose == null ? "" : purpose;
            this.status = status == null ? OFFERED : status;
        }

        public String query() {
            return query;
        }

        public String purpose() {
            return purpose;
        }

        public String status() {
            return status;
        }
    }

    /**
     * Die gelben Suchvorschläge des Agenten ({@code offer_searches}) mit ihrem Stand, nur in der Phase Konzept
     * (askai arch {@code ScopingComposerAccessory}: Vorschläge gibt es nur im Scoping).
     */
    public List<SearchTag> searchTags() {
        ResearchConceptHost concept;
        synchronized (this) {
            if (conceptHost == null && existingProject() == null) {
                return Collections.emptyList();
            }
            ensureOpen();
            if (!ResearchStateIds.SCOPING.equals(state.getPhaseId())) {
                return Collections.emptyList();
            }
            concept = conceptHost;
        }
        java.util.Map<String, String> statuses = searchedQueries();
        List<SearchTag> tags = new ArrayList<SearchTag>();
        for (ResearchConceptHost.SearchOffer offer : concept.offeredSearches()) {
            String status = statuses.get(searchKey(offer.query));
            tags.add(new SearchTag(offer.query, offer.purpose, status == null ? SearchTag.OFFERED : status));
        }
        return tags;
    }

    /** Ein Klick auf einen gelben Vorschlag: die Suche läuft. */
    public void searchTagStarted(String query) {
        recordSearch(query, SearchTag.RUNNING);
    }

    /** Der Agent meldet das Ende der Suche: Haken („schon gesucht“) oder Retry. */
    public void searchTagFinished(String query, boolean succeeded) {
        recordSearch(query, succeeded ? SearchTag.DONE : SearchTag.FAILED);
    }

    private void recordSearch(String query, String status) {
        String key = searchKey(query);
        if (key.isEmpty()) {
            return;
        }
        synchronized (this) {
            ensureOpen();
            java.util.Map<String, String> statuses = searchedQueries();
            statuses.put(key, status);
            StringBuilder text = new StringBuilder();
            for (java.util.Map.Entry<String, String> entry : statuses.entrySet()) {
                text.append(entry.getKey()).append('\t').append(entry.getValue()).append('\n');
            }
            writeAtomically(new File(projectDirectory, SEARCHED_FILE), text.toString());
        }
        fireStateChangedLater();
    }

    private synchronized java.util.Map<String, String> searchedQueries() {
        java.util.Map<String, String> statuses = new java.util.LinkedHashMap<String, String>();
        if (projectDirectory == null) {
            return statuses;
        }
        for (String line : readLines(new File(projectDirectory, SEARCHED_FILE))) {
            String[] parts = line.split("\t", -1);
            if (parts.length >= 2 && !parts[0].isEmpty()) {
                statuses.put(parts[0], parts[1]);
            }
        }
        return statuses;
    }

    private static String searchKey(String query) {
        return clean(query).toLowerCase(java.util.Locale.ROOT);
    }

    // ------------------------------------------------------------------ gelesene Seiten aller Läufe

    private static final String VISITED_FILE = "visited-pages.txt";

    /**
     * Die in allen autonomen Läufen dieses Projekts gelesenen Seiten (kanonische Adressen, askai arch
     * {@code visitedAcrossRuns}, hier im Projekt statt im Agentenprozess): ein Folgelauf liest sie nicht erneut,
     * auch nach einem Neustart des Agenten oder des Clients.
     */
    public synchronized List<String> visitedPages() {
        if (projectDirectory == null && existingProject() == null) {
            return Collections.emptyList();
        }
        ensureOpen();
        List<String> pages = new ArrayList<String>();
        for (String line : readLines(new File(projectDirectory, VISITED_FILE))) {
            if (!line.trim().isEmpty()) {
                pages.add(line.trim());
            }
        }
        return pages;
    }

    /** Merkt gelesene Seiten eines Laufs vor; schon bekannte zählen einmal. */
    public synchronized void addVisitedPages(java.util.Collection<String> pages) {
        ensureOpen();
        LinkedHashSet<String> known = new LinkedHashSet<String>(visitedPages());
        for (String page : pages == null ? Collections.<String>emptyList() : pages) {
            String clean = clean(page);
            if (!clean.isEmpty() && known.add(clean)) {
                appendLine(new File(projectDirectory, VISITED_FILE), clean);
            }
        }
    }

    // ------------------------------------------------------------------ Dokument-Zwischenstand (je Kapitel)

    private static final String DOCUMENT_DRAFT_FILE = "document-draft.json";

    /**
     * Speichert ein fertig geschriebenes Kapitel des Dokuments sofort (nur in Phase Dokument/„running“), damit ein
     * Fehler bei einem späteren Kapitel die früheren nicht vernichtet. Der Zwischenstand gehört zur aktuellen
     * Gliederung; ändert sie sich, beginnt er neu.
     *
     * @param index      Kapitelnummer ab 0
     * @param passagesJson {@code {passage-id: resource-id}} der Belege des Kapitels
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String saveDocumentSection(int index, String heading, String text, String passagesJson) {
        if (!isWritable(ResearchStateIds.DRAFT)) {
            return "Not allowed in the current state (" + phaseId() + "/" + stateId() + ").";
        }
        if (index < 0) {
            return "invalid index";
        }
        String outline = outline();
        synchronized (this) {
            com.google.gson.JsonObject draft = readDocumentDraft(outline);
            com.google.gson.JsonArray sections = draft.getAsJsonArray("sections");
            if (index > sections.size()) {
                return "Kapitel " + (index + 1) + " folgt nicht auf den Zwischenstand (" + sections.size()
                        + " Kapitel gespeichert).";
            }
            com.google.gson.JsonObject section = new com.google.gson.JsonObject();
            section.addProperty("heading", heading == null ? "" : heading);
            section.addProperty("text", text == null ? "" : text);
            com.google.gson.JsonObject passages = new com.google.gson.JsonObject();
            try {
                if (passagesJson != null && !passagesJson.trim().isEmpty()) {
                    passages = com.google.gson.JsonParser.parseString(passagesJson).getAsJsonObject();
                }
            } catch (RuntimeException invalid) {
                return "invalid passages_json: " + invalid.getMessage();
            }
            section.add("passages", passages);
            com.google.gson.JsonArray kept = new com.google.gson.JsonArray();
            for (int i = 0; i < index; i++) {
                kept.add(sections.get(i));
            }
            kept.add(section);
            draft.add("sections", kept);
            writeAtomically(new File(projectDirectory, DOCUMENT_DRAFT_FILE), draft.toString());
        }
        LOG.info("[research-document] Kapitel " + (index + 1) + " gespeichert");
        return null;
    }

    /** Der Zwischenstand zur aktuellen Gliederung als JSON {@code {outline, sections:[{heading, text, passages}]}}. */
    public String documentDraftJson() {
        String outline = outline();
        synchronized (this) {
            return readDocumentDraft(outline).toString();
        }
    }

    /** Wie viele Kapitel des Dokuments schon gespeichert sind (zur aktuellen Gliederung). */
    public int documentDraftSectionCount() {
        String outline = outline();
        synchronized (this) {
            if (projectDirectory == null && existingProject() == null) {
                return 0; // Anzeigen legen kein Projekt an
            }
            return readDocumentDraft(outline).getAsJsonArray("sections").size();
        }
    }

    /**
     * Der Zwischenstand als (unvollständiges) Dokument für den Reiter „Dokument“: die gespeicherten Kapitel mit
     * nummerierten Belegen; {@code null} ohne Zwischenstand.
     */
    public ResearchDocument documentDraft() {
        String outline = outline();
        com.google.gson.JsonArray stored;
        synchronized (this) {
            if (projectDirectory == null && existingProject() == null) {
                return null;
            }
            stored = readDocumentDraft(outline).getAsJsonArray("sections");
        }
        if (stored.size() == 0) {
            return null;
        }
        List<ResearchDocument.Section> sections = new ArrayList<ResearchDocument.Section>();
        java.util.Map<String, String> passages = new java.util.LinkedHashMap<String, String>();
        for (com.google.gson.JsonElement element : stored) {
            com.google.gson.JsonObject section = element.getAsJsonObject();
            sections.add(new ResearchDocument.Section(section.get("heading").getAsString(),
                    section.get("text").getAsString()));
            if (section.has("passages") && section.get("passages").isJsonObject()) {
                for (java.util.Map.Entry<String, com.google.gson.JsonElement> entry
                        : section.getAsJsonObject("passages").entrySet()) {
                    passages.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
        }
        String title = "Recherchebericht";
        for (String line : outline.split("\\r?\\n")) {
            if (line.trim().startsWith("# ")) {
                title = line.trim().substring(2).trim();
                break;
            }
        }
        return ResearchDocument.fromDraft(title, outline, sections, passages, sourceRefs());
    }

    /** Wie viele Kapitel die aktuelle Gliederung hat ({@code ##}-Zeilen, wie der Agent sie liest). */
    public int outlineChapterCount() {
        int chapters = 0;
        for (String line : outline().split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                chapters++;
            }
        }
        return chapters;
    }

    private com.google.gson.JsonObject readDocumentDraft(String outline) {
        ensureOpen();
        String json = readText(new File(projectDirectory, DOCUMENT_DRAFT_FILE));
        com.google.gson.JsonObject draft = null;
        try {
            if (!json.trim().isEmpty()) {
                draft = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            }
        } catch (RuntimeException unreadable) {
            LOG.warning("Dokument-Zwischenstand nicht lesbar: " + unreadable.getMessage());
        }
        String current = outline == null ? "" : outline.trim();
        if (draft == null || !draft.has("outline") || !current.equals(draft.get("outline").getAsString().trim())
                || !draft.has("sections") || !draft.get("sections").isJsonArray()) {
            draft = new com.google.gson.JsonObject();
            draft.addProperty("outline", current);
            draft.add("sections", new com.google.gson.JsonArray());
        }
        return draft;
    }

    private void clearDocumentDraft() {
        File draft = new File(projectDirectory, DOCUMENT_DRAFT_FILE);
        if (draft.isFile() && !draft.delete()) {
            LOG.warning("Dokument-Zwischenstand nicht gelöscht: " + draft);
        }
    }

    private static void writeAtomically(File file, String text) {
        File temp = new File(file.getParentFile(), file.getName() + ".tmp");
        writeText(temp, text);
        try {
            java.nio.file.Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException notMoved) {
            LOG.warning("Nicht gespeichert: " + file.getName() + ": " + notMoved.getMessage());
        }
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

    /**
     * Die Konzeptwerkzeuge für den Werkzeugsatz des Agenten, ohne ein Projekt anzulegen: ohne Projekt die eines
     * frischen (Konzept/neu, nur lesen). Angelegt wird das Projekt erst, wenn ein Werkzeug aufgerufen wird.
     */
    synchronized List<com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution> conceptToolsPreview() {
        if (conceptHost == null && existingProject() == null) {
            return ResearchConceptTools.freshProjectTools();
        }
        return conceptHost().tools();
    }

    /** Ob Suchen angeboten wurden; ohne Projekt nein (legt nichts an). */
    synchronized boolean searchTagsOffered() {
        if (conceptHost == null && existingProject() == null) {
            return false;
        }
        return conceptHost().searchTagsOffered();
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

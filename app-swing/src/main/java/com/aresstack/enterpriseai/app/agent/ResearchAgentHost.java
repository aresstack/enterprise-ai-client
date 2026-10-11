package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;
import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeFenceView;
import com.aresstack.enterpriseai.research.domain.scope.ScopeFacet;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatch;
import com.aresstack.enterpriseai.research.domain.scope.ScopePatchOperations;
import com.aresstack.enterpriseai.research.domain.scope.UnresolvedScopeIssue;
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
 * <p>Ein Projekt ist ein Verzeichnis ({@code scope-draft.json} usw.). „+ Neuer Chat“ im Agent-Modus beginnt ein
 * neues Projekt ({@link #startNewProject}), beim Start wird das zuletzt benutzte fortgesetzt.
 */
public final class ResearchAgentHost {

    private static final Logger LOG = Logger.getLogger(ResearchAgentHost.class.getName());
    private static final String GREETED_MARKER = "greeted";
    private static final String CURRENT_PROJECT_FILE = "current-project";
    private static final String CONCEPT_DIRECTORY = "concept";

    private final File projectsRoot;
    private final Function<File, ResearchScopeDraftStore> stores;
    private final Function<File, ConceptBranchService> concepts;
    private final List<Runnable> stateListeners = new CopyOnWriteArrayList<Runnable>();
    private volatile Consumer<String> problemSink;
    private File projectDirectory;
    private ResearchScopeCoordinator coordinator;
    private ResearchConceptHost conceptHost;

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
        this.stores = stores;
        this.concepts = concepts;
    }

    /** Öffnet das zuletzt benutzte Projekt erst bei Bedarf: ohne Agent-Turn wird nichts angelegt. */
    private void ensureOpen() {
        if (coordinator == null) {
            File current = readCurrentProject();
            openProject(current != null ? current : newProjectDirectory());
        }
    }

    // ------------------------------------------------------------------ project

    /** Beginnt ein neues, leeres Research-Projekt (frischer Weidezaun); „+ Neuer Chat“ im Agent-Modus. */
    public void startNewProject() {
        synchronized (this) {
            openProject(newProjectDirectory());
        }
        fireStateChanged();
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
                });
        writeCurrentProject(directory);
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

    private File readCurrentProject() {
        File pointer = new File(projectsRoot, CURRENT_PROJECT_FILE);
        if (!pointer.isFile()) {
            return null;
        }
        try {
            String name = new String(java.nio.file.Files.readAllBytes(pointer.toPath()), "UTF-8").trim();
            File directory = new File(projectsRoot, name);
            return name.isEmpty() || name.contains("/") || name.contains("\\") || !directory.isDirectory()
                    ? null : directory;
        } catch (IOException unreadable) {
            LOG.warning("Aktuelles Research-Projekt nicht lesbar: " + unreadable.getMessage());
            return null;
        }
    }

    private void writeCurrentProject(File directory) {
        try {
            if (!projectsRoot.isDirectory() && !projectsRoot.mkdirs()) {
                return;
            }
            java.nio.file.Files.write(new File(projectsRoot, CURRENT_PROJECT_FILE).toPath(),
                    directory.getName().getBytes("UTF-8"));
        } catch (IOException notWritten) {
            LOG.warning("Aktuelles Research-Projekt nicht gespeichert: " + notWritten.getMessage());
        }
    }

    /** Ob der Agent dieses Projekt schon begrüßt hat (askai: Zustand scoping/new → weiter). */
    public synchronized boolean isGreeted() {
        ensureOpen();
        return new File(projectDirectory, GREETED_MARKER).isFile();
    }

    /** askai {@code handleGreetingDone}: die Begrüßung wird nie wiederholt. */
    public synchronized void markGreeted() {
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
        if (coordinator == null && readCurrentProject() != null) {
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
            if (conceptHost == null && readCurrentProject() != null) {
                ensureOpen();
            }
            return conceptHost == null ? null : conceptHost.service();
        }
    }

    /** askai {@code blacklistTermsForDisplay}: was der Konzeptbaum als ausgeschlossen markiert. */
    public List<String> blacklistTermsForDisplay() {
        ResearchConceptHost concept;
        synchronized (this) {
            if (conceptHost == null && readCurrentProject() != null) {
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

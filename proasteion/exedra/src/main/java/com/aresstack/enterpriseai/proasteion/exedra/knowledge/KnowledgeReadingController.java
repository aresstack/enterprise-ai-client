package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.FavoritesPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.SourceSearchState;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.FoundResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentNotIndexedException;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SourceSearchOutcome;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Verbindet Suchleiste, Lesebereich und Favoriten mit den Use Cases: die Suchleiste des Reiters „Wissensquellen“
 * durchsucht alle angehakten Quellen parallel ({@link SearchKnowledgeSourcesUseCase}), ein Klick auf eine Quelle, einen
 * Treffer oder einen Favoriten zeigt ihn im Lesebereich ({@link BrowseKnowledgeSourceUseCase}), ★ merkt ihn in den
 * Favoriten. Blockierende Arbeit läuft auf dem Arbeits-Executor, alles an der Oberfläche auf dem UI-Executor.
 *
 * <p>Vorladen wie MainframeMate {@code ConfluenceConnectionTab}: nach der Suche lädt der {@link KnowledgePrefetchService}
 * die Treffer ab Position 0 vor, nach jeder Auswahl und beim Scrollen ab der gewählten bzw. obersten sichtbaren Zeile
 * (die laufende Serie bricht ab). Ein Klick liest über den Priority-Lader: aus dem Cache, sonst sofort, nie hinter der
 * Vorlade-Warteschlange; die gelesene Seite wird danach indexiert.
 */
public final class KnowledgeReadingController implements KnowledgeReaderPanel.Listener, FavoritesPanel.Actions {

    private static final Logger LOG = Logger.getLogger(KnowledgeReadingController.class.getName());
    /** Kanal der Vorlade-Serie des Drawers. */
    static final String PREFETCH_CHANNEL = "drawer";

    private final ChatWorkspacePanel workspace;
    private final Supplier<List<KnowledgeSourceRegistration>> enabledSources;
    private final Function<String, KnowledgeSourceRegistration> lookup;
    private final SearchKnowledgeSourcesUseCase search;
    private final BrowseKnowledgeSourceUseCase browse;
    private final KnowledgePrefetchService prefetch;
    private final FavoritesStore favorites;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private int searchGeneration;
    /** Die Treffer der letzten Suche in Anzeigereihenfolge (nur auf dem UI-Thread). */
    private List<KnowledgePrefetchService.Candidate> hitCandidates = new ArrayList<KnowledgePrefetchService.Candidate>();
    private List<String> hitKeys = new ArrayList<String>();
    private int prefetchedFrom = -1;

    /**
     * @param enabledSources die angebundenen, angehakten Quellen (frisch je Suche gelesen)
     * @param lookup         die angebundene Quelle zur ID oder {@code null}
     * @param favorites      Ablage der Favoriten; {@code null}: ohne ★ und ohne Favoriten
     */
    public KnowledgeReadingController(ChatWorkspacePanel workspace,
                                      Supplier<List<KnowledgeSourceRegistration>> enabledSources,
                                      Function<String, KnowledgeSourceRegistration> lookup,
                                      SearchKnowledgeSourcesUseCase search, BrowseKnowledgeSourceUseCase browse,
                                      KnowledgePrefetchService prefetch, FavoritesStore favorites,
                                      Executor uiExecutor, Executor workExecutor) {
        if (workspace == null || enabledSources == null || lookup == null || search == null || browse == null
                || prefetch == null || uiExecutor == null || workExecutor == null) {
            throw new IllegalArgumentException("only favorites may be null");
        }
        this.workspace = workspace;
        this.enabledSources = enabledSources;
        this.lookup = lookup;
        this.search = search;
        this.browse = browse;
        this.prefetch = prefetch;
        this.favorites = favorites;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
    }

    /** Hängt sich an Lesebereich und Favoriten-Seite; auf dem UI-Thread. */
    public void attach() {
        workspace.reader().setListener(this);
        workspace.favorites().setActions(this);
        publishFavorites();
    }

    // ------------------------------------------------------------------ Suche

    /** Durchsucht alle angehakten Quellen gleichzeitig; ein neuer Aufruf verwirft das Ergebnis des alten. */
    public void searchRequested(final String text) {
        final int generation = ++searchGeneration;
        List<KnowledgeSourceRegistration> registrations = enabledSources.get();
        final List<KnowledgeSourcePort> ports = new ArrayList<KnowledgeSourcePort>();
        for (KnowledgeSourceRegistration registration : registrations) {
            ports.add(registration.port());
        }
        final int count = ports.size();
        if (count == 0) {
            workspace.knowledgeSources().setSearchState(SourceSearchState.done(text, 0, null, null));
            return;
        }
        workspace.knowledgeSources().setSearchState(SourceSearchState.running(text, count));
        search.search(ports, text, SearchKnowledgeSourcesUseCase.DEFAULT_LIMIT_PER_SOURCE)
                .whenComplete((outcomes, failure) -> uiExecutor.execute(() -> {
                    if (generation != searchGeneration) {
                        return; // inzwischen neu gesucht oder geleert
                    }
                    List<ReaderLink> hits = new ArrayList<ReaderLink>();
                    List<KnowledgePrefetchService.Candidate> candidates =
                            new ArrayList<KnowledgePrefetchService.Candidate>();
                    List<String> problems = new ArrayList<String>();
                    if (failure != null) {
                        LOG.log(Level.WARNING, "Quellensuche fehlgeschlagen", failure);
                        problems.add("Suche fehlgeschlagen (Protokoll)");
                    } else {
                        for (SourceSearchOutcome outcome : outcomes) {
                            if (outcome.failed()) {
                                problems.add(outcome.sourceId().value() + ": " + outcome.problem());
                            }
                            KnowledgeSourceRegistration owner = lookup.apply(outcome.sourceId().value());
                            for (FoundResource found : outcome.hits()) {
                                ReaderLink link = new ReaderLink(found.sourceId().value(), found.resourceId().value(),
                                        found.title(), found.snippet(), found.location());
                                hits.add(link);
                                if (owner != null) {
                                    candidates.add(new KnowledgePrefetchService.Candidate(owner.port(),
                                            found.resourceId(), found.revision()));
                                }
                            }
                        }
                    }
                    workspace.knowledgeSources().setSearchState(SourceSearchState.done(text, count, hits, problems));
                    // Wie MainframeMate nach der Suche: alle Treffer ab Position 0 im Hintergrund vorladen.
                    hitCandidates = candidates;
                    hitKeys = new ArrayList<String>();
                    for (KnowledgePrefetchService.Candidate candidate : candidates) {
                        hitKeys.add(candidate.id().value());
                    }
                    prefetchFrom(0, true);
                }));
    }

    public void searchCleared() {
        searchGeneration++;
        hitCandidates = new ArrayList<KnowledgePrefetchService.Candidate>();
        hitKeys = new ArrayList<String>();
        prefetchedFrom = -1;
        prefetch.prefetch(PREFETCH_CHANNEL, hitCandidates, 0); // bricht das Vorladen der alten Treffer ab
    }

    /** Die Trefferliste wurde gescrollt: ab der obersten sichtbaren Zeile weiter vorladen. Auf dem UI-Thread. */
    public void hitsScrolled(ReaderLink firstVisible) {
        if (firstVisible != null && !firstVisible.isSource()) {
            prefetchFrom(hitKeys.indexOf(firstVisible.resourceId()), false);
        }
    }

    /** Vorladen ab {@code index} (MainframeMate {@code prefetchPages(ids, fromIndex)}); gleiche Position: nichts. */
    private void prefetchFrom(int index, boolean force) {
        if (hitCandidates.isEmpty() || index < 0 || index >= hitCandidates.size()
                || (!force && index == prefetchedFrom)) {
            return;
        }
        prefetchedFrom = index;
        prefetch.prefetch(PREFETCH_CHANNEL, hitCandidates, index);
    }

    // ------------------------------------------------------------------ Lesebereich

    /** Zeigt Quelle, Treffer oder Favoriten im Lesebereich (Vorschau, mit {@code pin} angeheftet). */
    public void open(final ReaderLink link, boolean pin) {
        workspace.showReader();
        final KnowledgeReaderPanel reader = workspace.reader();
        if (!reader.open(link, pin)) {
            return;
        }
        // Wie MainframeMate nach jeder Auswahl: ab der gewählten Zeile weiter vorladen.
        if (!link.isSource()) {
            prefetchFrom(hitKeys.indexOf(link.resourceId()), false);
        }
        final KnowledgeSourceRegistration registration = lookup.apply(link.sourceId());
        if (registration == null) {
            reader.showProblem(link.key(), "Die Quelle „" + link.sourceId() + "“ ist nicht angebunden.");
            return;
        }
        try {
            workExecutor.execute(() -> load(link, registration));
        } catch (RuntimeException rejected) {
            reader.showProblem(link.key(), "Gerade nicht ladbar.");
        }
    }

    private void load(final ReaderLink link, KnowledgeSourceRegistration registration) {
        final KnowledgeReaderPanel reader = workspace.reader();
        try {
            if (link.isSource()) {
                final List<ReaderLink> entries = new ArrayList<ReaderLink>();
                for (KnowledgeResource resource : browse.list(registration.port(), registration.scope())) {
                    entries.add(new ReaderLink(link.sourceId(), resource.id().value(), resource.title(), "",
                            resource.id().value()));
                }
                uiExecutor.execute(() -> reader.showLinks(link.key(), entries, "Diese Quelle meldet keine Einträge."));
            } else {
                final KnowledgeResourceId id = KnowledgeResourceId.of(link.resourceId());
                // Priority-Lader: Cache oder sofort, nie hinter dem Vorladen; danach wird die Seite indexiert.
                final KnowledgeDocument document = prefetch.read(id, () -> browse.read(registration.port(), id));
                uiExecutor.execute(() -> reader.showMarkdown(link.key(), document.text()));
            }
        } catch (KnowledgeDocumentNotIndexedException e) {
            uiExecutor.execute(() -> reader.showProblem(link.key(), "Nicht lesbar (Protokoll)."));
        } catch (KnowledgeSourceException e) {
            LOG.log(Level.WARNING, "Lesen von " + link.key() + " fehlgeschlagen (" + e.kind() + ")", e);
            final String text = problemText(e);
            uiExecutor.execute(() -> reader.showProblem(link.key(), text));
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Lesen von " + link.key() + " fehlgeschlagen", e);
            uiExecutor.execute(() -> reader.showProblem(link.key(), "Nicht lesbar (Protokoll)."));
        }
    }

    private static String problemText(KnowledgeSourceException e) {
        switch (e.kind()) {
            case NOT_FOUND:
                return "Gibt es in der Quelle nicht (mehr).";
            case ACCESS_DENIED:
                return "Zugriff verweigert.";
            case UNAVAILABLE:
                return "Die Quelle ist gerade nicht erreichbar.";
            case UNSUPPORTED:
                return "Diese Adresse gehört nicht zu der Quelle.";
            default:
                return "Unerwartete Antwort der Quelle (Protokoll).";
        }
    }

    @Override
    public void linkOpened(ReaderLink link, boolean pin) {
        open(link, pin);
    }

    // ------------------------------------------------------------------ Favoriten

    @Override
    public boolean canFavorite() {
        return favorites != null;
    }

    @Override
    public boolean isFavorite(ReaderLink link) {
        return favorites != null && favorites.contains(link);
    }

    @Override
    public void favoriteToggled(ReaderLink link) {
        if (favorites != null) {
            favorites.toggle(link);
            publishFavorites();
        }
    }

    @Override
    public void opened(ReaderLink favorite, boolean pin) {
        open(favorite, pin);
    }

    @Override
    public void removed(ReaderLink favorite) {
        if (favorites != null) {
            favorites.remove(favorite);
            publishFavorites();
        }
    }

    private void publishFavorites() {
        workspace.favorites().setFavorites(favorites == null ? null : favorites.all());
        workspace.reader().refreshFavorite();
    }
}

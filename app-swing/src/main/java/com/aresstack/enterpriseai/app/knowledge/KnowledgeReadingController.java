package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.app.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.app.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.app.ui.workspace.FavoritesPanel;
import com.aresstack.enterpriseai.app.ui.workspace.SourceSearchState;
import com.aresstack.enterpriseai.application.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.application.knowledge.FoundResource;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.application.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.application.knowledge.SourceSearchOutcome;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
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
 */
public final class KnowledgeReadingController implements KnowledgeReaderPanel.Listener, FavoritesPanel.Actions {

    private static final Logger LOG = Logger.getLogger(KnowledgeReadingController.class.getName());

    private final ChatWorkspacePanel workspace;
    private final Supplier<List<KnowledgeSourceRegistration>> enabledSources;
    private final Function<String, KnowledgeSourceRegistration> lookup;
    private final SearchKnowledgeSourcesUseCase search;
    private final BrowseKnowledgeSourceUseCase browse;
    private final FavoritesStore favorites;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private int searchGeneration;

    /**
     * @param enabledSources die angebundenen, angehakten Quellen (frisch je Suche gelesen)
     * @param lookup         die angebundene Quelle zur ID oder {@code null}
     * @param favorites      Ablage der Favoriten; {@code null}: ohne ★ und ohne Favoriten
     */
    public KnowledgeReadingController(ChatWorkspacePanel workspace,
                                      Supplier<List<KnowledgeSourceRegistration>> enabledSources,
                                      Function<String, KnowledgeSourceRegistration> lookup,
                                      SearchKnowledgeSourcesUseCase search, BrowseKnowledgeSourceUseCase browse,
                                      FavoritesStore favorites, Executor uiExecutor, Executor workExecutor) {
        if (workspace == null || enabledSources == null || lookup == null || search == null || browse == null
                || uiExecutor == null || workExecutor == null) {
            throw new IllegalArgumentException("only favorites may be null");
        }
        this.workspace = workspace;
        this.enabledSources = enabledSources;
        this.lookup = lookup;
        this.search = search;
        this.browse = browse;
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
                    List<String> problems = new ArrayList<String>();
                    if (failure != null) {
                        LOG.log(Level.WARNING, "Quellensuche fehlgeschlagen", failure);
                        problems.add("Suche fehlgeschlagen (Protokoll)");
                    } else {
                        for (SourceSearchOutcome outcome : outcomes) {
                            if (outcome.failed()) {
                                problems.add(outcome.sourceId().value() + ": " + outcome.problem());
                            }
                            for (FoundResource found : outcome.hits()) {
                                hits.add(new ReaderLink(found.sourceId().value(), found.resourceId().value(),
                                        found.title(), found.snippet(), found.location()));
                            }
                        }
                    }
                    workspace.knowledgeSources().setSearchState(SourceSearchState.done(text, count, hits, problems));
                }));
    }

    public void searchCleared() {
        searchGeneration++;
    }

    // ------------------------------------------------------------------ Lesebereich

    /** Zeigt Quelle, Treffer oder Favoriten im Lesebereich (Vorschau, mit {@code pin} angeheftet). */
    public void open(final ReaderLink link, boolean pin) {
        workspace.showReader();
        final KnowledgeReaderPanel reader = workspace.reader();
        if (!reader.open(link, pin)) {
            return;
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
                final KnowledgeDocument document = browse.read(registration.port(),
                        KnowledgeResourceId.of(link.resourceId()));
                uiExecutor.execute(() -> reader.showMarkdown(link.key(), document.text()));
            }
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

package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.KnowledgeReaderPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderImage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderImageDescription;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderImageLoader;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.FavoritesPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.SourceSearchState;
import com.aresstack.enterpriseai.astu.acropolis.image.ImageAnalysis;
import com.aresstack.enterpriseai.astu.acropolis.image.SourceImageAnalysisUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.FoundResource;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SourceSearchOutcome;
import com.aresstack.enterpriseai.astu.acropolis.navigation.BuildDocumentNavigationUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.SourceScope;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
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
 *
 * <p>Hat die Quelle eine Lese-Ansicht ({@link PageView}, z. B. Confluence), zeigt der Reiter das Original-HTML mit den
 * Bildern an ihrer Stelle; die Bilder lädt er über die Quelle, eine Beschreibung erst, wenn die Diashow auf
 * „Beschreibung“ umschaltet ({@link SourceImageAnalysisUseCase#describe}, Modell der Kategorie VISION).</p>
 */
public final class KnowledgeReadingController implements KnowledgeReaderPanel.Listener, FavoritesPanel.Actions {

    private static final Logger LOG = Logger.getLogger(KnowledgeReadingController.class.getName());
    /** Kanal der Vorlade-Serie des Drawers. */
    static final String PREFETCH_CHANNEL = "drawer";
    static final String NO_VISION_MODEL = "Kein Modell für Bildverständnis gewählt (Einstellungen → Modelle).";
    static final String EMPTY_DESCRIPTION = "Das Modell hat keine Beschreibung geliefert.";
    static final String DESCRIPTION_FAILED = "Die Beschreibung ist fehlgeschlagen (Protokoll).";
    static final String DESCRIPTIONS_UNAVAILABLE = "Bildbeschreibung ist hier nicht verfügbar.";
    static final String IMAGE_FAILED_UNEXPECTEDLY = "Bild nicht ladbar: unerwarteter Fehler (Protokoll).";

    private final ChatWorkspacePanel workspace;
    private final Supplier<List<KnowledgeSourceRegistration>> enabledSources;
    private final Function<String, KnowledgeSourceRegistration> lookup;
    private final SearchKnowledgeSourcesUseCase search;
    private final BrowseKnowledgeSourceUseCase browse;
    private final KnowledgePrefetchService prefetch;
    private final FavoritesStore favorites;
    private final BuildDocumentNavigationUseCase navigation;
    private final Executor uiExecutor;
    private final Executor workExecutor;
    private int searchGeneration;
    /** Die Treffer der letzten Suche in Anzeigereihenfolge (nur auf dem UI-Thread). */
    private List<KnowledgePrefetchService.Candidate> hitCandidates = new ArrayList<KnowledgePrefetchService.Candidate>();
    private List<String> hitKeys = new ArrayList<String>();
    private int prefetchedFrom = -1;
    private volatile SourceImageAnalysisUseCase imageDescriptions;

    /**
     * @param enabledSources die angebundenen, angehakten Quellen (frisch je Suche gelesen)
     * @param lookup         die angebundene Quelle zur ID oder {@code null}
     * @param favorites      Ablage der Favoriten; {@code null}: ohne ★ und ohne Favoriten
     * @param navigation     baut die Dokumentnavigation (Wolkenbanner); {@code null}: Dokumente ohne Banner
     */
    public KnowledgeReadingController(ChatWorkspacePanel workspace,
                                      Supplier<List<KnowledgeSourceRegistration>> enabledSources,
                                      Function<String, KnowledgeSourceRegistration> lookup,
                                      SearchKnowledgeSourcesUseCase search, BrowseKnowledgeSourceUseCase browse,
                                      KnowledgePrefetchService prefetch, FavoritesStore favorites,
                                      BuildDocumentNavigationUseCase navigation,
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
        this.navigation = navigation;
        this.uiExecutor = uiExecutor;
        this.workExecutor = workExecutor;
    }

    /** Der Weg für Bildbeschreibungen auf Abruf; ohne ihn meldet die Diashow, dass es keine gibt. */
    public void describeImagesWith(SourceImageAnalysisUseCase descriptions) {
        this.imageDescriptions = descriptions;
    }

    /** Hängt sich an Lesebereich und Favoriten-Seite; auf dem UI-Thread. */
    public void attach() {
        workspace.reader().setListener(this);
        workspace.favorites().setActions(this);
        workspace.knowledgeSources().setFavoriteMarker(this);
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
                SourceScope scope = link.startPoint().isEmpty() ? registration.scope()
                        : SourceScope.builder().startPoint(link.startPoint()).ignoringSelection().build();
                for (KnowledgeResource resource : browse.list(registration.port(), scope)) {
                    entries.add(new ReaderLink(link.sourceId(), resource.id().value(), resource.title(), "",
                            resource.id().value()));
                }
                uiExecutor.execute(() -> reader.showLinks(link.key(), entries, "Diese Quelle meldet keine Einträge."));
            } else {
                final KnowledgeResourceId id = KnowledgeResourceId.of(link.resourceId());
                final PageView view = browse.view(registration.port(), id);
                if (view != null) {
                    final List<ReaderImage> images = new ArrayList<ReaderImage>();
                    final Map<String, PageView.Image> byKey = new LinkedHashMap<String, PageView.Image>();
                    for (PageView.Image image : view.images()) {
                        images.add(new ReaderImage(image.src(), image.src(), image.title()));
                        byKey.put(image.src(), image);
                    }
                    final ReaderImageLoader loader = new SourceImages(registration.port(), byKey);
                    uiExecutor.execute(() -> reader.showHtml(link.key(), view.html(), view.baseUri(), images, loader));
                    // Danach wie bisher aufbereiten (Index); der Banner zieht auf der Lese-Ansicht nach.
                    KnowledgeDocument viewed = indexAfterViewing(registration.port(), id, link);
                    if (viewed != null && navigation != null) {
                        navigate(link, viewed, false);
                    }
                    return;
                }
                // Priority-Thread: Cache oder sofort, nie hinter dem Vorladen; die Seite wird sofort aufbereitet.
                final KnowledgeDocument document = prefetch.openSelected(registration.port(), id);
                if (navigation == null) {
                    uiExecutor.execute(() -> reader.showMarkdown(link.key(), document.text()));
                    return;
                }
                navigate(link, document, true);
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

    /**
     * Sofort: Text und Originalüberschriften (oder die schon analysierte Navigation dieser Fassung). Der Banner zieht
     * nach, sobald die Index-Pipeline genau diese Fassung aufbereitet hat (ohne Vorrang). {@code show}: das Dokument
     * als Text zeigen; sonst steht schon die Lese-Ansicht (HTML), und nur der Banner kommt dazu.
     */
    private void navigate(final ReaderLink link, final KnowledgeDocument document, boolean show) {
        final KnowledgeReaderPanel reader = workspace.reader();
        final DocumentNavigation outline = navigation.outline(document);
        if (show) {
            uiExecutor.execute(() -> reader.showDocument(link.key(), document.text(), outline));
        } else {
            uiExecutor.execute(() -> reader.updateNavigation(link.key(), outline));
        }
        if (outline.analysis() != DocumentNavigation.Analysis.SEMANTIC) {
            navigation.analyze(document).thenAccept(analyzed -> {
                if (analyzed != outline) {
                    uiExecutor.execute(() -> reader.updateNavigation(link.key(), analyzed));
                }
            });
        }
    }

    /**
     * Wie bisher nach jedem Lesen: die Seite über den Priority-Lader aufbereiten (Index), die Anzeige steht schon.
     *
     * @return das aufbereitete Dokument oder {@code null}, wenn das fehlschlug
     */
    private KnowledgeDocument indexAfterViewing(KnowledgeSourcePort port, KnowledgeResourceId id, ReaderLink link) {
        try {
            return prefetch.openSelected(port, id);
        } catch (KnowledgeSourceException | RuntimeException e) {
            LOG.log(Level.FINE, "Aufbereiten von " + link.key() + " nach dem Anzeigen fehlgeschlagen", e);
            return null;
        }
    }

    /**
     * Bilder einer Lese-Ansicht: Bytes über die Quelle, Beschreibung auf Abruf über das VISION-Modell (Cache je Bild
     * und Version im Use Case). Blockierendes auf dem Arbeits-Executor, Rückrufe auf dem UI-Executor.
     */
    private final class SourceImages implements ReaderImageLoader {
        private final KnowledgeSourcePort port;
        private final Map<String, PageView.Image> byKey;

        SourceImages(KnowledgeSourcePort port, Map<String, PageView.Image> byKey) {
            this.port = port;
            this.byKey = byKey;
        }

        @Override
        public void load(final ReaderImage image, final Consumer<byte[]> done) {
            load(image, done, reason -> done.accept(null));
        }

        /** Der Grund eines Fehlschlags geht an die Oberfläche (Diashow) und ins Protokoll, nicht nur auf FINE. */
        @Override
        public void load(final ReaderImage image, final Consumer<byte[]> done, final Consumer<String> failed) {
            try {
                workExecutor.execute(() -> {
                    try {
                        final byte[] bytes = browse.viewImage(port, image.src());
                        uiExecutor.execute(() -> done.accept(bytes));
                    } catch (KnowledgeSourceException e) {
                        LOG.warning("Bild nicht ladbar (" + e.kind() + "): " + image.src() + ": " + e.getMessage());
                        final String reason = e.getMessage();
                        uiExecutor.execute(() -> failed.accept(reason));
                    } catch (RuntimeException e) {
                        LOG.log(Level.WARNING, "Bild nicht ladbar: " + image.src(), e);
                        uiExecutor.execute(() -> failed.accept(IMAGE_FAILED_UNEXPECTEDLY));
                    }
                });
            } catch (RuntimeException rejected) {
                failed.accept(null);
            }
        }

        @Override
        public void describe(final ReaderImage image, final String mediaType, final byte[] bytes,
                             final Consumer<ReaderImageDescription> done) {
            final SourceImageAnalysisUseCase descriptions = imageDescriptions;
            if (descriptions == null) {
                done.accept(ReaderImageDescription.unavailable(DESCRIPTIONS_UNAVAILABLE));
                return;
            }
            final PageView.Image source = byKey.get(image.key());
            try {
                workExecutor.execute(() -> {
                    final ReaderImageDescription described = describeOne(() -> {
                        boolean known = source != null && source.resource() != null;
                        return descriptions.describe(
                                known ? source.resource().id().value() : image.src(), image.title(),
                                known ? source.resource().revision().version() : "", mediaType, bytes);
                    }, image.src());
                    uiExecutor.execute(() -> done.accept(described));
                });
            } catch (RuntimeException rejected) {
                done.accept(ReaderImageDescription.unavailable(DESCRIPTION_FAILED));
            }
        }
    }

    /**
     * Ruft die Beschreibung ab und bildet das Ergebnis auf die Anzeige ab: ohne Bildmodell ({@code null}) der Hinweis
     * auf die Einstellungen, leere Antwort und Fehler je als eigener Hinweis.
     */
    static ReaderImageDescription describeOne(java.util.function.Supplier<ImageAnalysis> call, String src) {
        try {
            ImageAnalysis analysis = call.get();
            if (analysis == null) {
                return ReaderImageDescription.unavailable(NO_VISION_MODEL);
            }
            return analysis.description().isEmpty() ? ReaderImageDescription.unavailable(EMPTY_DESCRIPTION)
                    : ReaderImageDescription.of(analysis.description());
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Bildbeschreibung für " + src + " fehlgeschlagen", e);
            return ReaderImageDescription.unavailable(DESCRIPTION_FAILED);
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

    /** ★ an einer Zeile oder im Kopf: in den Ordner, den die Favoritenseite gerade vorgibt (MainframeMate). */
    @Override
    public void favoriteToggled(ReaderLink link) {
        if (favorites != null) {
            String added = favorites.toggle(link, workspace.favorites().targetFolderId());
            publishFavorites();
            if (added != null) {
                workspace.favorites().select(added); // der nächste Favorit landet daneben
            }
        }
    }

    @Override
    public void favoriteOpened(ReaderLink favorite, boolean pin) {
        open(favorite, pin);
    }

    @Override
    public void entryRemoveRequested(String id) {
        if (favorites != null && favorites.remove(id)) {
            publishFavorites();
        }
    }

    @Override
    public String folderCreateRequested(String parentId, String name) {
        if (favorites == null) {
            return null;
        }
        String id = favorites.createFolder(name, parentId);
        publishFavorites();
        return id;
    }

    @Override
    public void folderRenameRequested(String id, String name) {
        if (favorites != null && favorites.renameFolder(id, name)) {
            publishFavorites();
        }
    }

    @Override
    public boolean moveRequested(String id, String targetFolderId, int index) {
        if (favorites == null || !favorites.move(id, targetFolderId, index)) {
            return false;
        }
        publishFavorites();
        return true;
    }

    private void publishFavorites() {
        workspace.favorites().setFavorites(favorites == null ? null : favorites.tree());
        workspace.reader().refreshFavorite();
        workspace.knowledgeSources().refreshFavorites();
    }
}

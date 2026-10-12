package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.resource.KnowledgeBookmarks;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceListPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.SourceReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Die Quellen unter einer Chat-Antwort am Lesebereich und an den Favoriten: ein Klick öffnet die Quelle über denselben
 * Eingang wie Treffer und Favoriten ({@link KnowledgeReadingController#open(ReaderLink, boolean)}), nie im Browser.
 * Grundlage sind allein die stabilen Kennungen der Quelle (Wissensquelle, Ressource, Abschnitt); der sichtbare Ort
 * wird nie zurückgeparst und keine Kennung aus Titel oder Adresse erfunden. Quellen ohne gültige Kennungen (ältere
 * gespeicherte Chats) bleiben nur lesbar und kopierbar.
 *
 * <p>Das Bookmark ({@code confluence://...}) entsteht über {@link KnowledgeBookmarks} aus der Resource-ID; angezeigt
 * wird weiter der HTTP-Ort.
 */
public final class ChatSourceNavigation implements SourceListPanel.Actions {

    static final String FOLDER_PREFIX = "Quellen – ";
    static final String UNTITLED_CHAT = "Chat";

    /** The reader and favorites as far as the chat sources need them ({@link KnowledgeReadingController}). */
    public interface Reading {

        void open(ReaderLink link, boolean pin);

        boolean canFavorite();

        boolean addFavorite(ReaderLink link);

        FavoritesStore.BulkSave addFavorites(String folderName, List<ReaderLink> links);
    }

    private final Reading reading;
    private final Supplier<String> chatTitle;

    /**
     * @param reading   der Lesebereich samt Favoriten
     * @param chatTitle Titel des aktuellen Chats für den Sammelordner; {@code null} oder leer = „Chat“
     */
    public ChatSourceNavigation(Reading reading, Supplier<String> chatTitle) {
        if (reading == null) {
            throw new IllegalArgumentException("reading must not be null");
        }
        this.reading = reading;
        this.chatTitle = chatTitle;
    }

    /**
     * Map a chat source to a reader link: source and resource id as stored, the title, the visible location and the
     * chunk as passage.
     *
     * @return the link, {@code null} without a source id or a valid resource id
     */
    public static ReaderLink linkOf(SourceReference source) {
        if (source == null || source.getSourceId().isEmpty() || resourceIdOf(source) == null) {
            return null;
        }
        return new ReaderLink(source.getSourceId(), source.getResourceId(), source.getTitle(), "",
                source.getLocation()).withPassage(source.getChunkId());
    }

    /** Return the bookmark URI ({@code confluence://...}) of the source, empty without a valid resource id. */
    public static String bookmarkUriOf(SourceReference source) {
        KnowledgeResourceId id = source == null ? null : resourceIdOf(source);
        if (id == null) {
            return "";
        }
        try {
            return KnowledgeBookmarks.of(id).toString();
        } catch (IllegalArgumentException invalid) {
            return "";
        }
    }

    private static KnowledgeResourceId resourceIdOf(SourceReference source) {
        if (source.getResourceId().isEmpty()) {
            return null;
        }
        try {
            return KnowledgeResourceId.of(source.getResourceId());
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    @Override
    public boolean canOpen(SourceReference source) {
        return linkOf(source) != null;
    }

    @Override
    public void open(SourceReference source) {
        ReaderLink link = linkOf(source);
        if (link != null) {
            reading.open(link, false);
        }
    }

    @Override
    public void openAll(List<SourceReference> sources) {
        for (ReaderLink link : distinctLinks(sources)) {
            reading.open(link, true); // pinned, so each source keeps its own tab
        }
    }

    @Override
    public boolean canFavorite() {
        return reading.canFavorite();
    }

    @Override
    public String saveFavorite(SourceReference source) {
        ReaderLink link = linkOf(source);
        if (link == null || !reading.canFavorite()) {
            return "";
        }
        return reading.addFavorite(link) ? "Als Favorit gespeichert." : "War bereits ein Favorit.";
    }

    @Override
    public String saveAllFavorites(List<SourceReference> sources) {
        List<ReaderLink> links = distinctLinks(sources);
        if (links.isEmpty() || !reading.canFavorite()) {
            return "";
        }
        FavoritesStore.BulkSave saved = reading.addFavorites(folderName(), links);
        return saved == null ? "" : summary(saved);
    }

    @Override
    public String bookmarkOf(SourceReference source) {
        return bookmarkUriOf(source);
    }

    /** Return the folder name for a bulk save: {@code Quellen – <chat title>}. */
    String folderName() {
        String title = chatTitle == null ? null : chatTitle.get();
        return FOLDER_PREFIX + (title == null || title.trim().isEmpty() ? UNTITLED_CHAT : title.trim());
    }

    static String summary(FavoritesStore.BulkSave saved) {
        if (saved.added() == 0) {
            return saved.alreadyPresent() == 1 ? "Die Quelle war bereits Favorit."
                    : "Alle " + saved.alreadyPresent() + " Quellen waren bereits Favoriten.";
        }
        String added = saved.added() == 1 ? "1 Quelle gespeichert" : saved.added() + " Quellen gespeichert";
        if (saved.folderName() != null) {
            added += " in „" + saved.folderName() + "“";
        }
        if (saved.alreadyPresent() == 0) {
            return added + ".";
        }
        return added + ", " + saved.alreadyPresent() + (saved.alreadyPresent() == 1 ? " war" : " waren")
                + " bereits Favorit" + (saved.alreadyPresent() == 1 ? "." : "en.");
    }

    private static List<ReaderLink> distinctLinks(List<SourceReference> sources) {
        Set<ReaderLink> links = new LinkedHashSet<ReaderLink>();
        if (sources != null) {
            for (SourceReference source : sources) {
                ReaderLink link = linkOf(source);
                if (link != null) {
                    links.add(link);
                }
            }
        }
        return new ArrayList<ReaderLink>(links);
    }
}

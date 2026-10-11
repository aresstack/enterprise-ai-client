package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.SidebarIcons;

import javax.swing.JComponent;
import java.util.List;

/**
 * Der gezeichnete Stern rechts in einer {@link ReaderLinkRow}: leer = noch kein Favorit, gelb = Favorit. Ein Klick
 * schaltet um und öffnet den Verweis nicht.
 */
public final class FavoriteStarButton {

    static final String ADD_TOOLTIP = "Als Favorit merken";
    static final String REMOVE_TOOLTIP = "Aus den Favoriten entfernen";

    private final ReaderLink link;
    private final FavoriteMarker marker;
    private final ComposerButton button;

    private FavoriteStarButton(ReaderLink link, FavoriteMarker marker) {
        this.link = link;
        this.marker = marker;
        this.button = ComposerButton.iconButton(SidebarIcons.star(false), ADD_TOOLTIP);
        button.addActionListener(event -> {
            marker.favoriteToggled(link);
            refresh();
        });
        refresh();
    }

    /** Ein Stern für {@code link}, oder {@code null}, wenn es keine Favoriten gibt. */
    public static FavoriteStarButton forLink(ReaderLink link, FavoriteMarker marker) {
        return marker == null || !marker.canFavorite() ? null : new FavoriteStarButton(link, marker);
    }

    public JComponent component() {
        return button;
    }

    public ReaderLink link() {
        return link;
    }

    public boolean isFavorite() {
        return marker.isFavorite(link);
    }

    /** Zeichnet den Zustand neu (nach einer Änderung der Favoriten an anderer Stelle). */
    public void refresh() {
        boolean favorite = marker.isFavorite(link);
        button.setIcon(SidebarIcons.star(favorite));
        button.setToolTipText(favorite ? REMOVE_TOOLTIP : ADD_TOOLTIP);
    }

    /** Für Tests: der Klick. */
    public void click() {
        button.doClick();
    }

    static void refreshAll(List<FavoriteStarButton> stars) {
        for (FavoriteStarButton star : stars) {
            star.refresh();
        }
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.FavoriteEntry;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Die Grenzfälle der Ordnerlogik, an denen MainframeMates {@code BookmarkHelper} Einträge verlor. */
public class FavoritesStoreTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private static ReaderLink page(String id) {
        return new ReaderLink("confluence://host", "confluence:host/page/" + id, "Seite " + id, "", null);
    }

    private FavoritesStore store() throws Exception {
        return new FavoritesStore(temp.getRoot().toPath().resolve("favorites.json"));
    }

    private static List<String> names(List<FavoriteEntry> entries) {
        List<String> names = new ArrayList<String>();
        for (FavoriteEntry entry : entries) {
            names.add(entry.name());
        }
        return names;
    }

    @Test
    public void starLandsInTheChosenFolderOrInRootAndTogglesOffAnywhere() throws Exception {
        FavoritesStore store = store();
        String folder = store.createFolder("Doku", null);
        String inFolder = store.toggle(page("1"), folder);
        store.toggle(page("2"), null);
        store.toggle(page("3"), "gibt-es-nicht");
        assertEquals(1, store.tree().get(0).children().size());
        assertEquals(inFolder, store.tree().get(0).children().get(0).id());
        assertEquals(3, store.tree().size()); // Ordner + zwei in der Wurzel

        assertNull(store.toggle(page("1"), null)); // schon Favorit (im Ordner): Stern nimmt ihn heraus
        assertTrue(store.tree().get(0).children().isEmpty());
        assertFalse(store.contains(page("1")));
    }

    @Test
    public void droppingOntoAFavoriteIsRejectedInsteadOfLosingTheEntry() throws Exception {
        FavoritesStore store = store();
        String a = store.toggle(page("a"), null);
        String b = store.toggle(page("b"), null);
        assertFalse(store.move(a, b, -1));
        assertEquals(2, store.all().size());
    }

    @Test
    public void folderNeverMovesIntoItselfOrItsSubfolder() throws Exception {
        FavoritesStore store = store();
        String outer = store.createFolder("Außen", null);
        String inner = store.createFolder("Innen", outer);
        store.toggle(page("x"), inner);
        assertFalse(store.move(outer, outer, -1));
        assertFalse(store.move(outer, inner, -1));
        assertEquals(1, store.tree().size());
        assertEquals(1, store.tree().get(0).favoriteCount());
    }

    @Test
    public void movingDownInTheSameFolderLandsAtTheDropMark() throws Exception {
        FavoritesStore store = store();
        String a = store.toggle(page("a"), null);
        store.toggle(page("b"), null);
        store.toggle(page("c"), null);
        assertTrue(store.move(a, null, 2)); // Marke zwischen b und c
        assertEquals(names(store.tree()).toString(), "[Seite b, Seite a, Seite c]");
        assertTrue(store.move(a, null, 0));
        assertEquals(names(store.tree()).toString(), "[Seite a, Seite b, Seite c]");
    }

    @Test
    public void sameFolderNamesStayIndependent() throws Exception {
        FavoritesStore store = store();
        String first = store.createFolder("Neu", null);
        String second = store.createFolder("Neu", first);
        store.toggle(page("1"), first);
        store.toggle(page("2"), second);
        assertTrue(store.remove(second));
        assertEquals(1, store.tree().size());
        assertEquals(1, store.tree().get(0).favoriteCount());
        assertTrue(store.renameFolder(first, "Alt"));
        assertEquals("Alt", store.tree().get(0).name());
    }

    @Test
    public void treeSurvivesARestartAndTheOldListFormatIsDiscarded() throws Exception {
        FavoritesStore store = store();
        String folder = store.createFolder("Doku", null);
        store.toggle(page("1"), folder);
        FavoritesStore reloaded = store();
        assertEquals(folder, reloaded.tree().get(0).id());
        assertTrue(reloaded.contains(page("1")));

        Path file = temp.getRoot().toPath().resolve("favorites.json");
        Files.write(file, "[{\"sourceId\":\"confluence://host\",\"resourceId\":\"x\"}]".getBytes(StandardCharsets.UTF_8));
        assertTrue(store().tree().isEmpty());
        Files.write(file, "null".getBytes(StandardCharsets.UTF_8));
        assertTrue(store().tree().isEmpty());
    }

    @Test
    public void addNeverRemovesAnExistingFavorite() throws Exception {
        FavoritesStore store = store();
        String folder = store.createFolder("Doku", null);
        String first = store.add(page("1"), folder);
        assertNull(store.add(page("1"), null)); // already a favorite: stays in its folder
        assertTrue(store.contains(page("1")));
        assertEquals(first, store.tree().get(0).children().get(0).id());
        assertEquals(1, store.all().size());
    }

    @Test
    public void severalFavoritesMoveTogetherIntoAFolderInTreeOrder() throws Exception {
        FavoritesStore store = store();
        String folder = store.createFolder("Projekt X", null);
        store.toggle(page("a"), null);
        String b = store.toggle(page("b"), null);
        store.toggle(page("c"), null);
        String d = store.toggle(page("d"), null);
        assertTrue(store.moveAll(java.util.Arrays.asList(d, b), folder, -1)); // order of the request does not count
        assertEquals("[Seite b, Seite d]", names(store.tree().get(0).children()).toString());
        assertEquals("[Projekt X, Seite a, Seite c]", names(store.tree()).toString());
        assertEquals("persisted", "[Seite b, Seite d]", names(store().tree().get(0).children()).toString());
    }

    @Test
    public void aBlockLandsBetweenRowsAlsoInsideTheSameFolder() throws Exception {
        FavoritesStore store = store();
        String a = store.toggle(page("a"), null);
        String b = store.toggle(page("b"), null);
        store.toggle(page("c"), null);
        store.toggle(page("d"), null);
        store.toggle(page("e"), null);
        assertTrue(store.moveAll(java.util.Arrays.asList(a, b), null, 4)); // mark between d and e
        assertEquals("[Seite c, Seite d, Seite a, Seite b, Seite e]", names(store.tree()).toString());
        assertTrue(store.moveAll(java.util.Arrays.asList(a, b), null, 0));
        assertEquals("[Seite a, Seite b, Seite c, Seite d, Seite e]", names(store.tree()).toString());
        assertTrue("free area: end of the top level", store.moveAll(java.util.Arrays.asList(a, b), null, -1));
        assertEquals("[Seite c, Seite d, Seite e, Seite a, Seite b]", names(store.tree()).toString());
    }

    @Test
    public void aMixedSelectionMovesASubtreeOnceAndNeverIntoItself() throws Exception {
        FavoritesStore store = store();
        String outer = store.createFolder("Außen", null);
        String inside = store.toggle(page("i"), outer);
        String loose = store.toggle(page("l"), null);
        String target = store.createFolder("Ziel", null);
        assertTrue(store.moveAll(java.util.Arrays.asList(inside, outer, loose), target, -1));
        FavoriteEntry moved = store.tree().get(0);
        assertEquals("[Außen, Seite l]", names(moved.children()).toString());
        assertEquals(1, moved.children().get(0).children().size());
        assertEquals(2, store.all().size());

        assertFalse(store.moveAll(java.util.Arrays.asList(loose, target), outer, -1));
        assertFalse("unknown ids reject the whole move", store.moveAll(java.util.Arrays.asList(loose, "nope"), null, 0));
        assertEquals(2, store.all().size());
    }
}

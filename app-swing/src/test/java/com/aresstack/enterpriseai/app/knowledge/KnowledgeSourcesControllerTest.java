package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.SourceConfig;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.app.ui.settings.SourceActions;
import com.aresstack.enterpriseai.app.ui.settings.SourceForm;
import com.aresstack.enterpriseai.app.ui.settings.SourcePanel;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Häkchen, Indexstand, Indexieren, Hinzufügen, Bearbeiten und Entfernen des Drawer-Reiters „Wissensquellen“. */
public class KnowledgeSourcesControllerTest {

    private static final Executor DIRECT = Runnable::run;
    private static final long NOON_UTC = 12L * 3600 * 1000;

    private final DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
    private final InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
    private final IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings,
            embeddings.modelIdentity(), new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
    private final KnowledgeIndexingBinding binding = new KnowledgeIndexingBinding(indexing,
            new KnowledgeStatusModel(), DIRECT, DIRECT, () -> NOON_UTC, ZoneId.of("UTC"));
    private final KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
    private final Function<KnowledgeSourceId, Integer> count = id ->
            index.resourceIds(embeddings.modelIdentity(), id).size();
    private final List<List<KnowledgeSourceItem>> published = new ArrayList<List<KnowledgeSourceItem>>();
    private final FakeFile file = new FakeFile();

    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("1", "Urlaub", "Dreißig Tage Urlaub.").add("2", "Gleitzeit", "Kernzeit bis 15 Uhr.");

    private KnowledgeSourcesController controller(Function<SourceConfig, KnowledgeSourcePort> factory) {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(wiki, SourceScope.of("1", "2")));
        selection.register(wiki.sourceId(), true);
        file.forms.add(form("wiki", true));
        KnowledgeSourcesController controller = new KnowledgeSourcesController(
                Collections.singletonList(resolve("wiki")), catalog, selection, binding, count, factory, DIRECT, DIRECT,
                () -> NOON_UTC, ZoneId.of("UTC"));
        controller.attach(published::add);
        return controller;
    }

    private static SourceForm form(String id, boolean enabled) {
        return SourceForm.builder(id, SourceForm.TYPE_MEDIAWIKI).url("http://127.0.0.1:9/w/api.php")
                .startPoints("Urlaub").enabled(enabled).build();
    }

    private KnowledgeSourceItem last(String id) {
        for (KnowledgeSourceItem item : published.get(published.size() - 1)) {
            if (item.id().equals(id)) {
                return item;
            }
        }
        return null;
    }

    @Test
    public void indexingASourceShowsItsPagesAndTime() {
        KnowledgeSourcesController controller = controller(null);
        assertEquals(KnowledgeSourcesController.NOT_INDEXED_TEXT, last("wiki").status());
        assertTrue(last("wiki").indexable());
        assertFalse("ohne Datei kein Bearbeiten", last("wiki").editable());

        controller.indexRequested("wiki");
        assertEquals("2 Seiten im Index · Stand 12:00", last("wiki").status());
        assertEquals(KnowledgeSourceItem.State.IDLE, last("wiki").state());
        boolean sawRunning = false;
        for (List<KnowledgeSourceItem> items : published) {
            sawRunning |= KnowledgeSourcesController.RUNNING_TEXT.equals(items.get(0).status());
        }
        assertTrue("„Wird indexiert …“ erscheint während des Laufs", sawRunning);
    }

    @Test
    public void checkboxSwitchesRetrievalAndIndexingAndIsSaved() {
        KnowledgeSourcesController controller = controller(null);
        controller.setEditing(file, id -> null, (initial, originalId, actions) -> null);
        controller.enabledChanged("wiki", false);
        assertFalse(selection.isEnabled(wiki.sourceId()));
        assertTrue(selection.allowedSources().isEmpty());
        assertEquals(Collections.singletonList("enabled wiki false"), file.calls);
        assertFalse(last("wiki").enabled());
        assertFalse(last("wiki").indexable());
        assertEquals(KnowledgeSourcesController.DISABLED_TEXT, last("wiki").status());

        controller.indexRequested("wiki");
        assertTrue("abgewählte Quellen werden nicht indexiert",
                index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).isEmpty());

        controller.enabledChanged("wiki", true);
        assertTrue(last("wiki").enabled());
        assertTrue(last("wiki").indexable());
    }

    @Test
    public void addedSourceIsConnectedAndIndexedWithoutRestart() {
        final InMemoryKnowledgeSource handbook = new InMemoryKnowledgeSource("wiki2")
                .add("9", "Homeoffice", "Zwei Tage pro Woche.");
        KnowledgeSourcesController controller = controller(config -> handbook);
        final List<String> offered = new ArrayList<String>();
        controller.setEditing(file, this::resolve, (initial, originalId, actions) -> {
            offered.add(initial.id() + "/" + originalId);
            SourceForm saved = SourceForm.builder(initial.id(), initial.type()).url("http://127.0.0.1:9/w/api.php")
                    .startPoints("9").build();
            file.forms.add(saved);
            return new KnowledgeSourcesController.SourceEditorLauncher.Result(SourcePanel.Outcome.SAVED, saved);
        });
        assertTrue(controller.canAdd());
        controller.addRequested(SourceForm.TYPE_MEDIAWIKI);

        assertEquals("die nächste freie ID wird vorgeschlagen", Collections.singletonList("wiki2/null"), offered);
        assertTrue(selection.isEnabled(handbook.sourceId()));
        assertEquals(1, index.resourceIds(embeddings.modelIdentity(), handbook.sourceId()).size());
        assertEquals("1 Seite im Index · Stand 12:00", last("wiki2").status());
        assertTrue(last("wiki2").editable());
    }

    @Test
    public void withoutFactoryChangesApplyAfterRestart() {
        KnowledgeSourcesController controller = controller(null);
        controller.setEditing(file, this::resolve, (initial, originalId, actions) -> {
            SourceForm saved = form(initial.id(), true);
            file.forms.add(saved);
            return new KnowledgeSourcesController.SourceEditorLauncher.Result(SourcePanel.Outcome.SAVED, saved);
        });
        controller.addRequested(SourceForm.TYPE_CONFLUENCE);
        KnowledgeSourceItem added = last("confluence");
        assertEquals(KnowledgeSourcesController.RESTART_TEXT, added.status());
        assertFalse(added.indexable());
        assertFalse(selection.isRegistered(KnowledgeSourceId.of("confluence")));
    }

    @Test
    public void removedSourceIsNoLongerSearched() {
        KnowledgeSourcesController controller = controller(null);
        controller.setEditing(file, this::resolve, (initial, originalId, actions) -> {
            file.forms.clear();
            return new KnowledgeSourcesController.SourceEditorLauncher.Result(SourcePanel.Outcome.REMOVED, null);
        });
        controller.editRequested("wiki");
        assertFalse(selection.isRegistered(wiki.sourceId()));
        assertTrue(published.get(published.size() - 1).isEmpty());
        assertNull(last("wiki"));
    }

    @Test
    public void brokenFileSourcesShowTheirFirstProblem() {
        KnowledgeSourcesController controller = controller(null);
        file.forms.add(form("kaputt", true));
        file.problems = Collections.singletonList("URL (source.kaputt.apiUrl): fehlt");
        controller.setEditing(file, this::resolve, (initial, originalId, actions) -> null);
        KnowledgeSourceItem broken = last("kaputt");
        assertEquals(KnowledgeSourceItem.State.PROBLEM, broken.state());
        assertEquals("Fehlerhaft: URL (source.kaputt.apiUrl): fehlt", broken.status());
        assertTrue("über ✎ korrigierbar", broken.editable());
    }

    private SourceConfig resolve(String id) {
        Properties p = new Properties();
        p.setProperty("source." + id + ".type", "mediawiki");
        p.setProperty("source." + id + ".apiUrl", "http://127.0.0.1:9/w/api.php");
        p.setProperty("source." + id + ".startPoints", "9");
        return AppConfigLoader.sourceSection(p, id);
    }

    private static final class FakeFile implements SourceActions {
        final List<SourceForm> forms = new ArrayList<SourceForm>();
        final List<String> calls = new ArrayList<String>();
        List<String> problems = Collections.emptyList();

        @Override
        public List<SourceForm> sources() {
            return new ArrayList<SourceForm>(forms);
        }

        @Override
        public List<String> validate(SourceForm draft, String originalId) {
            return draft.id().equals("wiki") ? Collections.<String>emptyList() : problems;
        }

        @Override
        public void save(SourceForm draft, String originalId) throws IOException {
            calls.add("save " + draft.id());
        }

        @Override
        public void remove(String id) {
            calls.add("remove " + id);
        }

        @Override
        public void setEnabled(String id, boolean enabled) {
            calls.add("enabled " + id + " " + enabled);
        }
    }
}

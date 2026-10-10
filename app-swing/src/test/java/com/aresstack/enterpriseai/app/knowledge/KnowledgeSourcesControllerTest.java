package com.aresstack.enterpriseai.app.knowledge;

import com.aresstack.enterpriseai.app.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.application.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.application.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.application.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.source.SourceSettingField;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.knowledge.api.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceDefinitionStore;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryKnowledgeSource;
import org.junit.Test;

import java.io.IOException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
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
    private final FakeProvider provider = new FakeProvider();

    private final InMemoryKnowledgeSource wiki = new InMemoryKnowledgeSource("wiki")
            .add("1", "Urlaub", "Dreißig Tage Urlaub.").add("2", "Gleitzeit", "Kernzeit bis 15 Uhr.");

    private KnowledgeSourcesController controller(boolean withProvider) {
        KnowledgeSourceCatalog catalog = KnowledgeSourceCatalog.of(
                new KnowledgeSourceRegistration(wiki, SourceScope.of("1", "2")));
        selection.register(wiki.sourceId(), true);
        file.definitions.add(definition("wiki", true));
        List<KnowledgeSourceProvider> providers = withProvider
                ? Collections.<KnowledgeSourceProvider>singletonList(provider)
                : Collections.<KnowledgeSourceProvider>emptyList();
        KnowledgeSourcesController controller = new KnowledgeSourcesController(
                Collections.singletonList(definition("wiki", true)), catalog, selection, binding, count,
                new KnowledgeSourceManagement(providers, null, index), DIRECT, DIRECT, () -> NOON_UTC,
                ZoneId.of("UTC"));
        controller.attach(published::add);
        return controller;
    }

    private static SourceDefinition definition(String id, boolean enabled) {
        return new SourceDefinition(id, "mediawiki", enabled, SourceSettings.empty()
                .with("apiUrl", "http://127.0.0.1:9/w/api.php").with("startPoints", "9"));
    }

    /** Ein Launcher, der den Entwurf mit {@code edit} beantwortet und die Rückfrage mit {@code confirm}. */
    private static KnowledgeSourcesController.SourceEditorLauncher launcher(final Editor edit, final boolean confirm) {
        return new KnowledgeSourcesController.SourceEditorLauncher() {
            @Override
            public SourceDefinition edit(SourceDefinition initial, String originalId, List<KnowledgeSourceType> types,
                                         com.aresstack.enterpriseai.app.ui.settings.SourceActions actions) {
                return edit.edit(initial, originalId, actions);
            }

            @Override
            public boolean confirmRemove(String sourceId, String typeName) {
                return confirm;
            }
        };
    }

    private interface Editor {
        SourceDefinition edit(SourceDefinition initial, String originalId,
                              com.aresstack.enterpriseai.app.ui.settings.SourceActions actions);
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
        KnowledgeSourcesController controller = controller(false);
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
        KnowledgeSourcesController controller = controller(false);
        controller.setEditing(file, launcher((initial, originalId, actions) -> null, false));
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
        provider.sources.put("wiki2", handbook);
        KnowledgeSourcesController controller = controller(true);
        final List<String> offered = new ArrayList<String>();
        controller.setEditing(file, launcher((initial, originalId, actions) -> {
            offered.add(initial.id() + "/" + originalId);
            SourceDefinition saved = definition(initial.id(), true);
            try {
                actions.save(saved, originalId);
            } catch (IOException e) {
                throw new AssertionError(e);
            }
            return saved;
        }, false));
        assertTrue(controller.canAdd());
        controller.addRequested();

        assertEquals("die nächste freie ID wird vorgeschlagen", Collections.singletonList("wiki2/null"), offered);
        assertTrue(selection.isEnabled(handbook.sourceId()));
        assertEquals(1, index.resourceIds(embeddings.modelIdentity(), handbook.sourceId()).size());
        assertEquals("1 Seite im Index · Stand 12:00", last("wiki2").status());
        assertTrue(last("wiki2").editable());
    }

    @Test
    public void withoutAdapterChangesApplyAfterRestart() {
        KnowledgeSourcesController controller = controller(false);
        controller.setEditing(file, launcher((initial, originalId, actions) -> null, false));
        controller.editRequested("wiki");
        file.definitions.add(new SourceDefinition("confluence", "confluence", true, SourceSettings.empty()));
        controller.enabledChanged("confluence", true);
        KnowledgeSourceItem added = last("confluence");
        assertFalse(added.indexable());
        assertFalse(selection.isRegistered(KnowledgeSourceId.of("confluence")));
    }

    @Test
    public void removedSourceIsNoLongerSearchedAndItsIndexIsWithdrawn() {
        KnowledgeSourcesController controller = controller(true);
        controller.indexRequested("wiki");
        assertFalse(index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).isEmpty());
        controller.setEditing(file, launcher((initial, originalId, actions) -> null, false));
        controller.removeRequested("wiki");
        assertTrue("ohne Bestätigung bleibt die Quelle", selection.isRegistered(wiki.sourceId()));

        controller.setEditing(file, launcher((initial, originalId, actions) -> null, true));
        controller.removeRequested("wiki");
        assertFalse(selection.isRegistered(wiki.sourceId()));
        assertEquals(Collections.singletonList("remove wiki"), file.calls);
        assertTrue(published.get(published.size() - 1).isEmpty());
        assertNull(last("wiki"));
        assertTrue("der Index der Quelle wird zurückgezogen",
                index.resourceIds(embeddings.modelIdentity(), wiki.sourceId()).isEmpty());
    }

    @Test
    public void brokenFileSourcesShowTheirFirstProblem() {
        KnowledgeSourcesController controller = controller(true);
        file.definitions.add(new SourceDefinition("kaputt", "mediawiki", true, SourceSettings.empty()));
        controller.setEditing(file, launcher((initial, originalId, actions) -> null, false));
        KnowledgeSourceItem broken = last("kaputt");
        assertEquals(KnowledgeSourceItem.State.PROBLEM, broken.state());
        assertEquals("Fehlerhaft: API-URL: fehlt", broken.status());
        assertTrue("über ✎ korrigierbar", broken.editable());
    }

    private static final class FakeFile implements SourceDefinitionStore {
        final List<SourceDefinition> definitions = new ArrayList<SourceDefinition>();
        final List<String> calls = new ArrayList<String>();

        @Override
        public List<SourceDefinition> definitions() {
            return new ArrayList<SourceDefinition>(definitions);
        }

        @Override
        public void save(SourceDefinition definition, String originalId) throws IOException {
            calls.add("save " + definition.id());
            definitions.add(definition);
        }

        @Override
        public void remove(String id) {
            calls.add("remove " + id);
            for (int i = definitions.size() - 1; i >= 0; i--) {
                if (definitions.get(i).id().equals(id)) {
                    definitions.remove(i);
                }
            }
        }

        @Override
        public void setEnabled(String id, boolean enabled) {
            calls.add("enabled " + id + " " + enabled);
        }
    }

    /** Ein Quelltyp „mediawiki“, der seine Quellen aus einer Tabelle liefert. */
    private static final class FakeProvider implements KnowledgeSourceProvider {
        final Map<String, KnowledgeSourcePort> sources = new HashMap<String, KnowledgeSourcePort>();
        private final KnowledgeSourceType type = KnowledgeSourceType.builder("mediawiki", "wiki", "MediaWiki")
                .idPrefix("wiki").summaryKey("startPoints")
                .field(SourceSettingField.text("apiUrl", "API-URL", "").required())
                .field(SourceSettingField.text("startPoints", "Startseiten", ""))
                .build();

        @Override
        public KnowledgeSourceType type() {
            return type;
        }

        @Override
        public List<String> validate(SourceSettings settings) {
            return settings.has("apiUrl") ? Collections.<String>emptyList()
                    : Collections.singletonList("API-URL: fehlt");
        }

        @Override
        public SourceScope scope(SourceSettings settings) {
            return SourceScope.of(settings.get("startPoints").split(","));
        }

        @Override
        public KnowledgeSourcePort open(KnowledgeSourceId sourceId, SourceSettings settings) {
            KnowledgeSourcePort port = sources.get(sourceId.value());
            if (port == null) {
                throw new IllegalArgumentException("unbekannt: " + sourceId);
            }
            return port;
        }
    }
}

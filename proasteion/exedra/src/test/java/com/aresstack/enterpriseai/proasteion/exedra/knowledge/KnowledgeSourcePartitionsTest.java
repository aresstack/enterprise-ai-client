package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.source.KnowledgeSourceManagement;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.source.SourceLocators;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.application.knowledge.KnowledgeSourceSelection;
import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.proasteion.exedra.chat.KnowledgeIndexingBinding;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.reader.ReaderLink;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.KnowledgeSourceActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.KnowledgeSourceRow;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourcePartition;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.ui.comic.paint.StrokeIcon;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Spaces im Drawer wie MainframeMate: Chevron als Icon, Auf-/Zugeklappt im Anwendungszustand, Häkchen und Name
 * getrennt, der Name öffnet die Startseite des Space unter ihrer kanonischen Ressourcen-ID.
 */
public class KnowledgeSourcePartitionsTest {

    private static final Executor DIRECT = Runnable::run;
    private static final String SOURCE = "cf";
    private static final KnowledgeResourceId HOME =
            KnowledgeResourceId.of("confluence", "confluence.example.org/wiki/page/100");

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final List<List<KnowledgeSourceItem>> published = new ArrayList<List<KnowledgeSourceItem>>();
    private final List<String> calls = new ArrayList<String>();
    private final List<ReaderLink> opened = new ArrayList<ReaderLink>();

    private KnowledgeSourcesController controller(Path stateFile) {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        KnowledgeIndexingBinding binding = new KnowledgeIndexingBinding(indexing, new KnowledgeStatusModel(),
                DIRECT, DIRECT, () -> 0L, ZoneId.of("UTC"));
        KnowledgeSourceSelection selection = new KnowledgeSourceSelection();
        Spaces port = new Spaces();
        selection.register(port.sourceId(), true);
        SourceDefinition definition = new SourceDefinition(SOURCE, "confluence", true, SourceSettings.empty());
        KnowledgeSourcesController controller = new KnowledgeSourcesController(
                Collections.singletonList(definition),
                KnowledgeSourceCatalog.of(new KnowledgeSourceRegistration(port, SourceScope.of("space:*"))),
                selection, binding, null,
                new KnowledgeSourceManagement(Collections.<KnowledgeSourceProvider>singletonList(new Provider()),
                        null, index),
                DIRECT, DIRECT, () -> 0L, ZoneId.of("UTC"));
        controller.setPartitionSelection(new SourcePartitionSelection(new ApplicationStateService(stateFile)));
        controller.attach(published::add);
        return controller;
    }

    private KnowledgeSourceItem last() {
        return published.get(published.size() - 1).get(0);
    }

    private KnowledgeSourceRow row(KnowledgeSourceItem item) {
        return new KnowledgeSourceRow(item, new KnowledgeSourceActions() {
            @Override
            public void enabledChanged(String sourceId, boolean enabled) {
            }

            @Override
            public void indexRequested(String sourceId) {
            }

            @Override
            public void editRequested(String sourceId) {
            }

            @Override
            public void removeRequested(String sourceId) {
            }

            @Override
            public void addRequested() {
            }

            @Override
            public void partitionChanged(String sourceId, String key, boolean selected) {
                calls.add("partition " + key + " " + selected);
            }

            @Override
            public void linkOpened(ReaderLink link, boolean pin) {
                opened.add(link);
            }
        }, ComicPalette.defaultPalette());
    }

    private static void click(JLabel label) {
        MouseEvent event = new MouseEvent(label, MouseEvent.MOUSE_CLICKED, 0, MouseEvent.BUTTON1_DOWN_MASK, 1, 1, 1,
                false, MouseEvent.BUTTON1);
        for (MouseListener listener : label.getMouseListeners()) {
            listener.mouseClicked(event);
        }
    }

    @Test
    public void chevronIsAnIconAndNotUnicodeText() throws Exception {
        KnowledgeSourcesController controller = controller(temp.newFile("state.json").toPath());
        JLabel collapsed = row(last()).partitionsToggle();
        assertTrue(collapsed.getIcon() instanceof StrokeIcon);
        assertEquals("Spaces", collapsed.getText());

        controller.partitionsToggled(SOURCE, true);
        JLabel expanded = row(last()).partitionsToggle();
        assertTrue(expanded.getIcon() instanceof StrokeIcon);
        assertEquals("Spaces · alle 2", expanded.getText());
        assertFalse(expanded.getText().contains("▸") || expanded.getText().contains("▾"));
    }

    @Test
    public void expandedStateSurvivesARestart() throws Exception {
        Path state = temp.newFile("state.json").toPath();
        controller(state).partitionsToggled(SOURCE, true);

        published.clear();
        controller(state);
        assertTrue("nach dem Neustart wieder aufgeklappt", last().partitionsExpanded());
        assertEquals("Spaces gleich geladen", 2, last().partitions().size());
        assertTrue(new SourcePartitionSelection(new ApplicationStateService(state))
                .excluded(SourceAddress.of(SOURCE)).isEmpty());

        controller(state).partitionsToggled(SOURCE, false);
        published.clear();
        controller(state);
        assertFalse(last().partitionsExpanded());
    }

    @Test
    public void homepageIdSurvivesUpToTheReaderLinkAndAClickOpensIt() throws Exception {
        controller(temp.newFile("state.json").toPath()).partitionsToggled(SOURCE, true);
        assertEquals(HOME.value(), last().partitions().get(0).link().resourceId());
        assertEquals("ohne Startseite: Seiten des Space", "space:OPS", last().partitions().get(1).link().startPoint());

        KnowledgeSourceRow row = row(last());
        click(row.partitionLinks().get(0));
        assertEquals(1, opened.size());
        assertEquals(SOURCE, opened.get(0).sourceId());
        assertEquals(HOME.value(), opened.get(0).resourceId());
        assertTrue("Label-Klick ändert die Checkbox nicht", row.partitionBoxes().get(0).isSelected());
        assertTrue(calls.isEmpty());
    }

    @Test
    public void checkboxClickOnlyChangesTheSelection() throws Exception {
        controller(temp.newFile("state.json").toPath()).partitionsToggled(SOURCE, true);
        KnowledgeSourceRow row = row(last());
        JCheckBox box = row.partitionBoxes().get(0);
        assertEquals("", box.getText());
        box.doClick();
        assertFalse(box.isSelected());
        assertEquals(Collections.singletonList("partition DEV false"), calls);
        assertTrue("Häkchen öffnet nichts", opened.isEmpty());
    }

    /** Eine Quelle mit zwei Spaces, einer mit, einer ohne Startseite. */
    private static final class Spaces implements KnowledgeSourcePort {
        @Override
        public SourceAddress sourceId() {
            return SourceAddress.of(SOURCE);
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "keine Seiten");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
        }

        @Override
        public List<SourcePartition> partitions() {
            return Arrays.asList(new SourcePartition("DEV", "Entwicklung", HOME, "space:DEV"),
                    new SourcePartition("OPS", "Betrieb", null, "space:OPS"));
        }
    }

    private static final class Provider implements KnowledgeSourceProvider {
        private final KnowledgeSourceType type = KnowledgeSourceType.builder("confluence", "confluence", "Confluence")
                .partitionsLabel("Spaces").build();

        @Override
        public KnowledgeSourceType type() {
            return type;
        }

        @Override
        public List<String> validate(SourceSettings settings) {
            return Collections.emptyList();
        }

        @Override
        public SourceScope scope(SourceSettings settings) {
            return SourceScope.of("space:*");
        }

        @Override
        public String locator(SourceSettings settings) {
            return SourceLocators.of(settings.get("site"));
        }

        @Override
        public KnowledgeSourcePort open(SourceAddress sourceId, SourceSettings settings) {
            throw new IllegalArgumentException("nicht im Test");
        }
    }
}

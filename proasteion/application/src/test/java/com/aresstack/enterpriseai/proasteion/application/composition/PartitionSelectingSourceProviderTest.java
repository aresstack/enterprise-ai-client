package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeDocumentReader;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.proasteion.application.knowledge.SourcePartitionSelection;
import com.aresstack.enterpriseai.proasteion.application.state.ApplicationStateService;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Abgehakte Spaces gelten für Indexierung und Suche, nicht für den Klick auf einen Space (wie MainframeMate). */
public class PartitionSelectingSourceProviderTest {

    private static final String SOURCE = "confluence://cf.intern/confluence";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void uncheckedSpaceWithoutHomepageStillListsItsPagesWhenClicked() throws Exception {
        SourcePartitionSelection selection =
                new SourcePartitionSelection(new ApplicationStateService(temp.newFile("state.json").toPath()));
        selection.setSelected(SOURCE, "OPS", false);
        KnowledgeSourcePort port = new PartitionSelectingSourceProvider(new Provider(), selection)
                .open(SourceAddress.of(SOURCE), SourceSettings.empty());
        BrowseKnowledgeSourceUseCase browse = new BrowseKnowledgeSourceUseCase(KnowledgeDocumentReader.direct());

        assertTrue("Indexierung und Suche lassen den abgehakten Space aus",
                browse.list(port, SourceScope.of("space:OPS")).isEmpty());
        List<KnowledgeResource> clicked = browse.list(port,
                SourceScope.builder().startPoint("space:OPS").ignoringSelection().build());
        assertEquals(1, clicked.size());
        assertEquals("Runbook", clicked.get(0).title());
    }

    /** Ein Space OPS ohne Startseite mit einer Seite; abgehakte Spaces lässt die Quelle aus. */
    private static final class Spaces implements KnowledgeSourcePort {
        @Override
        public SourceAddress sourceId() {
            return SourceAddress.of(SOURCE);
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            List<KnowledgeResource> found = new ArrayList<KnowledgeResource>();
            if (scope.startPoints().contains("space:OPS") && !scope.excludedPartitions().contains("OPS")) {
                found.add(KnowledgeResource.builder(KnowledgeResourceId.of("confluence", "cf.intern/confluence/page/200"),
                        sourceId()).title("Runbook").build());
            }
            return found;
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId resourceId) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, "nicht im Test");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
            return Collections.emptyList();
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
            return "cf.intern/confluence";
        }

        @Override
        public KnowledgeSourcePort open(SourceAddress sourceId, SourceSettings settings) {
            return new Spaces();
        }
    }
}

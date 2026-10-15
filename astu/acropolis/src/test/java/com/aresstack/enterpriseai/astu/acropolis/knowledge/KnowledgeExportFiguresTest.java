package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.source.api.CrossReferences;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.NavigationChildren;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.PageNavigationSource;
import com.aresstack.enterpriseai.source.api.PagePosition;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #132: the export tree carries the figures of every page, loaded through the source's own sign-in. */
public class KnowledgeExportFiguresTest {

    /** A navigable source with a reading view per page; images load only through {@link #loadViewImage}. */
    private static final class ViewSource implements KnowledgeSourcePort, PageNavigationSource, PageViewSource {
        final InMemoryNavigationSource pages = new InMemoryNavigationSource("wiki", 5)
                .space("DEV", "Development").page("DEV", "a", "A").page("a", "b", "B");
        final Map<String, byte[]> images = new HashMap<String, byte[]>();
        final List<String> imageCalls = new ArrayList<String>();
        final Map<String, PageView> views = new HashMap<String, PageView>();

        @Override
        public SourceAddress sourceId() {
            return pages.sourceId();
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return pages.discover(scope);
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId id) throws KnowledgeSourceException {
            KnowledgeDocument plain = pages.load(id);
            return KnowledgeDocument.of(plain.resource(), plain.text() + "\n\nEinleitung der Seite.\n\nSchluss.");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId id) {
            return pages.discoverLinks(id);
        }

        @Override
        public PagePosition position(KnowledgeResourceId page) throws KnowledgeSourceException {
            return pages.position(page);
        }

        @Override
        public NavigationChildren children(NavigationNode parent, String cursor) throws KnowledgeSourceException {
            return pages.children(parent, cursor);
        }

        @Override
        public CrossReferences crossReferences(KnowledgeResourceId page) throws KnowledgeSourceException {
            return pages.crossReferences(page);
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) throws KnowledgeSourceException {
            PageView view = views.get(page.value());
            if (view == null) {
                throw new KnowledgeSourceException(Kind.UNSUPPORTED, "keine Ansicht");
            }
            return view;
        }

        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            imageCalls.add(src);
            byte[] bytes = images.get(src);
            if (bytes == null) {
                throw new KnowledgeSourceException(Kind.ACCESS_DENIED, "kein Zugriff");
            }
            return bytes;
        }
    }

    private final KnowledgeExportUseCase export = new KnowledgeExportUseCase(new PageNavigationUseCase());

    private static PageView view(String... srcs) {
        StringBuilder html = new StringBuilder("<p>Einleitung der Seite.</p>");
        List<PageView.Image> images = new ArrayList<PageView.Image>();
        for (String src : srcs) {
            html.append("<img src=\"").append(src).append("\">");
            images.add(new PageView.Image(src, "Bild " + src, null));
        }
        return new PageView(html.append("<p>Schluss.</p>").toString(), "https://wiki/", images);
    }

    @Test
    public void figuresOfEveryPageOfTheSubtreeComeWithTheirBytes() throws Exception {
        ViewSource source = new ViewSource();
        source.views.put(source.pages.idOf("a").value(), view("https://wiki/a.png"));
        source.views.put(source.pages.idOf("b").value(), view("https://wiki/b.png"));
        source.images.put("https://wiki/a.png", new byte[]{1, 2});
        source.images.put("https://wiki/b.png", new byte[]{3});
        NavigationNode a = source.position(source.pages.idOf("a")).page();

        ExportDocumentNode tree = export.subtree(source, a, null, null);

        assertEquals(1, tree.images().size());
        assertArrayEquals(new byte[]{1, 2}, tree.images().get(0).bytes());
        assertTrue(tree.text(), tree.text().contains("Einleitung der Seite.\n\n![Bild https://wiki/a.png]"
                + "(<https://wiki/a.png>)\n\nSchluss."));
        ExportDocumentNode b = tree.children().get(0);
        assertEquals("the child page keeps its own figure", "https://wiki/b.png", b.images().get(0).src());
        assertFalse("the indexable text stays without images", tree.document().text().contains("!["));
        assertEquals(Arrays.asList("https://wiki/a.png", "https://wiki/b.png"), source.imageCalls);
    }

    @Test
    public void aFigureThatCannotBeLoadedStaysWithItsReason() throws Exception {
        ViewSource source = new ViewSource();
        source.views.put(source.pages.idOf("a").value(), view("https://wiki/secret.png"));

        ExportDocumentNode page = export.page(source, source.pages.idOf("a"), "A", null, null);

        ExportImage image = page.images().get(0);
        assertFalse(image.ok());
        assertEquals("kein Zugriff", image.failure());
    }

    @Test
    public void withoutReadingViewTheTextIsTheExport() throws Exception {
        ViewSource source = new ViewSource();
        ExportDocumentNode page = export.page(source, source.pages.idOf("a"), "A", null, null);
        assertEquals(Collections.<ExportImage>emptyList(), page.images());
        assertEquals(page.document().text(), page.text());
    }
}

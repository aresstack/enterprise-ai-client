package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.testing.InMemoryKnowledgeIndex;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.BrowseKnowledgeSourceUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.IndexKnowledgeUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgePrefetchService;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceCatalog;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeSourceRegistration;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.ResolveKnowledgeAddressUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.SearchKnowledgeSourcesUseCase;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunker;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeChunkingPolicy;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.embedding.api.testing.DeterministicEmbeddingPort;
import com.aresstack.enterpriseai.proasteion.exedra.knowledge.KnowledgeReadingController;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.CachingMermaidImageRenderer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.PageView;
import com.aresstack.enterpriseai.source.api.PageViewSource;
import com.aresstack.enterpriseai.source.api.AddressableKnowledgeSource;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.After;
import org.junit.Test;

import javax.swing.SwingUtilities;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.html.HTMLDocument;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Issue #94: a link in the reading view only reports that it was activated; the reader opens pages of the same source
 * itself and leaves everything else to the browser. Anchors of the page still scroll inside it.
 */
public class ReaderPageLinksTest {

    private static final SourceAddress SOURCE = SourceAddress.of("wiki");
    private static final String BASE = "https://wiki.invalid/confluence/";
    private static final String PAGE = "confluence:wiki.invalid/confluence/page/7";
    private static final String HTML = "<h2 id=\"oben\">Oben</h2><p><a href=\"#unten\">Anker</a> "
            + "<a href=\"pages/viewpage.action?pageId=8\">Relativ</a></p>"
            + "<p>Text</p><h2 id=\"unten\">Unten</h2><p>Ende</p>";

    private final List<KnowledgePrefetchService> services = new ArrayList<KnowledgePrefetchService>();

    @After
    public void tearDown() {
        for (KnowledgePrefetchService service : services) {
            service.shutdown();
        }
    }

    @Test
    public void theViewReportsLinksResolvedAgainstThePageBaseAndKeepsAnchorsToItself() throws Exception {
        final List<String> browser = new ArrayList<String>();
        final List<String> reported = new ArrayList<String>();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), browser::add,
                    CachingMermaidImageRenderer.forChat());
            final ReaderLink page = new ReaderLink(SOURCE.value(), PAGE, "Seite", "", "");
            reader.setListener(new KnowledgeReaderPanel.Listener() {
                @Override
                public void linkOpened(ReaderLink link, boolean pin) {
                }

                @Override
                public void pageLinkActivated(ReaderLink from, String address, String text, Runnable external) {
                    assertEquals(page, from);
                    reported.add(address);
                }
            });
            reader.open(page, true);
            reader.showHtml(page.key(), HTML, BASE, Collections.<ReaderImage>emptyList(), (image, done) -> { });
            ReaderHtmlView view = reader.htmlView(page.key());

            activate(view, "#unten");
            assertTrue("Anker bleibt in der Seite: " + reported, reported.isEmpty());
            activate(view, "pages/viewpage.action?pageId=8");
            activate(view, "https://example.org/extern");
            activate(view, "mailto:someone@example.org");
            assertEquals(java.util.Arrays.asList(BASE + "pages/viewpage.action?pageId=8",
                    "https://example.org/extern", "mailto:someone@example.org"), reported);
            assertTrue("der Lesebereich entscheidet, nicht die Ansicht: " + browser, browser.isEmpty());
        });
    }

    @Test
    public void withoutAListenerEveryLinkGoesToTheBrowser() throws Exception {
        final List<String> browser = new ArrayList<String>();
        run(() -> {
            KnowledgeReaderPanel reader = new KnowledgeReaderPanel(ComicPalette.defaultPalette(), browser::add,
                    CachingMermaidImageRenderer.forChat());
            ReaderLink page = new ReaderLink(SOURCE.value(), PAGE, "Seite", "", "");
            reader.open(page, true);
            reader.showHtml(page.key(), HTML, BASE, Collections.<ReaderImage>emptyList(), (image, done) -> { });
            activate(reader.htmlView(page.key()), "https://example.org/extern");
            assertEquals(Collections.singletonList("https://example.org/extern"), browser);
        });
    }

    @Test
    public void linksTheSourceResolvesOpenInTheReaderAndTheRestInTheBrowser() throws Exception {
        final LinkingSource source = new LinkingSource();
        final List<String> browser = new ArrayList<String>();
        run(() -> {
            ChatWorkspacePanel workspace = workspace();
            KnowledgeReadingController controller = controller(workspace, source);
            KnowledgeReaderPanel reader = workspace.reader();
            ReaderLink page = new ReaderLink(SOURCE.value(), PAGE, "Seite", "", "");
            controller.open(page, true);

            controller.pageLinkActivated(page, BASE + "pages/viewpage.action?pageId=8#unten", "Zielseite",
                    () -> browser.add("intern"));
            String target = new ReaderLink(SOURCE.value(), "confluence:wiki.invalid/confluence/page/8", "", "", "")
                    .key();
            assertTrue("kein Browser für die eigene Seite: " + browser, browser.isEmpty());
            assertEquals("Zielseite im Lesebereich", target, reader.activeKey());
            assertTrue("als Vorschau", !reader.isPinned(target));
            assertTrue("Seite angefordert: " + source.viewed, source.viewed.contains(
                    "confluence:wiki.invalid/confluence/page/8"));

            controller.pageLinkActivated(page, "https://example.org/extern", "", () -> browser.add("extern"));
            controller.pageLinkActivated(page, "mailto:someone@example.org", "", () -> browser.add("mail"));
            controller.pageLinkActivated(page, BASE + "pages/viewpage.action?pageId=unbekannt", "",
                    () -> browser.add("unbekannt"));
            controller.pageLinkActivated(page, BASE + "kaputt", "", () -> browser.add("fehler"));
            assertEquals(java.util.Arrays.asList("extern", "mail", "unbekannt", "fehler"), browser);
            assertEquals("die Zielseite bleibt aktiv", target, reader.activeKey());
        });
    }

    @Test
    public void theFragmentOfALinkIsTheAnchorToScrollTo() {
        assertEquals("unten", KnowledgeReadingController.fragmentOf(BASE + "x?pageId=8#unten"));
        assertEquals("Größe", KnowledgeReadingController.fragmentOf(BASE + "x#Gr%C3%B6%C3%9Fe"));
        assertEquals("", KnowledgeReadingController.fragmentOf(BASE + "x#"));
        assertEquals("", KnowledgeReadingController.fragmentOf(BASE + "x"));
    }

    // ------------------------------------------------------------------ Aufbau

    /** Fires the event Swing's link controller fires: the address resolved against the document base. */
    private static void activate(ReaderHtmlView view, String href) {
        URL url;
        try {
            URL base = ((HTMLDocument) view.getDocument()).getBase();
            url = base == null ? new URL(href) : new URL(base, href);
        } catch (MalformedURLException e) {
            url = null;
        }
        view.fireHyperlinkUpdate(new HyperlinkEvent(view, HyperlinkEvent.EventType.ACTIVATED, url, href, null));
    }

    private ChatWorkspacePanel workspace() {
        ChatShellModel model = new ChatShellModel(() -> 0L);
        ChatShellPanel shell = new ChatShellPanel(model, new ChatShellActions() {
            @Override
            public void sendRequested(String text, boolean ragEnabled) {
            }

            @Override
            public void stopRequested() {
            }
        }, ComicPalette.defaultPalette(), BubblePalette.windowsPhoneInspired());
        return new ChatWorkspacePanel(new ShellModeModel(false), shell, null, ComicPalette.defaultPalette());
    }

    private KnowledgeReadingController controller(ChatWorkspacePanel workspace, LinkingSource source) {
        DeterministicEmbeddingPort embeddings = DeterministicEmbeddingPort.withDimension(8);
        InMemoryKnowledgeIndex index = new InMemoryKnowledgeIndex();
        IndexKnowledgeUseCase indexing = new IndexKnowledgeUseCase(index, embeddings, embeddings.modelIdentity(),
                new KnowledgeChunker(KnowledgeChunkingPolicy.defaults()));
        KnowledgePrefetchService prefetch = new KnowledgePrefetchService(indexing, (owner, port, id) -> port.load(id),
                () -> KnowledgePrefetchService.Settings.of(5, 1, 10));
        services.add(prefetch);
        final KnowledgeSourceRegistration registration = new KnowledgeSourceRegistration(source,
                SourceScope.of("1"));
        KnowledgeReadingController controller = new KnowledgeReadingController(workspace,
                () -> Collections.singletonList(registration), id -> SOURCE.value().equals(id) ? registration : null,
                new SearchKnowledgeSourcesUseCase(index, embeddings::modelIdentity, Runnable::run),
                new ResolveKnowledgeAddressUseCase(() -> KnowledgeSourceCatalog.of(registration)),
                new BrowseKnowledgeSourceUseCase((owner, port, id) -> port.load(id)), prefetch, null, null,
                Runnable::run, Runnable::run);
        controller.attach();
        return controller;
    }

    private static void run(Runnable body) throws Exception {
        try {
            SwingUtilities.invokeAndWait(body);
        } catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Error) {
                throw (Error) ex.getCause();
            }
            if (ex.getCause() instanceof RuntimeException) {
                throw (RuntimeException) ex.getCause();
            }
            throw ex;
        }
    }

    /**
     * A source with a reading view that knows its own page addresses {@code <BASE>pages/viewpage.action?pageId=<n>};
     * {@code <BASE>kaputt} fails like a source that cannot ask.
     */
    private static final class LinkingSource implements KnowledgeSourcePort, PageViewSource,
            AddressableKnowledgeSource {
        final List<String> viewed = new ArrayList<String>();

        @Override
        public SourceAddress sourceId() {
            return SOURCE;
        }

        @Override
        public List<KnowledgeResource> discover(SourceScope scope) {
            return Collections.emptyList();
        }

        @Override
        public KnowledgeDocument load(KnowledgeResourceId id) {
            return KnowledgeDocument.of(KnowledgeResource.builder(id, SOURCE).title("Seite").build(), "# Seite\n\nText");
        }

        @Override
        public List<SourceLink> discoverLinks(KnowledgeResourceId id) {
            return Collections.emptyList();
        }

        @Override
        public PageView pageView(KnowledgeResourceId page) {
            viewed.add(page.value());
            return new PageView(HTML, BASE, null);
        }

        @Override
        public byte[] loadViewImage(String src) throws KnowledgeSourceException {
            throw new KnowledgeSourceException(KnowledgeSourceException.Kind.NOT_FOUND, src);
        }

        @Override
        public java.util.regex.Pattern addressPattern() {
            return java.util.regex.Pattern.compile("https?://\\S+");
        }

        @Override
        public KnowledgeResourceId resolveAddress(String address) throws KnowledgeSourceException {
            if (address.equals(BASE + "kaputt")) {
                throw new KnowledgeSourceException(KnowledgeSourceException.Kind.UNAVAILABLE, "nicht erreichbar");
            }
            String prefix = BASE + "pages/viewpage.action?pageId=";
            if (!address.startsWith(prefix)) {
                return null;
            }
            String id = address.substring(prefix.length()).replaceAll("#.*", "");
            return id.matches("[0-9]+") ? KnowledgeResourceId.of("confluence:wiki.invalid/confluence/page/" + id)
                    : null;
        }
    }
}

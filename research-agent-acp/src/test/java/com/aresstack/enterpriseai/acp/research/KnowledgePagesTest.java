package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.acquire.WebAcquisitionText;
import com.aresstack.enterpriseai.research.runtime.loop.ToolInvoker;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@code knowledge_search} im Textformat von {@code KnowledgeSearchTool} (renderHit: Index-Treffer mit
 * {@code passage:}; renderLive: Live-Treffer „(not read yet)“; einer ohne {@code url:}): die echte URL kommt beim
 * Agenten an, Metadaten bleiben aus dem Auszug, und die Treffer zählen einen Host.
 */
public class KnowledgePagesTest {

    private static final String SEARCH_RESULT = "Query: Betriebshandbuch Batch\n"
            + "Note: source wiki could not be searched (timeout).\n"
            + "\n[1] Betriebshandbuch – Nachtlauf\n"
            + "id: confluence:47110815\n"
            + "url: https://cf.intern/confluence/pages/viewpage.action?pageId=47110815\n"
            + "passage: confluence:47110815#p3@0a1b2c3d-4e5f6071\n"
            + "source: confluence\n"
            + "Der Nachtlauf startet um 22 Uhr\nund endet vor 6 Uhr.\n"
            + "\n[2] Störungen\n"
            + "id: confluence:47110999\n"
            + "url: https://cf.intern/confluence/pages/viewpage.action?pageId=47110999\n"
            + "source: confluence (not read yet)\n"
            + "Abbruch bei fehlender Freigabe.\n"
            + "\n[3] Notizen\n"
            + "id: local:notizen.txt\n"
            + "source: local\n"
            + "Ohne eigene Adresse.\n"
            + "\nRead a hit completely with knowledge_read — example: id=\"confluence:47110815\"";

    @Test
    public void parsesMetadataAndKeepsItOutOfTheSnippet() {
        List<KnowledgePages.Hit> hits = KnowledgePages.parseHits(SEARCH_RESULT);

        assertEquals(3, hits.size());
        KnowledgePages.Hit first = hits.get(0);
        assertEquals("confluence:47110815", first.id);
        assertEquals("Betriebshandbuch – Nachtlauf", first.title);
        assertEquals("https://cf.intern/confluence/pages/viewpage.action?pageId=47110815", first.url);
        assertEquals("confluence:47110815#p3@0a1b2c3d-4e5f6071", first.passage);
        assertEquals("confluence", first.source);
        assertEquals("Der Nachtlauf startet um 22 Uhr und endet vor 6 Uhr.", first.snippet);

        KnowledgePages.Hit live = hits.get(1);
        assertEquals("https://cf.intern/confluence/pages/viewpage.action?pageId=47110999", live.url);
        assertEquals("", live.passage);
        assertEquals("Abbruch bei fehlender Freigabe.", live.snippet);

        assertEquals("", hits.get(2).url);
        for (KnowledgePages.Hit hit : hits) {
            assertFalse(hit.snippet, hit.snippet.contains("url:"));
            assertFalse(hit.snippet, hit.snippet.contains("passage:"));
            assertFalse(hit.snippet, hit.snippet.contains("source:"));
            assertFalse(hit.snippet, hit.snippet.contains("Read a hit"));
        }
    }

    @Test
    public void openAndAcceptUseTheRealUrlAndReadByTheOpaqueId() throws Exception {
        final List<String> calls = new ArrayList<String>();
        ToolInvoker host = new ToolInvoker() {
            @Override
            public String call(String tool, Map<String, Object> args) {
                calls.add(tool + " " + args);
                if (KnowledgePages.SEARCH_TOOL.equals(tool)) {
                    return SEARCH_RESULT;
                }
                if (KnowledgePages.READ_TOOL.equals(tool)) {
                    return "Seitentext";
                }
                return "ok";
            }
        };
        KnowledgePages pages = new KnowledgePages(host);

        List<KnowledgePages.Hit> hits = pages.search("Betriebshandbuch Batch", 5);
        String realUrl = "https://cf.intern/confluence/pages/viewpage.action?pageId=47110815";
        assertEquals(realUrl, hits.get(0).url);
        assertEquals("knowledge:local:notizen.txt", hits.get(2).url);

        Map<String, Object> open = new java.util.LinkedHashMap<String, Object>();
        open.put("url", realUrl);
        String page = pages.call("web_open", open);
        assertEquals(realUrl, WebAcquisitionText.finalUrlOf(page));
        assertEquals("cf.intern", WebAcquisitionText.hostOf(WebAcquisitionText.finalUrlOf(page)));
        assertTrue(calls.get(1), calls.get(1).startsWith(KnowledgePages.READ_TOOL + " {id=confluence:47110815}"));

        pages.acceptance().accept(WebAcquisitionText.field(page, "capture_id"));
        String accept = calls.get(2);
        assertTrue(accept, accept.startsWith("source_accept "));
        assertTrue(accept, accept.contains("url=" + realUrl));
    }

    @Test
    public void addressesWithoutAHostCountUnderTheirScheme() {
        assertEquals("cf.intern", WebAcquisitionText.hostOf("https://cf.intern/confluence/x"));
        assertEquals("knowledge", WebAcquisitionText.hostOf("knowledge:local:notizen.txt"));
        assertEquals("file", WebAcquisitionText.hostOf("file:///C:/daten/notizen.txt"));
        assertEquals("", WebAcquisitionText.hostOf("C:\\daten\\notizen.txt"));
    }
}

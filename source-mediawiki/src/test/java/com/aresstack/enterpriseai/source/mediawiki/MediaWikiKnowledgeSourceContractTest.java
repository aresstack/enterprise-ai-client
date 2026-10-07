package com.aresstack.enterpriseai.source.mediawiki;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.testing.KnowledgeSourceContractTest;

/** Der MediaWiki-Adapter erfüllt den gemeinsamen Source-Vertrag gegen die Fake-{@code api.php}. */
public class MediaWikiKnowledgeSourceContractTest extends KnowledgeSourceContractTest {

    static FakeMediaWikiTransport sampleWiki() {
        FakeMediaWikiTransport wiki = new FakeMediaWikiTransport();
        wiki.page("Hauptseite", "<p>Willkommen im <a href=\"/wiki/Handbuch\">Handbuch</a>.</p>", 1, 101,
                "Handbuch", "Glossar", "Rotlink", "Alter Name");
        wiki.page("Handbuch", "<h2>Installation</h2><p>Die Installation erfolgt per Gradle.</p>", 2, 102,
                "Hauptseite", "Glossar");
        wiki.page("Glossar", "<dl><dt>RAG</dt><dd>Retrieval Augmented Generation</dd></dl>", 3, 103);
        wiki.page("Neuer Name", "<p>Umbenannte Seite.</p>", 4, 104);
        wiki.redirects.put("Alter Name", "Neuer Name");
        return wiki;
    }

    @Override
    protected KnowledgeSourcePort createSource() {
        return new MediaWikiKnowledgeSource(KnowledgeSourceId.of("wiki-intranet"),
                MediaWikiSiteConfig.builder("intranet", "https://wiki.example/w").build(), sampleWiki(),
                MediaWikiCredentialsProvider.anonymous());
    }

    @Override
    protected String linkedStartPoint() {
        return "Hauptseite";
    }

    @Override
    protected String missingStartPoint() {
        return "Gibt es nicht";
    }

    @Override
    protected KnowledgeResourceId unknownResourceId() {
        return KnowledgeResourceId.of("wiki:intranet/Gibt_es_nicht");
    }

    @Override
    protected String searchTextWithHits() {
        return "Gradle";
    }
}

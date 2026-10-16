package com.aresstack.enterpriseai.astu.acropolis.source;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeDocument;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;
import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;
import com.aresstack.enterpriseai.astu.source.KnowledgeSourceType;
import com.aresstack.enterpriseai.astu.source.SourceDefinition;
import com.aresstack.enterpriseai.astu.source.SourceSettingField;
import com.aresstack.enterpriseai.astu.source.SourceSettings;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourcePort;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceProvider;
import com.aresstack.enterpriseai.source.api.SourceLink;
import com.aresstack.enterpriseai.source.api.SourceScope;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** "Verbindung testen" (#137): generic over the provider port, at most one resource, adapter errors as messages. */
public class KnowledgeSourceConnectionCheckTest {

    private final List<SourceScope> scopes = new ArrayList<SourceScope>();
    private KnowledgeSourceException failure;
    private List<KnowledgeResource> found = Collections.emptyList();

    @Test
    public void successAsksForAtMostOneResource() {
        SourceConnectionCheck check = management().checkConnection(draft("https://repo.example.org/x"));

        assertTrue(check.message(), check.successful());
        assertEquals(1, scopes.size());
        assertEquals(1, scopes.get(0).maxResources());
        assertEquals(Collections.singletonList("src"), scopes.get(0).startPoints());
        assertTrue(check.message().contains("nichts gefunden"));
    }

    @Test
    public void adapterErrorBecomesFailureWithKind() {
        failure = new KnowledgeSourceException(KnowledgeSourceException.Kind.ACCESS_DENIED, "HTTP 401");

        SourceConnectionCheck check = management().checkConnection(draft("https://repo.example.org/x"));

        assertFalse(check.successful());
        assertTrue(check.message(), check.message().startsWith("Anmeldung fehlgeschlagen"));
        assertTrue(check.message(), check.message().endsWith("HTTP 401"));
    }

    @Test
    public void invalidSettingsNeverReachTheSource() {
        SourceConnectionCheck check = management().checkConnection(draft(""));

        assertFalse(check.successful());
        assertTrue(check.message(), check.message().startsWith("Einstellungen unvollständig"));
        assertTrue(scopes.isEmpty());
    }

    private KnowledgeSourceManagement management() {
        return new KnowledgeSourceManagement(Collections.<KnowledgeSourceProvider>singletonList(new Provider()), null,
                null);
    }

    private static SourceDefinition draft(String url) {
        java.util.Map<String, String> values = new java.util.HashMap<String, String>();
        values.put("url", url);
        return new SourceDefinition("", "probe", true, SourceSettings.of(values));
    }

    private final class Provider implements KnowledgeSourceProvider {

        private final KnowledgeSourceType type = KnowledgeSourceType.builder("probe", "probe", "Probe")
                .summaryKey("url").field(SourceSettingField.text("url", "URL", "").required()).build();

        @Override
        public KnowledgeSourceType type() {
            return type;
        }

        @Override
        public List<String> validate(SourceSettings settings) {
            String url = settings.get("url");
            return url == null || url.isEmpty() ? Collections.singletonList("url: fehlt")
                    : Collections.<String>emptyList();
        }

        @Override
        public SourceScope scope(SourceSettings settings) {
            return SourceScope.builder().startPoint("src").maxDepth(5).maxResources(500).build();
        }

        @Override
        public KnowledgeSourcePort open(final SourceAddress sourceId, SourceSettings settings) {
            return new KnowledgeSourcePort() {
                @Override
                public SourceAddress sourceId() {
                    return sourceId;
                }

                @Override
                public List<KnowledgeResource> discover(SourceScope scope) throws KnowledgeSourceException {
                    scopes.add(scope);
                    if (failure != null) {
                        throw failure;
                    }
                    return found;
                }

                @Override
                public KnowledgeDocument load(KnowledgeResourceId resourceId) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public List<SourceLink> discoverLinks(KnowledgeResourceId resourceId) {
                    return Collections.emptyList();
                }
            };
        }
    }
}

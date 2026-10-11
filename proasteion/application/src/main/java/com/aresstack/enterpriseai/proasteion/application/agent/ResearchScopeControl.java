package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.domain.scope.ResearchScopeDraft;
import com.aresstack.enterpriseai.research.domain.scope.ScopeAnchor;
import com.aresstack.enterpriseai.research.domain.scope.ScopeFenceEvaluator;
import com.aresstack.enterpriseai.research.scope.ScopeSweepConfiguration;
import com.aresstack.enterpriseai.research.scope.ScopeSweepPlanAssembler;
import com.aresstack.enterpriseai.research.scope.ScopeSweepService;
import com.aresstack.enterpriseai.research.scope.SearchScopeGate;
import com.aresstack.enterpriseai.research.store.ScopeAnchorVectorIndex;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Der Weidezaun als Filter des autonomen Recherche-Laufs (askai-java8 arch {@code ResearchAgentSession}
 * {@code searchScopeBegin/Evaluate/End}, SC1): {@code begin} friert den wirksamen Zaun eines Laufs ein (Konzept →
 * IN-Posten, Ausschlüsse → OUT-Posten), {@code evaluate} misst Trefferauszüge semantisch dagegen
 * ({@link SearchScopeGate}: nur klar Ausgeschlossenes fällt heraus), {@code end} gibt den Schnappschuss frei. Mit
 * OUT-Posten und kaputtem Embedding antwortet der Host {@code UNAVAILABLE}: der Lauf endet dann typisiert, er sucht
 * nie still ohne Zaun weiter.
 */
public final class ResearchScopeControl {

    /** Das Embedding der Zaunposten und Trefferauszüge (in der Composition Root über den Embedding-Port). */
    public interface Embedder {
        /** Fingerabdruck des Modells; {@code null}, solange keins eingestellt ist. */
        String fingerprint();

        List<float[]> embed(List<String> texts);
    }

    private static final Logger LOG = Logger.getLogger(ResearchScopeControl.class.getName());
    private static final String VECTOR_FILE = "scope-anchor-vectors.json";

    private static final class Snapshot {
        final List<ScopeFenceEvaluator.AnchorVector> anchors;
        final Map<String, String> labels;
        final ScopeSweepService.SweepEmbedder embedder;

        Snapshot(List<ScopeFenceEvaluator.AnchorVector> anchors, Map<String, String> labels,
                 ScopeSweepService.SweepEmbedder embedder) {
            this.anchors = anchors;
            this.labels = labels;
            this.embedder = embedder;
        }
    }

    private final ResearchAgentHost host;
    private final Map<String, Snapshot> snapshots =
            Collections.synchronizedMap(new LinkedHashMap<String, Snapshot>());
    private volatile Embedder embedder;

    ResearchScopeControl(ResearchAgentHost host) {
        this.host = host;
    }

    void setEmbedder(Embedder embedder) {
        this.embedder = embedder;
    }

    /** Der wirksame Zaun als Posten: Facetten des Entwurfs, Konzeptkarten (IN), Ausschlüsse (OUT). */
    List<ScopeAnchor> fenceAnchors() {
        ResearchScopeDraft draft = host.currentScopeDraft();
        List<ScopeAnchor> concept = ConceptAnchorProjection.anchorsOf(host.conceptDocumentJson(), draft);
        List<ScopeAnchor> combined = new ArrayList<ScopeAnchor>(ScopeSweepPlanAssembler.combinedAnchors(draft,
                concept));
        combined.addAll(ScopeSweepPlanAssembler.combinedAnchors(ResearchScopeDraft.empty(),
                ConceptAnchorProjection.exclusionAnchorsOf(draft, host.blacklistTermsForDisplay())));
        return combined;
    }

    String begin() {
        List<ScopeAnchor> anchors = fenceAnchors();
        int out = 0;
        int in = 0;
        Map<String, String> labels = new LinkedHashMap<String, String>();
        for (ScopeAnchor anchor : anchors) {
            labels.put(anchor.getAnchorId(), anchor.getSemanticText());
            if (anchor.getMembership() == ScopeAnchor.Membership.OUT) {
                out++;
            } else if (anchor.getMembership() == ScopeAnchor.Membership.IN) {
                in++;
            }
        }
        if (out == 0 && in == 0) {
            LOG.info("[research-scope] search-scope INACTIVE (no anchors)");
            return "INACTIVE outAnchors=0";
        }
        final Embedder current = embedder;
        final String fingerprint = current == null ? null : current.fingerprint();
        if (fingerprint == null) {
            if (out > 0) {
                LOG.info("[research-scope] search-scope UNAVAILABLE (no embedding model, outAnchors=" + out + ")");
                return "UNAVAILABLE no embedding model";
            }
            return "INACTIVE inAffinity unavailable";
        }
        ScopeSweepService.SweepEmbedder sweep = new ScopeSweepService.SweepEmbedder() {
            @Override
            public String modelFingerprint() {
                return fingerprint;
            }

            @Override
            public List<float[]> embed(List<String> texts) {
                return current.embed(texts);
            }
        };
        List<ScopeFenceEvaluator.AnchorVector> vectors;
        try {
            vectors = new ScopeAnchorVectorIndex(new File(host.projectDirectory(), VECTOR_FILE))
                    .vectorsFor(anchors, fingerprint, new ScopeAnchorVectorIndex.AnchorEmbedder() {
                        @Override
                        public List<float[]> embed(List<String> semanticTexts) {
                            return current.embed(semanticTexts);
                        }
                    });
        } catch (IOException | RuntimeException failed) {
            LOG.info("[research-scope] search-scope anchors failed: " + failed.getMessage());
            return out > 0 ? "UNAVAILABLE anchor embedding failed" : "INACTIVE inAffinity unavailable";
        }
        String handle = "ss-" + UUID.randomUUID();
        snapshots.put(handle, new Snapshot(vectors, labels, sweep));
        String description = "inAnchors=" + in + " outAnchors=" + out + " outFilter=" + (out > 0)
                + " inAffinity=" + (in > 0);
        LOG.info("[research-scope] search-scope snapshot " + description);
        return "ACTIVE handle=" + handle + " " + description;
    }

    String evaluate(String handle, String lane, String itemsJson) {
        Snapshot snapshot = handle == null ? null : snapshots.get(handle);
        if (snapshot == null) {
            return "UNKNOWN_HANDLE";
        }
        List<SearchScopeGate.Item> items = new ArrayList<SearchScopeGate.Item>();
        try {
            for (JsonElement element : JsonParser.parseString(itemsJson == null ? "" : itemsJson).getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                items.add(new SearchScopeGate.Item(object.has("id") ? object.get("id").getAsString() : "",
                        object.has("text") ? object.get("text").getAsString() : ""));
            }
        } catch (RuntimeException malformed) {
            return "UNAVAILABLE malformed items_json";
        }
        List<SearchScopeGate.Decision> decisions;
        try {
            decisions = SearchScopeGate.evaluate(items, snapshot.anchors, snapshot.labels, snapshot.embedder,
                    ScopeSweepConfiguration.defaults().fenceThresholds);
        } catch (RuntimeException infrastructure) {
            LOG.info("[research-scope] search-scope " + lane + " -> UNAVAILABLE (" + infrastructure + ")");
            return "UNAVAILABLE " + infrastructure.getMessage();
        }
        int out = 0;
        StringBuilder reply = new StringBuilder();
        for (SearchScopeGate.Decision decision : decisions) {
            reply.append('\n').append(decision.verdict.name());
            if (decision.verdict == SearchScopeGate.Verdict.OUT) {
                out++;
                reply.append(" nearest=\"").append(decision.nearestOutLabel).append('"');
                LOG.info("[research-scope] skip " + lane + " nearest=\"" + decision.nearestOutLabel + "\" id="
                        + decision.id);
            } else if (decision.nearIn) {
                reply.append(" in=NEAR");
                if (decision.nearestInLabel != null) {
                    reply.append(" nearest_in=\"").append(decision.nearestInLabel).append('"');
                }
            }
            reply.append(" id=").append(decision.id);
        }
        LOG.info("[research-scope] search-scope " + lane + " items=" + decisions.size() + " out=" + out);
        return "EVALUATED items=" + decisions.size() + reply;
    }

    String end(String handle) {
        if (handle != null) {
            snapshots.remove(handle);
        }
        return "ENDED";
    }
}

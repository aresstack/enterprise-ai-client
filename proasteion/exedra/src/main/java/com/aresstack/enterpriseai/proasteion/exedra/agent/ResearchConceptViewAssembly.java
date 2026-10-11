package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchConceptEditing;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ConceptPaperView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ConceptTreeView;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Baut den Drawer-Reiter „Konzept“ des Agent-Modus, aus askai-java8 (arch) {@code ResearchBriefViewContribution}
 * übernommen: das Konzeptpapier ist die Arbeitsfläche des Scopings und zugleich der direkte Editor des Nutzers.
 * Manuelle Änderungen und Baum-Gesten laufen über {@link ResearchConceptEditing} im Kompositionspunkt (dieselbe
 * prüfende Pipeline wie jeder Agentenschritt); ein Schnappschuss je Aktualisierung, direkt aus dem Konzeptdienst des
 * aktuellen Projekts.
 */
public final class ResearchConceptViewAssembly {

    private ResearchConceptViewAssembly() {
    }

    public static ConceptPaperView create(final ResearchAgentHost host, final Executor uiExecutor) {
        final ResearchConceptEditing concept = new ResearchConceptEditing(host);
        final ConceptPaperView view = new ConceptPaperView();
        view.setEditActions(new ConceptPaperView.SaveHandler() {
            @Override
            public String save(String documentJson, long expectedRevision) {
                return concept.save(documentJson, expectedRevision);
            }
        }, new ConceptPaperView.HistoryReader() {
            @Override
            public String content(long revision) {
                return concept.historyContent(revision);
            }
        }, new ConceptPaperView.RestoreHandler() {
            @Override
            public String restore(long revision) {
                return concept.restore(revision);
            }
        });
        // Tree-editor gestures, ID-HARDENED: every action travels as (epoch, nodeId) and the service resolves the
        // CURRENT path inside the same atomic operation — a stale epoch or a vanished id aborts honestly.
        view.setTreeActions(new ConceptTreeView.Actions() {
            @Override
            public String rename(String epoch, String nodeId, String newName) {
                return concept.rename(epoch, nodeId, newName);
            }

            @Override
            public String deleteLeafOrTerminal(String epoch, String nodeId) {
                return concept.deleteLeafOrTerminal(epoch, nodeId);
            }

            @Override
            public String deleteBranch(String epoch, String nodeId) {
                return concept.deleteBranch(epoch, nodeId);
            }

            @Override
            public String addChild(String epoch, String parentNodeId, String insertBeforeNodeId, String name) {
                return concept.addChild(epoch, parentNodeId, insertBeforeNodeId, name);
            }

            @Override
            public String moveLeaf(String epoch, String sourceNodeId, String targetParentNodeId,
                                   String insertBeforeNodeId) {
                return concept.moveLeaf(epoch, sourceNodeId, targetParentNodeId, insertBeforeNodeId);
            }
        }, new ConceptTreeView.BlacklistSource() {
            @Override
            public List<String> terms() {
                return concept.blacklistTerms();
            }
        }, new ConceptTreeView.IdentityContext() {
            @Override
            public String epoch() {
                return concept.epoch();
            }

            @Override
            public String idAt(List<String> path) {
                return concept.idAt(path);
            }
        });
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                // ONE atomic snapshot per refresh (JSON + revision from the same state); the store stays the truth.
                ResearchConceptEditing.Snapshot snapshot = concept.snapshot();
                final ConceptPaperView.ConceptProjection projection = snapshot == null ? null
                        : new ConceptPaperView.ConceptProjection(snapshot.isReadable(), snapshot.diagnosticText(),
                        snapshot.workingRevision(), snapshot.prettyJson());
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        view.render(projection);
                    }
                });
            }
        };
        // The manual ⟳ runs exactly the same re-read as every listener — never a second path.
        view.setRefreshAction(refresh);
        // Every applied concept edit and every new project reaches the host's state listeners.
        concept.addStateListener(refresh);
        refresh.run();
        return view;
    }
}

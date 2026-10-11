package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ConceptPaperView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ConceptTreeView;
import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.ConceptProjection;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Baut den Drawer-Reiter „Konzept“ des Agent-Modus, aus askai-java8 (arch) {@code ResearchBriefViewContribution}
 * übernommen: das Konzeptpapier ist die Arbeitsfläche des Scopings und zugleich der direkte Editor des Nutzers.
 * Manuelle Änderungen gehen durch dieselbe prüfende Pipeline wie jeder Agentenschritt
 * ({@code ConceptBranchService.replaceDocument}), Baum-Gesten laufen über (Epoche, Knoten-ID). Ein Schnappschuss je
 * Aktualisierung, direkt aus dem Konzeptdienst des aktuellen Projekts.
 */
public final class ResearchConceptViewAssembly {

    private static final String NO_SERVICE = "This session has no concept service.";

    private ResearchConceptViewAssembly() {
    }

    public static ConceptPaperView create(final ResearchAgentHost host, final Executor uiExecutor) {
        final ConceptPaperView view = new ConceptPaperView();
        view.setEditActions(new ConceptPaperView.SaveHandler() {
            @Override
            public String save(String documentJson, long expectedRevision) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.EditResult result = service.replaceDocument(documentJson, expectedRevision);
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }
        }, new ConceptPaperView.HistoryReader() {
            @Override
            public String content(long revision) {
                ConceptBranchService service = host.existingConceptService();
                return service == null ? null : service.workingHistoryContent(revision);
            }
        }, new ConceptPaperView.RestoreHandler() {
            @Override
            public String restore(long revision) {
                // Clean browse + Save = identity-preserving restore: document and identity return as a pair.
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.EditResult result = service.restoreRevision(revision);
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }
        });
        // Tree-editor gestures, ID-HARDENED: every action travels as (epoch, nodeId) and the service resolves the
        // CURRENT path inside the same atomic operation — a stale epoch or a vanished id aborts honestly.
        view.setTreeActions(new ConceptTreeView.Actions() {
            @Override
            public String rename(String epoch, String nodeId, String newName) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.EditResult result = service.renameNodeById(epoch, nodeId, newName);
                host.logConceptUiAction("rename -> " + (result.isApplied()
                        ? "applied revision=" + result.getNewRevision() : "refused"));
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }

            @Override
            public String deleteLeafOrTerminal(String epoch, String nodeId) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.EditResult result = service.deleteTerminalBranchById(epoch, nodeId);
                host.logConceptUiAction("delete leaf/terminal -> " + (result.isApplied()
                        ? "applied revision=" + result.getNewRevision() : "refused"));
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }

            @Override
            public String deleteBranch(String epoch, String nodeId) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.EditResult result = service.removeNodeById(epoch, nodeId);
                host.logConceptUiAction("delete branch (confirmed) -> " + (result.isApplied()
                        ? "applied revision=" + result.getNewRevision() : "refused"));
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }

            @Override
            public String addChild(String epoch, String parentNodeId, String insertBeforeNodeId, String name) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.AddCardsResult result = service.addCardsUnderId(epoch, parentNodeId,
                        Collections.singletonList(name), insertBeforeNodeId);
                host.logConceptUiAction("add \"" + name + "\""
                        + (insertBeforeNodeId == null ? " (end)" : " (before anchor)")
                        + (result.isApplied() ? " -> applied revision=" + result.getNewRevision() : " -> refused"));
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }

            @Override
            public String moveLeaf(String epoch, String sourceNodeId, String targetParentNodeId,
                                   String insertBeforeNodeId) {
                ConceptBranchService service = host.existingConceptService();
                if (service == null) {
                    return NO_SERVICE;
                }
                ConceptBranchService.MoveLeafResult result = service.moveLeafById(epoch, sourceNodeId,
                        targetParentNodeId, insertBeforeNodeId);
                String position = insertBeforeNodeId == null ? "end" : "before anchor";
                host.logConceptUiAction("move (" + position + ") -> "
                        + (!result.isApplied() ? "refused"
                                : result.isNoChange() ? "no change (already in place)"
                                        : "applied revision=" + result.getNewRevision()
                                                + " \"" + result.getLabel() + "\" "
                                                + result.getFromPath() + " -> " + result.getToPath()));
                return result.isApplied() ? null : result.getDiagnostic().describeForModel();
            }
        }, new ConceptTreeView.BlacklistSource() {
            @Override
            public List<String> terms() {
                return host.blacklistTermsForDisplay();
            }
        }, new ConceptTreeView.IdentityContext() {
            @Override
            public String epoch() {
                ConceptBranchService service = host.existingConceptService();
                return service == null ? null : service.currentEpoch();
            }

            @Override
            public String idAt(List<String> path) {
                ConceptBranchService service = host.existingConceptService();
                return service == null ? null : service.nodeIdAtPath(path);
            }
        });
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                // ONE atomic snapshot per refresh (JSON + revision from the same state); the store stays the truth.
                ConceptBranchService service = host.existingConceptService();
                final ConceptPaperView.ConceptProjection projection = service == null ? null
                        : toView(ConceptProjection.of(service.snapshot()));
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
        host.addStateListener(refresh);
        refresh.run();
        return view;
    }

    private static ConceptPaperView.ConceptProjection toView(ConceptProjection projection) {
        return new ConceptPaperView.ConceptProjection(projection.isReadable(), projection.getDiagnosticText(),
                projection.getWorkingRevision(), projection.getPrettyJson());
    }
}

package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.concept.ConceptBranchService;
import com.aresstack.enterpriseai.research.concept.ConceptProjection;

import java.util.Collections;
import java.util.List;

/**
 * Die Bearbeitung des Konzeptpapiers für den Drawer-Reiter „Konzept“ (askai-java8 arch
 * {@code ResearchBriefViewContribution}), ohne Oberfläche: manuelle Änderungen gehen durch dieselbe prüfende
 * Pipeline wie jeder Agentenschritt ({@code ConceptBranchService.replaceDocument}), Baum-Gesten laufen über (Epoche,
 * Knoten-ID). Jede Aktion liefert {@code null} bei Erfolg, sonst die Diagnose. Die Schale (exedra) kennt den
 * Konzeptdienst nicht, nur diese Klasse.
 */
public final class ResearchConceptEditing {

    private static final String NO_SERVICE = "This session has no concept service.";

    private final ResearchAgentHost host;

    public ResearchConceptEditing(ResearchAgentHost host) {
        if (host == null) {
            throw new IllegalArgumentException("host must not be null");
        }
        this.host = host;
    }

    /** Ein Schnappschuss des Konzeptpapiers (JSON und Revision aus demselben Zustand). */
    public static final class Snapshot {
        private final boolean readable;
        private final String diagnosticText;
        private final long workingRevision;
        private final String prettyJson;

        Snapshot(boolean readable, String diagnosticText, long workingRevision, String prettyJson) {
            this.readable = readable;
            this.diagnosticText = diagnosticText;
            this.workingRevision = workingRevision;
            this.prettyJson = prettyJson;
        }

        public boolean isReadable() {
            return readable;
        }

        public String diagnosticText() {
            return diagnosticText;
        }

        public long workingRevision() {
            return workingRevision;
        }

        public String prettyJson() {
            return prettyJson;
        }
    }

    public String save(String documentJson, long expectedRevision) {
        ConceptBranchService service = host.existingConceptService();
        if (service == null) {
            return NO_SERVICE;
        }
        ConceptBranchService.EditResult result = service.replaceDocument(documentJson, expectedRevision);
        return result.isApplied() ? null : result.getDiagnostic().describeForModel();
    }

    public String historyContent(long revision) {
        ConceptBranchService service = host.existingConceptService();
        return service == null ? null : service.workingHistoryContent(revision);
    }

    public String restore(long revision) {
        // Clean browse + Save = identity-preserving restore: document and identity return as a pair.
        ConceptBranchService service = host.existingConceptService();
        if (service == null) {
            return NO_SERVICE;
        }
        ConceptBranchService.EditResult result = service.restoreRevision(revision);
        return result.isApplied() ? null : result.getDiagnostic().describeForModel();
    }

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

    public String moveLeaf(String epoch, String sourceNodeId, String targetParentNodeId, String insertBeforeNodeId) {
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

    public List<String> blacklistTerms() {
        return host.blacklistTermsForDisplay();
    }

    public String epoch() {
        ConceptBranchService service = host.existingConceptService();
        return service == null ? null : service.currentEpoch();
    }

    public String idAt(List<String> path) {
        ConceptBranchService service = host.existingConceptService();
        return service == null ? null : service.nodeIdAtPath(path);
    }

    /** ONE atomic snapshot per refresh (JSON + revision from the same state); {@code null} without a service. */
    public Snapshot snapshot() {
        ConceptBranchService service = host.existingConceptService();
        if (service == null) {
            return null;
        }
        ConceptProjection projection = ConceptProjection.of(service.snapshot());
        return new Snapshot(projection.isReadable(), projection.getDiagnosticText(),
                projection.getWorkingRevision(), projection.getPrettyJson());
    }

    /** Jede angewandte Änderung und jedes neue Projekt melden sich hier. */
    public void addStateListener(Runnable listener) {
        host.addStateListener(listener);
    }
}

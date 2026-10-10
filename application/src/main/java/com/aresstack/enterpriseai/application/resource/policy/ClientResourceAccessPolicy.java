package com.aresstack.enterpriseai.application.resource.policy;

/**
 * Tamias-Regel dieses Clients: Welche Quellen gelesen werden dürfen, entscheiden Konfiguration und Wissensindex
 * vorher (nur angebundene Quellen, nur indexierte Ressourcen); hier gilt deshalb: Lesen, Auflisten und Beschaffen
 * ist jedem Akteur erlaubt, einen Archiveintrag löschen darf nur die Anwendung selbst ({@link ActorType#SERVICE}),
 * etwa wenn eine Quelle entfernt wird. Jede andere Operation wird abgelehnt.
 */
public final class ClientResourceAccessPolicy implements ResourceAccessPolicy {

    @Override
    public ResourceAccessDecision evaluate(ResourceAccessRequest request) {
        if (request == null || request.operation() == null) {
            return ResourceAccessDecision.deny(AccessReasonCode.INVALID_OPERATION, "keine Operation");
        }
        switch (request.operation()) {
            case READ_METADATA:
            case READ_CONTENT:
            case LIST_CHILDREN:
            case FETCH_EXTERNAL:
                return ResourceAccessDecision.allow();
            case DELETE_ARCHIVE_ENTRY:
                if (request.actor() != null && request.actor().actorType() == ActorType.SERVICE) {
                    return ResourceAccessDecision.allow();
                }
                return ResourceAccessDecision.deny(AccessReasonCode.NOT_VISIBLE_TO_ACTOR,
                        "Archiveinträge löscht nur die Anwendung");
            default:
                return ResourceAccessDecision.deny(AccessReasonCode.NOT_WHITELISTED,
                        "Operation " + request.operation() + " ist nicht freigegeben");
        }
    }
}

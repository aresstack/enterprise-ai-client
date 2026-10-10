package com.aresstack.enterpriseai.application.resource.policy;

import com.aresstack.enterpriseai.domain.resource.BookmarkUri;

/**
 * A request for mediated access to a resource through the bronze archive counter.
 */
public final class ResourceAccessRequest {

    private final ActorIdentity actor;
    private final BookmarkUri target;
    private final ResourceOperation operation;
    private final String purpose;

    public ResourceAccessRequest(ActorIdentity actor, BookmarkUri target, ResourceOperation operation) {
        this(actor, target, operation, null);
    }

    public ResourceAccessRequest(ActorIdentity actor, BookmarkUri target,
                                 ResourceOperation operation, String purpose) {
        if (actor == null) throw new IllegalArgumentException("actor must not be null");
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (operation == null) throw new IllegalArgumentException("operation must not be null");
        this.actor = actor;
        this.target = target;
        this.operation = operation;
        this.purpose = purpose;
    }

    public ActorIdentity actor() { return actor; }
    public BookmarkUri target() { return target; }
    public ResourceOperation operation() { return operation; }
    public String purpose() { return purpose; }

    @Override
    public String toString() {
        return "ResourceAccessRequest{" + actor + ", " + target + ", " + operation + "}";
    }
}

package com.aresstack.enterpriseai.application.resource.policy;

import java.util.Collections;
import java.util.List;

/**
 * Identifies the caller requesting a resource operation.
 *
 * <p>A natural human user and a bot/AI-user may intentionally have different
 * rights even when the bot acts in the user's context.
 */
public final class ActorIdentity {

    private final String subjectId;
    private final ActorType actorType;
    private final String displayName;
    private final List<String> roles;

    public ActorIdentity(String subjectId, ActorType actorType) {
        this(subjectId, actorType, null, Collections.<String>emptyList());
    }

    public ActorIdentity(String subjectId, ActorType actorType, String displayName, List<String> roles) {
        if (subjectId == null || subjectId.isEmpty()) {
            throw new IllegalArgumentException("subjectId must not be null or empty");
        }
        if (actorType == null) {
            throw new IllegalArgumentException("actorType must not be null");
        }
        this.subjectId = subjectId;
        this.actorType = actorType;
        this.displayName = displayName;
        this.roles = roles != null
                ? Collections.unmodifiableList(new java.util.ArrayList<String>(roles))
                : Collections.<String>emptyList();
    }

    public String subjectId() { return subjectId; }
    public ActorType actorType() { return actorType; }
    public String displayName() { return displayName; }
    public List<String> roles() { return roles; }

    @Override
    public String toString() {
        return "ActorIdentity{" + subjectId + ", " + actorType + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ActorIdentity)) return false;
        ActorIdentity that = (ActorIdentity) o;
        return subjectId.equals(that.subjectId) && actorType == that.actorType;
    }

    @Override
    public int hashCode() {
        return 31 * subjectId.hashCode() + actorType.hashCode();
    }
}

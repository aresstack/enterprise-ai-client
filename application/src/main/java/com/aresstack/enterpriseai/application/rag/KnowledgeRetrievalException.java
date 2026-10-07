package com.aresstack.enterpriseai.application.rag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Alle aktiven Suchpfade sind ausgefallen; die einzelnen Ursachen stehen in {@link #warnings()}. */
public class KnowledgeRetrievalException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<RetrievalWarning> warnings;

    KnowledgeRetrievalException(List<RetrievalWarning> warnings, Throwable cause) {
        super("Wissenssuche fehlgeschlagen: " + warnings, cause);
        this.warnings = Collections.unmodifiableList(new ArrayList<RetrievalWarning>(warnings));
    }

    public List<RetrievalWarning> warnings() {
        return warnings == null ? Collections.<RetrievalWarning>emptyList() : warnings;
    }
}

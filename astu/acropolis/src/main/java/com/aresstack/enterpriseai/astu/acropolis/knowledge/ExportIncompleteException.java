package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A page export (#115) could not load every page of the chosen subtree. No document is written: an export is complete
 * or it fails. {@link #failures()} names each page or branch that was missing and why.
 */
public final class ExportIncompleteException extends Exception {

    private static final long serialVersionUID = 1L;

    private final List<String> failures;

    public ExportIncompleteException(List<String> failures) {
        super(failures == null || failures.isEmpty() ? "export incomplete"
                : "export incomplete: " + failures.size() + " page(s) missing");
        this.failures = failures == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<String>(failures));
    }

    /** Return one line per missing page or branch, e.g. {@code "Seite X: kein Zugriff"}. */
    public List<String> failures() {
        return failures;
    }
}

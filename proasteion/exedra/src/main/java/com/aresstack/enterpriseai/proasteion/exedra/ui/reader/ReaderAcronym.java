package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One acronym the analysis of a page found (issue #107), as the reader needs it: the spellings to mark and the long
 * forms this page itself states. Without long forms the reader shows no explanation.
 */
public final class ReaderAcronym {

    static final String TIP_ONE = "Langform in diesem Dokument: ";
    static final String TIP_MANY = "Dieses Dokument nennt verschiedene Langformen: ";

    private final Set<String> spellings;
    private final List<String> longForms;

    public ReaderAcronym(Set<String> spellings, List<String> longForms) {
        if (spellings == null || spellings.isEmpty()) {
            throw new IllegalArgumentException("spellings must not be empty");
        }
        this.spellings = Collections.unmodifiableSet(new LinkedHashSet<String>(spellings));
        this.longForms = Collections.unmodifiableList(longForms == null ? Collections.<String>emptyList()
                : new ArrayList<String>(longForms));
    }

    /** Every spelling as written in the page ({@code IAR}, {@code IARs}). */
    public Set<String> spellings() {
        return spellings;
    }

    /** The long forms stated in this page, in order; empty for a mere candidate. */
    public List<String> longForms() {
        return longForms;
    }

    /** The tooltip over a mark, or {@code null} when the page states no long form. */
    String tooltip() {
        if (longForms.isEmpty()) {
            return null;
        }
        if (longForms.size() == 1) {
            return TIP_ONE + longForms.get(0);
        }
        StringBuilder text = new StringBuilder(TIP_MANY);
        for (int i = 0; i < longForms.size(); i++) {
            text.append(i == 0 ? "" : "; ").append(longForms.get(i));
        }
        return text.toString();
    }
}

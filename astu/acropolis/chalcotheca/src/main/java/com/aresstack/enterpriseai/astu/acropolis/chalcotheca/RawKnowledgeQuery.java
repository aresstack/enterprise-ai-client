package com.aresstack.enterpriseai.astu.acropolis.chalcotheca;

import com.aresstack.enterpriseai.astu.knowledge.SourceAddress;

import java.util.Collection;
import java.util.Set;

/**
 * Anfrage an den {@link RawKnowledgeIndexPort}: Text (vom Index analysiert, keine Anfragesyntax), höchstens so viele
 * Dokumente, je Dokument so viele Textfenster dieser Länge, optional auf Quellen eingeschränkt (leer = alle).
 */
public final class RawKnowledgeQuery {

    private final String text;
    private final int maxDocuments;
    private final int windowsPerDocument;
    private final int windowChars;
    private final Set<SourceAddress> sources;

    private RawKnowledgeQuery(String text, int maxDocuments, int windowsPerDocument, int windowChars,
                              Set<SourceAddress> sources) {
        this.text = text == null ? "" : text;
        this.maxDocuments = QueryLimits.maxResults(maxDocuments);
        if (windowsPerDocument < 1) {
            throw new IllegalArgumentException("windowsPerDocument >= 1 erwartet: " + windowsPerDocument);
        }
        if (windowChars < 1) {
            throw new IllegalArgumentException("windowChars >= 1 erwartet: " + windowChars);
        }
        this.windowsPerDocument = windowsPerDocument;
        this.windowChars = windowChars;
        this.sources = sources;
    }

    /** @param maxDocuments Obergrenze der Dokumente; {@code <= 0} heißt Default (10), gedeckelt auf 1000 */
    public static RawKnowledgeQuery of(String text, int maxDocuments, int windowsPerDocument, int windowChars) {
        return new RawKnowledgeQuery(text, maxDocuments, windowsPerDocument, windowChars, QueryLimits.sources(null));
    }

    /** Kopie, die nur in den genannten Quellen sucht; leer hebt die Einschränkung auf. */
    public RawKnowledgeQuery restrictedTo(Collection<SourceAddress> sourceIds) {
        return new RawKnowledgeQuery(text, maxDocuments, windowsPerDocument, windowChars,
                QueryLimits.sources(sourceIds));
    }

    public String text() {
        return text;
    }

    public int maxDocuments() {
        return maxDocuments;
    }

    public int windowsPerDocument() {
        return windowsPerDocument;
    }

    /** Ungefähre Länge eines Textfensters in Zeichen (an Wortgrenzen). */
    public int windowChars() {
        return windowChars;
    }

    /** Erlaubte Quellen; leer heißt alle. */
    public Set<SourceAddress> sources() {
        return sources;
    }

    public boolean accepts(SourceAddress sourceId) {
        return sources.isEmpty() || sources.contains(sourceId);
    }
}

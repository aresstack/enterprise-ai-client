package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgeTokenCounter;
import com.aresstack.enterpriseai.embedding.api.EmbeddingPort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Quelle → Sätze → semantische Passagen, übernommen aus askai-java8 arch {@code PassageSegmentation}
 * ({@code BoundaryMode.SEMANTIC}). DIE STRUKTUR DER QUELLE KOMMT ZUERST: Überschriften, Listenpunkte,
 * Tabellenzeilen, Zitate und Code-Blöcke sind harte Grenzen; nur in Absätzen verfeinert die Themenwechsel-Erkennung:
 * nicht {@code cos(s[n], s[n+1])}, sondern die Ähnlichkeit eines LINKEN und RECHTEN Satzfensters (bis
 * {@link #WINDOW_SIZE} Sätze). Mindest- und Höchstzahl an Sätzen verhindern Mini-Passagen und Textwände. Jede
 * fertige Passage bekommt ihr EIGENES Embedding. Werte wie in arch (3, 0.35, 2, 8); dieselben Werte nutzt das
 * Vorlesen ({@code SemanticParagraphs}).
 *
 * <p>Die Blöcke liest sie aus dem Text der Quelle (Markdown-artig, wie {@code knowledge_read} ihn liefert):
 * {@code #}-Überschriften, Code-Zäune, Listenpunkte, {@code |}-Tabellenzeilen, {@code >}-Zitate, sonst Absätze
 * zwischen Leerzeilen.
 *
 * <p>Die Satzgrenzen liefert ein {@link SentenceSegmenter} (produktiv das OpenNLP-Satzmodell der Sprache, wie arch).
 * Abweichung von arch: eine Passage über {@link #MAX_PASSAGE_TOKENS} Token (z. B. ein langer Code-Block) wird an
 * Wortgrenzen geteilt, damit der Embedding-Endpunkt sie annimmt; die Passage-Embeddings eines Dokuments gehen in
 * Batches an den Port (gleiche Vektoren wie einzeln). {@link #fingerprint()} beschreibt alles, was die Passagen
 * bestimmt; ändert er sich, indexiert der Index neu.
 */
public final class PassageSegmentation {

    static final int WINDOW_SIZE = 3;
    static final double BOUNDARY_THRESHOLD = 0.35;
    static final int MIN_PASSAGE_SENTENCES = 2;
    static final int MAX_PASSAGE_SENTENCES = 8;
    /** Version der Zerlegung: ändert sie sich, entstehen neue Passage-IDs. */
    public static final String SEGMENTATION_VERSION = "semantic-v1";
    /** Höchstgröße einer Passage in Token (wie das bisherige Chunk-Budget), sonst an Wortgrenzen geteilt. */
    static final int MAX_PASSAGE_TOKENS = 350;
    /** Passage-Texte je Embedding-Aufruf. */
    static final int EMBEDDING_BATCH = 16;

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*?)[ \\t#]*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+•]|\\d{1,3}[.)])\\s+\\S.*$");

    enum BlockKind { HEADING, PARAGRAPH, LIST_ITEM, TABLE_ROW, QUOTE, CODE }

    static final class Block {
        final BlockKind kind;
        final String headingPath;
        final String text;

        Block(BlockKind kind, String headingPath, String text) {
            this.kind = kind;
            this.headingPath = headingPath;
            this.text = text;
        }
    }

    private final EmbeddingPort embeddings;
    private final SentenceSegmenter sentences;
    private final KnowledgeTokenCounter tokens = KnowledgeTokenCounter.wordsAndSymbols();

    public PassageSegmentation(EmbeddingPort embeddings) {
        this(embeddings, SentenceSegmenter.splitter());
    }

    public PassageSegmentation(EmbeddingPort embeddings, SentenceSegmenter sentences) {
        if (embeddings == null || sentences == null) {
            throw new IllegalArgumentException("embeddings and sentences must not be null");
        }
        this.embeddings = embeddings;
        this.sentences = sentences;
    }

    /**
     * Was die Passagen bestimmt (Verfahren, Parameter, Satzmodell), z. B.
     * {@code semantic-v1;window=3;threshold=0.35;min=2;max=8;maxTokens=350;sentences=opennlp-de}. Das Embedding-Modell
     * steckt im Namensraum des Index und in der Passage-ID.
     */
    public String fingerprint() {
        return SEGMENTATION_VERSION + ";window=" + WINDOW_SIZE + ";threshold=" + BOUNDARY_THRESHOLD + ";min="
                + MIN_PASSAGE_SENTENCES + ";max=" + MAX_PASSAGE_SENTENCES + ";maxTokens=" + MAX_PASSAGE_TOKENS
                + ";sentences=" + sentences.id();
    }

    /** Die Passagen einer Quelle in Lesereihenfolge. */
    public List<SourcePassage> segment(String sourceId, String text) {
        List<SourcePassage> passages = new ArrayList<SourcePassage>();
        List<String> pendingTexts = new ArrayList<String>();
        List<Object[]> pending = new ArrayList<Object[]>(); // {headingPath, firstSentence}
        int ordinal = 0;
        for (Block block : blocksOf(text)) {
            List<String> blockSentences = sentencesOf(block);
            int first = ordinal;
            ordinal += blockSentences.size();
            if (blockSentences.isEmpty() || block.kind == BlockKind.HEADING) {
                continue; // Überschriften bilden den Abschnittspfad, sind aber kein Passageninhalt
            }
            int offset = first;
            for (List<String> passageSentences : splitSemantically(blockSentences, block)) {
                StringBuilder joined = new StringBuilder();
                for (String sentence : passageSentences) {
                    if (joined.length() > 0) {
                        joined.append(' ');
                    }
                    joined.append(sentence);
                }
                for (String part : bounded(joined.toString())) {
                    pendingTexts.add(part);
                    pending.add(new Object[] {block.headingPath, Integer.valueOf(offset)});
                }
                offset += passageSentences.size();
            }
        }
        // Jede fertige Passage bekommt ihr EIGENES Embedding (in Batches statt einzeln, gleiche Vektoren).
        java.util.Set<String> ids = new java.util.HashSet<String>();
        for (int from = 0; from < pendingTexts.size(); from += EMBEDDING_BATCH) {
            List<String> slice = pendingTexts.subList(from, Math.min(pendingTexts.size(), from + EMBEDDING_BATCH));
            List<EmbeddingVector> vectors = embeddings.embed(slice).vectors();
            for (int i = 0; i < slice.size(); i++) {
                EmbeddingVector vector = vectors.get(i);
                Object[] meta = pending.get(from + i);
                int first = ((Integer) meta[1]).intValue();
                String fingerprint = vector.identity().fingerprint();
                String id = sourceId + "#p" + first + "@" + SEGMENTATION_VERSION + "-" + fingerprint;
                for (int part = 1; !ids.add(id); part++) { // geteilte Passage: Teilnummer anhängen
                    id = sourceId + "#p" + first + "." + part + "@" + SEGMENTATION_VERSION + "-" + fingerprint;
                }
                passages.add(new SourcePassage(id, sourceId, (String) meta[0], slice.get(i), first, fingerprint,
                        vector.values()));
            }
        }
        return passages;
    }

    /** Eine Passage über {@link #MAX_PASSAGE_TOKENS} an Wortgrenzen geteilt; sonst unverändert. */
    private List<String> bounded(String text) {
        if (tokens.count(text) <= MAX_PASSAGE_TOKENS) {
            return Collections.singletonList(text);
        }
        List<String> parts = new ArrayList<String>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = current.length() == 0 ? word : current + " " + word;
            if (current.length() > 0 && tokens.count(candidate) > MAX_PASSAGE_TOKENS) {
                parts.add(current.toString());
                current.setLength(0);
                current.append(word);
            } else {
                current.setLength(0);
                current.append(candidate);
            }
        }
        if (current.length() > 0) {
            parts.add(current.toString());
        }
        return parts;
    }

    private List<String> sentencesOf(Block block) {
        String trimmed = block.text.trim();
        if (trimmed.isEmpty()) {
            return Collections.emptyList();
        }
        if (block.kind == BlockKind.CODE || block.kind == BlockKind.TABLE_ROW || block.kind == BlockKind.HEADING) {
            return Collections.singletonList(trimmed); // Code und Tabellenzeilen nie in Scheinsätze zerlegen
        }
        List<String> found = sentences.sentences(trimmed);
        return found == null || found.isEmpty() ? Collections.singletonList(trimmed) : found;
    }

    /** Semantische Verfeinerung INNERHALB eines Absatzes (die Struktur ist schon harte Grenze). */
    private List<List<String>> splitSemantically(List<String> blockSentences, Block block) {
        List<List<String>> passages = new ArrayList<List<String>>();
        if (block.kind != BlockKind.PARAGRAPH || blockSentences.size() <= MIN_PASSAGE_SENTENCES) {
            passages.add(blockSentences);
            return passages;
        }
        List<EmbeddingVector> vectors = embeddings.embed(blockSentences).vectors();
        List<String> current = new ArrayList<String>();
        for (int i = 0; i < blockSentences.size(); i++) {
            current.add(blockSentences.get(i));
            boolean last = i == blockSentences.size() - 1;
            boolean maxReached = current.size() >= MAX_PASSAGE_SENTENCES;
            boolean semanticBoundary = false;
            if (!last && current.size() >= MIN_PASSAGE_SENTENCES
                    && blockSentences.size() - i - 1 >= MIN_PASSAGE_SENTENCES) {
                semanticBoundary = boundaryStrength(vectors, i) < BOUNDARY_THRESHOLD;
            }
            if (last || maxReached || semanticBoundary) {
                passages.add(current);
                current = new ArrayList<String>();
            }
        }
        if (!current.isEmpty()) {
            passages.add(current);
        }
        return passages;
    }

    /** Ähnlichkeit der Fenster LINKS der Grenze (…i) und RECHTS davon (i+1…): niedrig = Themenwechsel. */
    private static double boundaryStrength(List<EmbeddingVector> vectors, int boundaryAfter) {
        int leftFrom = Math.max(0, boundaryAfter - WINDOW_SIZE + 1);
        int rightTo = Math.min(vectors.size(), boundaryAfter + 1 + WINDOW_SIZE);
        return cosine(mean(vectors.subList(leftFrom, boundaryAfter + 1)),
                mean(vectors.subList(boundaryAfter + 1, rightTo)));
    }

    private static double[] mean(List<EmbeddingVector> vectors) {
        double[] sum = new double[vectors.get(0).dimension()];
        for (EmbeddingVector vector : vectors) {
            for (int d = 0; d < sum.length; d++) {
                sum[d] += vector.valueAt(d);
            }
        }
        for (int d = 0; d < sum.length; d++) {
            sum[d] /= vectors.size();
        }
        return sum;
    }

    private static double cosine(double[] left, double[] right) {
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int d = 0; d < left.length; d++) {
            dot += left[d] * right[d];
            leftNorm += left[d] * left[d];
            rightNorm += right[d] * right[d];
        }
        return leftNorm == 0 || rightNorm == 0 ? 0 : dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    /** Die Strukturblöcke des Textes; Überschriften setzen den Abschnittspfad der folgenden Blöcke. */
    static List<Block> blocksOf(String text) {
        List<Block> blocks = new ArrayList<Block>();
        List<String> headings = new ArrayList<String>();
        StringBuilder paragraph = new StringBuilder();
        StringBuilder code = null;
        for (String line : (text == null ? "" : text).split("\\r?\\n", -1)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                if (code == null) {
                    flush(blocks, paragraph, headings);
                    code = new StringBuilder();
                } else {
                    blocks.add(new Block(BlockKind.CODE, path(headings), code.toString()));
                    code = null;
                }
                continue;
            }
            if (code != null) {
                code.append(line).append('\n');
                continue;
            }
            java.util.regex.Matcher heading = HEADING.matcher(line);
            if (heading.matches()) {
                flush(blocks, paragraph, headings);
                int level = heading.group(1).length();
                while (headings.size() >= level) {
                    headings.remove(headings.size() - 1);
                }
                while (headings.size() < level - 1) {
                    headings.add("");
                }
                headings.add(heading.group(2).trim());
                blocks.add(new Block(BlockKind.HEADING, path(headings), heading.group(2).trim()));
            } else if (trimmed.isEmpty()) {
                flush(blocks, paragraph, headings);
            } else if (trimmed.startsWith("|")) {
                flush(blocks, paragraph, headings);
                if (!trimmed.matches("\\|?[\\s:|-]+\\|?")) { // Trennzeile der Tabelle auslassen
                    blocks.add(new Block(BlockKind.TABLE_ROW, path(headings), trimmed));
                }
            } else if (trimmed.startsWith(">")) {
                flush(blocks, paragraph, headings);
                blocks.add(new Block(BlockKind.QUOTE, path(headings), trimmed.substring(1).trim()));
            } else if (LIST_ITEM.matcher(line).matches()) {
                flush(blocks, paragraph, headings);
                blocks.add(new Block(BlockKind.LIST_ITEM, path(headings), trimmed));
            } else {
                if (paragraph.length() > 0) {
                    paragraph.append(' ');
                }
                paragraph.append(trimmed);
            }
        }
        if (code != null && code.length() > 0) {
            blocks.add(new Block(BlockKind.CODE, path(headings), code.toString()));
        }
        flush(blocks, paragraph, headings);
        return blocks;
    }

    private static void flush(List<Block> blocks, StringBuilder paragraph, List<String> headings) {
        if (paragraph.length() > 0) {
            blocks.add(new Block(BlockKind.PARAGRAPH, path(headings), paragraph.toString()));
            paragraph.setLength(0);
        }
    }

    private static String path(List<String> headings) {
        StringBuilder path = new StringBuilder();
        for (String heading : headings) {
            if (heading.isEmpty()) {
                continue;
            }
            if (path.length() > 0) {
                path.append(" > ");
            }
            path.append(heading);
        }
        return path.toString();
    }
}

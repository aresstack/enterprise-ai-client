package com.aresstack.enterpriseai.domain.knowledge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Zerlegt ein {@link KnowledgeDocument} deterministisch in {@link KnowledgeChunk}s – reine Logik ohne
 * Abhängigkeiten, gleiche Eingabe ergibt unter jedem JDK dieselben Chunks.
 *
 * <p>Regeln (Struktur zuerst, dann Sätze):
 * <ol>
 *   <li>Markdown-Überschriften ({@code #} bis {@code ######}) sind harte Grenzen: Ein Chunk überspannt nie zwei
 *       Abschnitte und trägt die Überschriftenkette als {@link KnowledgeChunk#headingPath()}.</li>
 *   <li>Innerhalb eines Abschnitts werden Absätze, Listenpunkte und Code-Blöcke in Sätze bzw. Zeilen zerlegt
 *       und satzweise bis zum Token-Budget gepackt. Absatzgrenzen bleiben als Leerzeile im Chunk erhalten,
 *       Listenpunkte als eigene Zeilen, Code wird nie in Sätze zerlegt.</li>
 *   <li>Overlap: Der nächste Chunk desselben Abschnitts beginnt mit den letzten
 *       {@link KnowledgeChunkingPolicy#overlapSentences()} Sätzen des vorigen, soweit das Budget es zulässt.
 *       Jeder Chunk enthält mindestens einen neuen Satz.</li>
 *   <li>Ein einzelner Satz über dem Budget wird an Wortgrenzen geteilt, damit kein Chunk den Embedding-Endpunkt
 *       überfordert.</li>
 * </ol>
 * Das Budget gilt exakt für {@link KnowledgeChunk#textWithHeading()} laut Token-Zähler (Trenner eingerechnet);
 * überschritten wird es nur von einem einzelnen Wort, das allein größer ist. Die Überschriftenzeile belegt
 * höchstens die Hälfte des Budgets und wird sonst gekürzt (äußere Überschriften zuerst).
 *
 * <p>Übernommen und adaptiert aus aresstack/corenth {@code NlpTextChunker} (Überschriften-Abschnitte,
 * Token-Budget, Satz-Overlap) und askai-java8 {@code PassageSegmentation} (Struktur als harte Grenze,
 * Code/Tabellen nie in Sätze zerlegt, deterministische IDs); ohne Lucene-Tokenizer und ohne Embedding-Aufrufe.
 */
public final class KnowledgeChunker {

    private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})[ \\t]+(.*?)(?:[ \\t]+#+)?[ \\t]*$");
    private static final Pattern LIST_ITEM = Pattern.compile("^\\s*(?:[-*+•]|\\d{1,3}[.)])\\s+\\S.*$");
    private static final String SENTENCE_SEPARATOR = " ";
    private static final String LINE_SEPARATOR = "\n";
    private static final String BLOCK_SEPARATOR = "\n\n";

    private final KnowledgeChunkingPolicy policy;
    private final KnowledgeTokenCounter tokenCounter;
    private final SentenceSplitter sentenceSplitter = new SentenceSplitter();

    public KnowledgeChunker(KnowledgeChunkingPolicy policy) {
        this(policy, KnowledgeTokenCounter.wordsAndSymbols());
    }

    public KnowledgeChunker(KnowledgeChunkingPolicy policy, KnowledgeTokenCounter tokenCounter) {
        if (policy == null) {
            throw new IllegalArgumentException("policy fehlt");
        }
        if (tokenCounter == null) {
            throw new IllegalArgumentException("tokenCounter fehlt");
        }
        this.policy = policy;
        this.tokenCounter = tokenCounter;
    }

    public KnowledgeChunkingPolicy policy() {
        return policy;
    }

    /** Chunks in Dokumentreihenfolge mit lückenlosen Ordinalzahlen ab 0; leer bei leerem Dokument. */
    public List<KnowledgeChunk> chunk(KnowledgeDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("Dokument fehlt");
        }
        List<KnowledgeChunk> chunks = new ArrayList<KnowledgeChunk>();
        if (document.isBlank()) {
            return chunks;
        }
        for (Section section : new StructureParser().parse(document.text())) {
            chunkSection(document.resource(), section, chunks);
        }
        return Collections.unmodifiableList(chunks);
    }

    // ------------------------------------------------------------------ packing

    private void chunkSection(KnowledgeResource resource, Section section, List<KnowledgeChunk> chunks) {
        List<String> headingPath = fitHeading(section.headingPath);
        String prefix = headingPath.isEmpty() ? "" : headingLine(headingPath) + "\n\n";
        List<Unit> units = fitToBudget(section.units, prefix);

        List<Unit> previous = Collections.emptyList();
        int next = 0;
        while (next < units.size()) {
            List<Unit> current = new ArrayList<Unit>(overlapOf(previous));
            current.add(units.get(next++));
            while (current.size() > 1 && !fits(prefix, current)) {
                current.remove(0); // Overlap weicht, der neue Satz bleibt
            }
            while (next < units.size()) {
                current.add(units.get(next));
                if (!fits(prefix, current)) {
                    current.remove(current.size() - 1);
                    break;
                }
                next++;
            }
            chunks.add(toChunk(resource, chunks.size(), headingPath, current));
            previous = current;
        }
    }

    private List<Unit> overlapOf(List<Unit> previous) {
        int overlap = Math.min(policy.overlapSentences(), previous.size());
        return previous.subList(previous.size() - overlap, previous.size());
    }

    /** Misst den zusammengesetzten Text samt Trennern und Überschrift, nicht die Summe der Einzelteile. */
    private boolean fits(String prefix, List<Unit> units) {
        return tokenCounter.count(prefix + join(units)) <= policy.maxTokens();
    }

    private static String join(List<Unit> units) {
        StringBuilder text = new StringBuilder();
        for (Unit unit : units) {
            if (text.length() > 0) {
                text.append(unit.separator);
            }
            text.append(unit.text);
        }
        return text.toString();
    }

    private KnowledgeChunk toChunk(KnowledgeResource resource, int ordinal, List<String> headingPath,
                                   List<Unit> units) {
        KnowledgeChunk draft = new KnowledgeChunk(KnowledgeChunkId.of(resource.id(), ordinal), resource.sourceId(),
                headingPath, join(units), 0);
        return new KnowledgeChunk(draft.id(), draft.sourceId(), headingPath, draft.text(),
                tokenCounter.count(draft.textWithHeading()));
    }

    /**
     * Begrenzt die Überschriftenzeile auf das halbe Budget: zuerst entfallen äußere Überschriften, dann wird die
     * innerste an Wortgrenzen gekürzt. So bleibt für den Text immer mindestens die Hälfte.
     */
    private List<String> fitHeading(List<String> headingPath) {
        int limit = policy.maxTokens() / 2;
        List<String> path = new ArrayList<String>(headingPath);
        while (path.size() > 1 && tokenCounter.count(headingLine(path)) > limit) {
            path.remove(0);
        }
        if (path.size() == 1 && tokenCounter.count(path.get(0)) > limit) {
            String[] words = path.get(0).split("\\s+");
            StringBuilder shortened = new StringBuilder(words[0]);
            for (int i = 1; i < words.length
                    && tokenCounter.count(shortened + " " + words[i]) <= limit; i++) {
                shortened.append(' ').append(words[i]);
            }
            path.set(0, shortened.toString());
        }
        return path;
    }

    /** Teilt Einheiten über dem Budget an Wortgrenzen; ein einzelnes Wort über dem Budget bleibt ganz. */
    private List<Unit> fitToBudget(List<Unit> units, String prefix) {
        List<Unit> fitted = new ArrayList<Unit>();
        for (Unit unit : units) {
            if (tokenCounter.count(prefix + unit.text) <= policy.maxTokens()) {
                fitted.add(unit);
                continue;
            }
            String separator = unit.separator;
            StringBuilder piece = new StringBuilder();
            for (String word : unit.text.trim().split("\\s+")) {
                if (piece.length() > 0
                        && tokenCounter.count(prefix + piece + " " + word) > policy.maxTokens()) {
                    fitted.add(new Unit(piece.toString(), separator));
                    separator = SENTENCE_SEPARATOR;
                    piece.setLength(0);
                }
                if (piece.length() > 0) {
                    piece.append(' ');
                }
                piece.append(word);
            }
            if (piece.length() > 0) {
                fitted.add(new Unit(piece.toString(), separator));
            }
        }
        return fitted;
    }

    private static String headingLine(List<String> headingPath) {
        StringBuilder line = new StringBuilder();
        for (String heading : headingPath) {
            if (line.length() > 0) {
                line.append(" > ");
            }
            line.append(heading);
        }
        return line.toString();
    }

    // ------------------------------------------------------------------ structure

    /** Zerlegt den normalisierten Text in Abschnitte (je Überschrift) aus Einheiten (Sätze, Listenpunkte, Code). */
    private final class StructureParser {

        private final List<Section> sections = new ArrayList<Section>();
        private final List<Integer> headingLevels = new ArrayList<Integer>();
        private final List<String> headingTitles = new ArrayList<String>();
        private final List<String> paragraph = new ArrayList<String>();
        private Section section = new Section(Collections.<String>emptyList());
        private boolean blockBreakPending;

        List<Section> parse(String text) {
            String fence = null;
            List<String> code = new ArrayList<String>();
            for (String line : text.split("\n", -1)) {
                String trimmed = line.trim();
                if (fence != null) {
                    if (trimmed.startsWith(fence) && trimmed.replace(fence.substring(0, 1), "").isEmpty()) {
                        addCode(code);
                        fence = null;
                    } else {
                        code.add(line);
                    }
                    continue;
                }
                if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                    flushParagraph();
                    fence = trimmed.substring(0, 3);
                    code = new ArrayList<String>();
                    continue;
                }
                Matcher heading = HEADING.matcher(line);
                if (heading.matches() && !heading.group(2).trim().isEmpty()) {
                    flushParagraph();
                    startSection(heading.group(1).length(), heading.group(2).trim());
                    continue;
                }
                if (trimmed.isEmpty()) {
                    flushParagraph();
                } else {
                    paragraph.add(line);
                }
            }
            if (fence != null) {
                addCode(code); // nicht geschlossener Block: Inhalt trotzdem behalten
            }
            flushParagraph();
            finishSection();
            return sections;
        }

        private void startSection(int level, String title) {
            finishSection();
            while (!headingLevels.isEmpty() && headingLevels.get(headingLevels.size() - 1) >= level) {
                headingLevels.remove(headingLevels.size() - 1);
                headingTitles.remove(headingTitles.size() - 1);
            }
            headingLevels.add(level);
            headingTitles.add(title);
            section = new Section(new ArrayList<String>(headingTitles));
        }

        private void finishSection() {
            if (!section.units.isEmpty()) {
                sections.add(section);
            }
            section = new Section(section.headingPath);
            blockBreakPending = false;
        }

        private void flushParagraph() {
            if (paragraph.isEmpty()) {
                return;
            }
            if (isList(paragraph)) {
                for (String item : listItems(paragraph)) {
                    addSentences(item, LINE_SEPARATOR);
                }
            } else {
                StringBuilder joined = new StringBuilder();
                for (String line : paragraph) {
                    if (joined.length() > 0) {
                        joined.append(' ');
                    }
                    joined.append(line.trim());
                }
                addSentences(joined.toString(), SENTENCE_SEPARATOR);
            }
            paragraph.clear();
            blockBreakPending = true;
        }

        private void addSentences(String text, String firstSeparator) {
            String separator = firstSeparator;
            for (String sentence : sentenceSplitter.split(text)) {
                add(sentence, separator);
                separator = SENTENCE_SEPARATOR;
            }
        }

        /** Code bleibt zeilenweise zusammen; Gruppen nur, wenn der Block nicht in ein Budget passt. */
        private void addCode(List<String> lines) {
            flushParagraph();
            StringBuilder block = new StringBuilder();
            for (String line : lines) {
                String content = rtrim(line);
                if (content.isEmpty()) {
                    continue;
                }
                if (block.length() > 0) {
                    block.append('\n');
                }
                block.append(content);
            }
            if (block.length() == 0) {
                return;
            }
            int tokens = tokenCounter.count(block.toString());
            if (tokens <= policy.maxTokens() / 2) {
                add(block.toString(), LINE_SEPARATOR);
            } else {
                for (String line : block.toString().split("\n")) {
                    add(line, LINE_SEPARATOR);
                }
            }
            blockBreakPending = true;
        }

        private void add(String text, String separator) {
            String effective = blockBreakPending ? BLOCK_SEPARATOR : separator;
            blockBreakPending = false;
            section.units.add(new Unit(text, effective));
        }
    }

    private static boolean isList(List<String> lines) {
        return LIST_ITEM.matcher(lines.get(0)).matches();
    }

    /** Listenpunkte; Folgezeilen ohne Aufzählungszeichen gehören zum vorigen Punkt. */
    private static List<String> listItems(List<String> lines) {
        List<String> items = new ArrayList<String>();
        for (String line : lines) {
            if (LIST_ITEM.matcher(line).matches() || items.isEmpty()) {
                items.add(line.trim());
            } else {
                items.set(items.size() - 1, items.get(items.size() - 1) + " " + line.trim());
            }
        }
        return items;
    }

    private static String rtrim(String line) {
        int end = line.length();
        while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
            end--;
        }
        return line.substring(0, end);
    }

    private static final class Section {
        final List<String> headingPath;
        final List<Unit> units = new ArrayList<Unit>();

        Section(List<String> headingPath) {
            this.headingPath = headingPath;
        }
    }

    /** Ein Satz, Listenpunkt oder Code-Abschnitt samt Trenner zu seinem Vorgänger im Quelltext. */
    private static final class Unit {
        final String text;
        final String separator;

        Unit(String text, String separator) {
            this.text = text;
            this.separator = separator;
        }
    }
}

package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.acropolis.passage.TopicClusterer;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.astu.navigation.SourcePosition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Baut die Navigation einer Fassung (FA-01 bis FA-05). DIE STRUKTUR GEHT VOR (wie askai arch
 * {@code PassageSegmentation}): Originalüberschriften bilden die Hierarchie und bleiben unverändert. Semantisch verfeinert
 * wird nur der Fließtext eines Bereichs (vor der ersten Unterüberschrift bzw. das ganze Dokument ohne Überschriften),
 * und nur, wenn er mindestens {@link #MIN_TOPIC_PASSAGES} Passagen hat: die Passagen gruppiert {@link TopicClusterer}
 * (arch, unverändert); aufeinanderfolgende Passagen desselben Themas bilden einen Themenabschnitt. Kehrt ein Thema
 * später wieder, entsteht dort ein eigener Abschnitt: die Navigation verliert nie die Lesereihenfolge (FA-03).
 *
 * <p>Bezeichnungen der Themenabschnitte kommen aus der Analyse (erster Satz der repräsentativen Passage des Themas,
 * arch {@code suggestedTitle}); ein {@link SectionLabeler} darf sie ersetzen. Jeder Abschnitt trägt Resource-ID,
 * Capture-ID, Start-/Endposition in der kanonischen Textfassung und sein übergeordnetes Kapitel (FA-05).</p>
 *
 * <p>Ein einzelnes oberstes Kapitel, das das ganze Dokument umschließt (Seitentitel als Überschrift), wird
 * aufgelöst: seine Unterkapitel werden die Hauptkapitel, damit der Banner etwas zum Navigieren hat.</p>
 */
public final class DocumentNavigationBuilder {

    /** Ab so vielen Passagen lohnt eine Unterteilung eines Bereichs in Themenabschnitte. */
    public static final int MIN_TOPIC_PASSAGES = 3;
    private static final int ANCHOR_CHARS = 60;

    private final TopicClusterer clusterer;

    public DocumentNavigationBuilder() {
        this(new TopicClusterer());
    }

    public DocumentNavigationBuilder(TopicClusterer clusterer) {
        if (clusterer == null) {
            throw new IllegalArgumentException("clusterer must not be null");
        }
        this.clusterer = clusterer;
    }

    /** Nur Originalüberschriften (Rückfall, FA-01); {@code reason} sagt warum (leer: Analyse läuft noch). */
    public DocumentNavigation headingsOnly(String resourceId, String captureId, String title, String text,
                                           String reason) {
        Context context = new Context(resourceId, captureId, text);
        List<Node> tree = tree(context, MarkdownHeadings.of(text));
        List<NavigationSection> chapters = new ArrayList<NavigationSection>();
        for (Node node : promote(tree)) {
            chapters.add(toSection(context, node, 1, null, Collections.<Node, List<NavigationSection>>emptyMap()));
        }
        return new DocumentNavigation(resourceId, captureId, title, chapters, DocumentNavigation.Analysis.HEADINGS,
                reason);
    }

    /**
     * Originalüberschriften plus Themenabschnitte aus den Passagen dieser Fassung.
     *
     * @param passages die Passagen der Index-Pipeline in Lesereihenfolge, nicht leer
     */
    public DocumentNavigation build(String resourceId, String captureId, String title, String text,
                                    List<NavigationPassage> passages, SectionLabeler labeler) {
        Context context = new Context(resourceId, captureId, text);
        List<Node> roots = tree(context, MarkdownHeadings.of(text));
        List<Node> top = promote(roots);

        // Passagen in Lesereihenfolge verorten und dem tiefsten Bereich zuordnen, in dessen Fließtext sie beginnen.
        TextLocator locator = context.locator;
        int cursor = 0;
        Map<Node, List<Located>> byNode = new HashMap<Node, List<Located>>();
        List<Located> preamble = new ArrayList<Located>();
        for (NavigationPassage passage : passages) {
            TextLocator.Span span = locator.find(passage.text(), cursor);
            if (span == null) {
                continue; // nicht im angezeigten Text (z. B. Bildbeschreibung): nicht anspringbar, weglassen
            }
            cursor = span.start;
            Located located = new Located(passage, span);
            Node owner = bodyOwner(roots, span.start);
            if (owner == null) {
                preamble.add(located);
            } else {
                List<Located> list = byNode.get(owner);
                if (list == null) {
                    list = new ArrayList<Located>();
                    byNode.put(owner, list);
                }
                list.add(located);
            }
        }

        // Themenabschnitte je Bereich; der Text je Abschnitt geht an den Benenner.
        List<TopicRun> allRuns = new ArrayList<TopicRun>();
        Map<Node, List<TopicRun>> runsByNode = new HashMap<Node, List<TopicRun>>();
        List<Node> wrappers = wrappers(roots);
        for (Map.Entry<Node, List<Located>> entry : byNode.entrySet()) {
            // Fließtext eines aufgelösten Seitentitel-Kapitels steht wie Vortext vor den Hauptkapiteln.
            if (wrappers.contains(entry.getKey())) {
                preamble.addAll(entry.getValue());
                continue;
            }
            List<TopicRun> runs = topicRuns(entry.getValue(), false);
            runsByNode.put(entry.getKey(), runs);
            allRuns.addAll(runs);
        }
        Collections.sort(preamble, new java.util.Comparator<Located>() {
            @Override
            public int compare(Located a, Located b) {
                return Integer.compare(a.span.start, b.span.start);
            }
        });
        List<TopicRun> preambleRuns = topicRuns(preamble, !roots.isEmpty());
        allRuns.addAll(preambleRuns);
        applyLabels(title, allRuns, labeler);

        Map<Node, List<NavigationSection>> topicSections = toTopicSections(context, runsByNode);
        List<NavigationSection> chapters = new ArrayList<NavigationSection>();
        for (TopicRun run : preambleRuns) {
            chapters.add(run.toSection(context, 1, null));
        }
        for (Node node : top) {
            chapters.add(toSection(context, node, 1, null, topicSections));
        }
        boolean semantic = !allRuns.isEmpty();
        return new DocumentNavigation(resourceId, captureId, title, chapters,
                semantic ? DocumentNavigation.Analysis.SEMANTIC : DocumentNavigation.Analysis.HEADINGS,
                semantic ? "" : "Keine Themenwechsel gefunden: die Originalüberschriften reichen.");
    }

    // ------------------------------------------------------------------ Überschriften-Baum

    private static final class Context {
        final String resourceId;
        final String captureId;
        final String text;
        final TextLocator locator;

        Context(String resourceId, String captureId, String text) {
            this.resourceId = resourceId;
            this.captureId = captureId;
            this.text = text == null ? "" : text;
            this.locator = new TextLocator(this.text);
        }
    }

    private static final class Node {
        final int ordinal;
        final MarkdownHeadings.Heading heading;
        final List<Node> children = new ArrayList<Node>();
        int end;

        Node(int ordinal, MarkdownHeadings.Heading heading) {
            this.ordinal = ordinal;
            this.heading = heading;
        }

        /** Ende des eigenen Fließtexts: erste Unterüberschrift oder Bereichsende. */
        int bodyEnd() {
            return children.isEmpty() ? end : children.get(0).heading.start;
        }
    }

    private static List<Node> tree(Context context, List<MarkdownHeadings.Heading> headings) {
        List<Node> roots = new ArrayList<Node>();
        List<Node> stack = new ArrayList<Node>();
        int ordinal = 0;
        for (MarkdownHeadings.Heading heading : headings) {
            Node node = new Node(ordinal++, heading);
            while (!stack.isEmpty() && stack.get(stack.size() - 1).heading.level >= heading.level) {
                stack.remove(stack.size() - 1).end = heading.start;
            }
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.get(stack.size() - 1).children.add(node);
            }
            stack.add(node);
        }
        for (Node open : stack) {
            open.end = context.text.length();
        }
        return roots;
    }

    /** Ein einziges oberstes Kapitel mit Unterkapiteln umschließt das Dokument: seine Kinder werden Hauptkapitel. */
    private static List<Node> promote(List<Node> roots) {
        List<Node> top = roots;
        while (top.size() == 1 && !top.get(0).children.isEmpty()) {
            top = top.get(0).children;
        }
        return top;
    }

    /** Die Kapitel, die {@link #promote} auflöst (in dieser Reihenfolge verschachtelt). */
    private static List<Node> wrappers(List<Node> roots) {
        List<Node> wrappers = new ArrayList<Node>();
        List<Node> top = roots;
        while (top.size() == 1 && !top.get(0).children.isEmpty()) {
            wrappers.add(top.get(0));
            top = top.get(0).children;
        }
        return wrappers;
    }

    /** Der tiefste Bereich, in dessen eigenem Fließtext {@code position} liegt; {@code null}: vor der ersten Überschrift. */
    private static Node bodyOwner(List<Node> nodes, int position) {
        for (Node node : nodes) {
            if (position >= node.heading.start && position < node.end) {
                if (position < node.bodyEnd()) {
                    return node;
                }
                Node inner = bodyOwner(node.children, position);
                return inner == null ? node : inner;
            }
        }
        return null;
    }

    private NavigationSection toSection(Context context, Node node, int level, String parentId,
                                        Map<Node, List<NavigationSection>> topics) {
        String id = context.resourceId + "@" + context.captureId + "#h" + node.ordinal;
        List<NavigationSection> children = new ArrayList<NavigationSection>();
        List<NavigationSection> own = topics.get(node);
        if (own != null) {
            for (NavigationSection topic : own) {
                children.add(relevel(topic, level + 1, id));
            }
        }
        for (Node child : node.children) {
            children.add(toSection(context, child, level + 1, id, topics));
        }
        SourcePosition position = new SourcePosition(context.resourceId, context.captureId, node.heading.start,
                node.end, node.heading.text);
        return new NavigationSection(id, node.heading.text, NavigationSection.Origin.HEADING, level, position,
                parentId, children);
    }

    private static NavigationSection relevel(NavigationSection section, int level, String parentId) {
        return new NavigationSection(section.id(), section.label(), section.origin(), level, section.position(),
                parentId, section.children());
    }

    // ------------------------------------------------------------------ Themenabschnitte

    private static final class Located {
        final NavigationPassage passage;
        final TextLocator.Span span;

        Located(NavigationPassage passage, TextLocator.Span span) {
            this.passage = passage;
            this.span = span;
        }
    }

    /** Aufeinanderfolgende Passagen eines Themas. */
    private static final class TopicRun {
        final List<Located> members = new ArrayList<Located>();
        String label;

        String text() {
            StringBuilder text = new StringBuilder();
            for (Located member : members) {
                if (text.length() > 0) {
                    text.append(' ');
                }
                text.append(member.passage.text());
            }
            return text.toString();
        }

        NavigationSection toSection(Context context, int level, String parentId) {
            Located first = members.get(0);
            Located last = members.get(members.size() - 1);
            SourcePosition position = new SourcePosition(context.resourceId, context.captureId, first.span.start,
                    Math.max(first.span.start, last.span.end), context.locator.anchorAt(first.span.start,
                    ANCHOR_CHARS));
            return new NavigationSection(first.passage.id(), label, NavigationSection.Origin.TOPIC, level, position,
                    parentId, null);
        }
    }

    /**
     * Themenabschnitte eines Bereichs: Clustering wie arch, dann Läufe gleicher Themen in Lesereihenfolge. Ein Bereich
     * mit nur einem Lauf bleibt ungeteilt (nichts zu navigieren), außer {@code keepSingle} (Text vor der ersten
     * Überschrift, sonst nicht anspringbar).
     */
    private List<TopicRun> topicRuns(List<Located> located, boolean keepSingle) {
        if (located.isEmpty() || (located.size() < MIN_TOPIC_PASSAGES && !keepSingle)) {
            return Collections.emptyList();
        }
        List<TopicClusterer.Member> members = new ArrayList<TopicClusterer.Member>();
        for (Located item : located) {
            members.add(new TopicClusterer.Member(item.passage.id(), item.passage.text(), item.passage.vector()));
        }
        // Unter der Mindestzahl (nur Vortext) bleibt jede Passage ohne Thema: ein Lauf je Passage wäre zu fein,
        // deshalb dort alle Passagen als ein Lauf.
        List<TopicClusterer.Topic> topics = located.size() < MIN_TOPIC_PASSAGES
                ? Collections.<TopicClusterer.Topic>emptyList() : clusterer.cluster(members);
        Map<String, TopicClusterer.Topic> topicOf = new HashMap<String, TopicClusterer.Topic>();
        for (TopicClusterer.Topic topic : topics) {
            for (String member : topic.memberIds()) {
                topicOf.put(member, topic);
            }
        }
        List<TopicRun> runs = new ArrayList<TopicRun>();
        TopicRun current = null;
        TopicClusterer.Topic currentTopic = null;
        for (Located item : located) {
            TopicClusterer.Topic topic = topicOf.get(item.passage.id());
            if (current == null || topic != currentTopic) {
                current = new TopicRun();
                runs.add(current);
                currentTopic = topic;
            }
            current.members.add(item);
        }
        if (runs.size() < 2 && !keepSingle) {
            return Collections.emptyList();
        }
        for (TopicRun run : runs) {
            run.label = analysisLabel(run, topicOf);
        }
        return runs;
    }

    /** arch {@code suggestedTitle}: erster Satz des Repräsentanten des Themas, sofern er in diesem Lauf liegt. */
    private static String analysisLabel(TopicRun run, Map<String, TopicClusterer.Topic> topicOf) {
        Located first = run.members.get(0);
        TopicClusterer.Topic topic = topicOf.get(first.passage.id());
        if (topic != null) {
            for (String representative : topic.representativeIds()) {
                for (Located member : run.members) {
                    if (member.passage.id().equals(representative)) {
                        return firstSentence(member.passage.text());
                    }
                }
            }
        }
        return firstSentence(first.passage.text());
    }

    /** Wie arch {@code TopicClusterer.suggestedTitle}: bis zum ersten Punkt, höchstens 80 Zeichen. */
    static String firstSentence(String text) {
        String trimmed = text.trim();
        int end = trimmed.indexOf('.');
        String head = end > 0 ? trimmed.substring(0, end) : trimmed;
        return head.length() <= 80 ? head : head.substring(0, 80);
    }

    private static void applyLabels(String title, List<TopicRun> runs, SectionLabeler labeler) {
        if (runs.isEmpty() || labeler == null) {
            return;
        }
        List<String> texts = new ArrayList<String>(runs.size());
        for (TopicRun run : runs) {
            texts.add(run.text());
        }
        List<String> labels;
        try {
            labels = labeler.labels(title, texts);
        } catch (RuntimeException failed) {
            return; // Benennung ist Kür: die Bezeichnungen der Analyse bleiben
        }
        if (labels == null) {
            return;
        }
        for (int i = 0; i < runs.size() && i < labels.size(); i++) {
            String label = labels.get(i);
            if (label != null && !label.trim().isEmpty()) {
                runs.get(i).label = label.trim();
            }
        }
    }

    private static Map<Node, List<NavigationSection>> toTopicSections(Context context,
                                                                     Map<Node, List<TopicRun>> runsByNode) {
        Map<Node, List<NavigationSection>> sections = new HashMap<Node, List<NavigationSection>>();
        for (Map.Entry<Node, List<TopicRun>> entry : runsByNode.entrySet()) {
            List<NavigationSection> list = new ArrayList<NavigationSection>();
            for (TopicRun run : entry.getValue()) {
                list.add(run.toSection(context, 1, null)); // Ebene und Kapitel setzt toSection(Node)
            }
            sections.put(entry.getKey(), list);
        }
        return sections;
    }
}

package com.aresstack.enterpriseai.astu.acropolis.passage;

import com.aresstack.enterpriseai.astu.embedding.EmbeddingVector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Hierarchisches AGGLOMERATIVES Clustering über Passagenvektoren mit Cosinus-Distanz und Average Linkage, aus
 * askai-java8 arch {@code TopicClusterer} (Ablauf wie arch; Cosinus und Mittelwert über {@link VectorMath}, das nicht
 * wörtlich übernommen ist): keine feste Themenzahl, kleine Einzelthemen bleiben sichtbar. Deterministisch: Gleichstände entscheidet die Eingabereihenfolge. Titel und Zusammenfassung stammen aus
 * dem repräsentativen Mitglied (dem Schwerpunkt am nächsten); die Mitgliedschaft hängt nie von dieser Formulierung ab.
 *
 * <p>Angepasst an den Client: Eingabe sind {@link Member} (ID, Text, {@link EmbeddingVector}) statt research-domain
 * {@code Passage} plus Vektorkarte; Ergebnis sind {@link Topic} statt {@code TopicProposal}; ohne {@code IdSequence},
 * die Kennung eines Themas ist die ID seines ersten Mitglieds in Eingabereihenfolge (wie arch
 * {@code LiveOutlineProjectionBuilder}: Identität aus den Mitgliedern, kein Zähler).</p>
 */
public final class TopicClusterer {

    /** Eine zu gruppierende Passage. */
    public static final class Member {
        private final String id;
        private final String text;
        private final EmbeddingVector vector;

        public Member(String id, String text, EmbeddingVector vector) {
            if (id == null || text == null || vector == null) {
                throw new IllegalArgumentException("id, text und vector sind Pflicht");
            }
            this.id = id;
            this.text = text;
            this.vector = vector;
        }

        public String id() {
            return id;
        }

        public String text() {
            return text;
        }

        public EmbeddingVector vector() {
            return vector;
        }
    }

    /** Ein gefundenes Thema (arch {@code TopicProposal} ohne Lebenszyklus). */
    public static final class Topic {
        private final String topicId;
        private final List<String> memberIds;
        private final List<String> representativeIds;
        private final String suggestedTitle;
        private final String suggestedSummary;
        private final double confidence;

        Topic(String topicId, List<String> memberIds, List<String> representativeIds, String suggestedTitle,
              String suggestedSummary, double confidence) {
            this.topicId = topicId;
            this.memberIds = Collections.unmodifiableList(memberIds);
            this.representativeIds = Collections.unmodifiableList(representativeIds);
            this.suggestedTitle = suggestedTitle;
            this.suggestedSummary = suggestedSummary;
            this.confidence = confidence;
        }

        public String topicId() {
            return topicId;
        }

        /** Die Mitglieder in Verschmelzungsreihenfolge (wie arch). */
        public List<String> memberIds() {
            return memberIds;
        }

        /** Die dem Schwerpunkt nächsten Mitglieder, das nächste zuerst. */
        public List<String> representativeIds() {
            return representativeIds;
        }

        /** Belegnaher Titel: der erste Satz des Repräsentanten, höchstens 80 Zeichen. */
        public String suggestedTitle() {
            return suggestedTitle;
        }

        public String suggestedSummary() {
            return suggestedSummary;
        }

        /** Mittlere Ähnlichkeit der Mitglieder zum Schwerpunkt. */
        public double confidence() {
            return confidence;
        }
    }

    /** arch-Standard: Verschmelzen bis zur Cosinus-Distanz 0,55. */
    public static final double DEFAULT_MERGE_DISTANCE_CUTOFF = 0.55;
    /** arch-Standard: zwei Repräsentanten je Thema. */
    public static final int DEFAULT_REPRESENTATIVES = 2;

    private final double mergeDistanceCutoff;
    private final int representativesPerTopic;

    public TopicClusterer() {
        this(DEFAULT_MERGE_DISTANCE_CUTOFF, DEFAULT_REPRESENTATIVES);
    }

    public TopicClusterer(double mergeDistanceCutoff, int representativesPerTopic) {
        this.mergeDistanceCutoff = mergeDistanceCutoff;
        this.representativesPerTopic = representativesPerTopic;
    }

    public List<Topic> cluster(List<Member> members) {
        List<List<Member>> clusters = new ArrayList<List<Member>>();
        for (Member member : members) {
            List<Member> singleton = new ArrayList<Member>();
            singleton.add(member);
            clusters.add(singleton);
        }
        // Agglomerieren: immer das gerade nächste Paar verschmelzen, bis die Grenzdistanz erreicht ist.
        while (clusters.size() > 1) {
            int bestA = -1;
            int bestB = -1;
            double bestDistance = Double.MAX_VALUE;
            for (int a = 0; a < clusters.size(); a++) {
                for (int b = a + 1; b < clusters.size(); b++) {
                    double distance = averageLinkageDistance(clusters.get(a), clusters.get(b));
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        bestA = a;
                        bestB = b;
                    }
                }
            }
            if (bestDistance > mergeDistanceCutoff) {
                break;
            }
            clusters.get(bestA).addAll(clusters.remove(bestB));
        }

        List<Topic> topics = new ArrayList<Topic>();
        for (List<Member> cluster : clusters) {
            topics.add(toTopic(cluster, members));
        }
        return topics;
    }

    private static double averageLinkageDistance(List<Member> a, List<Member> b) {
        double sum = 0;
        int pairs = 0;
        for (Member pa : a) {
            for (Member pb : b) {
                sum += 1.0 - VectorMath.cosine(pa.vector(), pb.vector());
                pairs++;
            }
        }
        return sum / pairs;
    }

    private Topic toTopic(List<Member> cluster, List<Member> inputOrder) {
        List<EmbeddingVector> memberVectors = new ArrayList<EmbeddingVector>();
        List<String> memberIds = new ArrayList<String>();
        for (Member member : cluster) {
            memberIds.add(member.id());
            memberVectors.add(member.vector());
        }
        final EmbeddingVector centroid = VectorMath.mean(memberVectors);
        // Repräsentanten: die dem Schwerpunkt nächsten Mitglieder, Gleichstände nach Einfügereihenfolge.
        List<Member> byCentroid = new ArrayList<Member>(cluster);
        Collections.sort(byCentroid, new Comparator<Member>() {
            public int compare(Member a, Member b) {
                double da = 1.0 - VectorMath.cosine(a.vector(), centroid);
                double db = 1.0 - VectorMath.cosine(b.vector(), centroid);
                return Double.compare(da, db);
            }
        });
        List<String> representatives = new ArrayList<String>();
        for (int i = 0; i < Math.min(representativesPerTopic, byCentroid.size()); i++) {
            representatives.add(byCentroid.get(i).id());
        }
        double intra = 0;
        for (EmbeddingVector vector : memberVectors) {
            intra += VectorMath.cosine(vector, centroid);
        }
        double confidence = intra / memberVectors.size();
        String title = suggestedTitle(byCentroid.get(0));
        String topicId = firstInInputOrder(cluster, inputOrder);
        return new Topic(topicId, memberIds, representatives, title, byCentroid.get(0).text(), confidence);
    }

    private static String firstInInputOrder(List<Member> cluster, List<Member> inputOrder) {
        for (Member member : inputOrder) {
            if (cluster.contains(member)) {
                return member.id();
            }
        }
        return cluster.get(0).id();
    }

    /** Deterministischer, belegnaher Titel (arch): der erste Satz des Repräsentanten, gekürzt. */
    static String suggestedTitle(Member representative) {
        String text = representative.text();
        int end = text.indexOf('.');
        String head = end > 0 ? text.substring(0, end) : text;
        return head.length() <= 80 ? head : head.substring(0, 80);
    }
}

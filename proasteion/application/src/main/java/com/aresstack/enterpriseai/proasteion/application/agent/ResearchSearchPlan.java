package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.concept.ConceptTopicScanner;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Die Suchen eines autonomen Laufs aus dem Konzept: statt den ganzen Konzepttext als eine Suchanfrage an die
 * Wissensquellen zu schicken (Confluence verknüpft die Wörter einer Volltextsuche), sucht der Lauf je Thema und
 * Unterthema: erst die Wurzelthemen, dann „Wurzel + Unterthema“. Ausgeschlossene Karten (Weidezaun) erzeugen keine
 * Suche. Ohne Konzept bleibt der Auftrag selbst die eine Suche. Eigene Festlegung dieses Clients (askai arch
 * suchte im Web mit der Nutzerfrage).
 */
public final class ResearchSearchPlan {

    /** Höchstens so viele Suchen je Lauf; das Seitenbudget gilt zusätzlich. */
    static final int MAX_QUERIES = 12;
    private static final int MAX_MISSION_WORDS = 8;

    private ResearchSearchPlan() {
    }

    public static List<String> queries(String conceptDocumentJson, String mission, Collection<String> exclusions) {
        Set<String> excluded = new HashSet<String>();
        if (exclusions != null) {
            for (String exclusion : exclusions) {
                excluded.add(normalize(exclusion));
            }
        }
        LinkedHashSet<String> queries = new LinkedHashSet<String>();
        String shortMission = mission == null ? "" : mission.trim();
        if (!shortMission.isEmpty() && shortMission.split("\\s+").length <= MAX_MISSION_WORDS) {
            queries.add(shortMission);
        }
        List<List<String>> paths = ConceptTopicScanner.collectCardPaths(conceptDocumentJson);
        List<String> combined = new ArrayList<String>();
        for (List<String> path : paths) {
            if (path.isEmpty() || isExcluded(path, excluded)) {
                continue;
            }
            String root = path.get(0).trim();
            if (path.size() == 1) {
                add(queries, root);
            } else {
                String leaf = path.get(path.size() - 1).trim();
                combined.add(leaf.toLowerCase(Locale.ROOT).contains(root.toLowerCase(Locale.ROOT))
                        ? leaf : root + " " + leaf);
            }
        }
        for (String query : combined) {
            add(queries, query);
        }
        List<String> plan = new ArrayList<String>(queries);
        return plan.size() > MAX_QUERIES ? new ArrayList<String>(plan.subList(0, MAX_QUERIES)) : plan;
    }

    private static void add(Set<String> queries, String query) {
        String trimmed = query == null ? "" : query.trim();
        if (!trimmed.isEmpty()) {
            queries.add(trimmed);
        }
    }

    private static boolean isExcluded(List<String> path, Set<String> excluded) {
        for (String card : path) {
            if (excluded.contains(normalize(card))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

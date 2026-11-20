package com.aresstack.enterpriseai.proasteion.emporion.holkas.jira;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResourceId;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Ressourcen-IDs und Startpunkte der Jira-Quelle (paketintern): {@code jira:<locator>/issue/<KEY-123>}, der Locator
 * ist die kanonische Basis-URL ohne Schema. Startpunkte: {@code project:*} (alle Projekte außer den abgehakten),
 * {@code project:KEY}, ein Vorgangsschlüssel ({@code KEY-123}, {@code issue:KEY-123}), {@code jql:<Abfrage>} oder
 * eine Ressourcen-ID dieser Quelle.
 */
final class JiraIds {

    static final String SCHEME = "jira";
    static final String ALL_PROJECTS = "project:*";
    static final String PROJECT_PREFIX = "project:";
    static final String ISSUE_PREFIX = "issue:";
    static final String JQL_PREFIX = "jql:";

    private static final Pattern ISSUE_KEY = Pattern.compile("[A-Z][A-Z0-9_]*-[0-9]{1,12}");
    private static final Pattern PROJECT_KEY = Pattern.compile("[A-Z][A-Z0-9_]{0,254}");

    private JiraIds() {
    }

    static KnowledgeResourceId issue(String locator, String key) {
        return KnowledgeResourceId.of(SCHEME, locator + "/issue/" + key);
    }

    static boolean isIssueKey(String value) {
        return value != null && ISSUE_KEY.matcher(value).matches();
    }

    static boolean isProjectKey(String value) {
        return value != null && PROJECT_KEY.matcher(value).matches();
    }

    /** Der Vorgangsschlüssel einer ID dieser Quelle oder {@code null}, wenn die ID fremd oder unbekannt geformt ist. */
    static String issueKey(String locator, KnowledgeResourceId id) {
        if (id == null || !SCHEME.equals(id.scheme())) {
            return null;
        }
        String prefix = locator + "/issue/";
        String rest = id.schemeSpecificPart();
        if (!rest.startsWith(prefix)) {
            return null;
        }
        String key = rest.substring(prefix.length());
        return isIssueKey(key) ? key : null;
    }

    /** Ein Startpunkt oder {@code null}, wenn er keine Form dieser Quelle hat. */
    static StartPoint startPoint(String locator, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        if (ALL_PROJECTS.equalsIgnoreCase(value)) {
            return StartPoint.allProjects();
        }
        if (value.regionMatches(true, 0, JQL_PREFIX, 0, JQL_PREFIX.length())) {
            String jql = value.substring(JQL_PREFIX.length()).trim();
            return jql.isEmpty() ? null : StartPoint.jql(jql);
        }
        if (value.startsWith(SCHEME + ":")) {
            String key;
            try {
                key = issueKey(locator, KnowledgeResourceId.of(value));
            } catch (IllegalArgumentException e) {
                return null;
            }
            return key == null ? null : StartPoint.issue(key);
        }
        if (value.regionMatches(true, 0, PROJECT_PREFIX, 0, PROJECT_PREFIX.length())) {
            String key = value.substring(PROJECT_PREFIX.length()).trim().toUpperCase(Locale.ROOT);
            return isProjectKey(key) ? StartPoint.project(key) : null;
        }
        if (value.regionMatches(true, 0, ISSUE_PREFIX, 0, ISSUE_PREFIX.length())) {
            String key = value.substring(ISSUE_PREFIX.length()).trim().toUpperCase(Locale.ROOT);
            return isIssueKey(key) ? StartPoint.issue(key) : null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        return isIssueKey(upper) ? StartPoint.issue(upper) : null;
    }

    /** Ein erkannter Startpunkt: genau eines der Felder ist gesetzt, oder {@link #allProjects}. */
    static final class StartPoint {
        final String projectKey;
        final String issueKey;
        final String jql;
        final boolean allProjects;

        private StartPoint(String projectKey, String issueKey, String jql, boolean allProjects) {
            this.projectKey = projectKey;
            this.issueKey = issueKey;
            this.jql = jql;
            this.allProjects = allProjects;
        }

        static StartPoint allProjects() {
            return new StartPoint(null, null, null, true);
        }

        static StartPoint project(String key) {
            return new StartPoint(key, null, null, false);
        }

        static StartPoint issue(String key) {
            return new StartPoint(null, key, null, false);
        }

        static StartPoint jql(String jql) {
            return new StartPoint(null, null, jql, false);
        }
    }
}

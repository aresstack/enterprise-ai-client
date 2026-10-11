package com.aresstack.enterpriseai.astu.navigation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Navigation einer Fassung (Capture) eines Dokuments: Hauptkapitel in Lesereihenfolge, jedes mit seinen
 * Unterabschnitten. {@link Analysis#SEMANTIC} heißt, dass automatisch erkannte Themenabschnitte ergänzt sind;
 * {@link Analysis#HEADINGS} ist der kontrollierte Rückfall auf die Originalüberschriften, {@link #fallbackReason()} sagt
 * warum.
 */
public final class DocumentNavigation {

    /** Wie weit die Analyse kam. */
    public enum Analysis {
        /** Nur Originalüberschriften (Rückfall oder noch nicht analysiert). */
        HEADINGS,
        /** Originalüberschriften plus semantische Themenabschnitte. */
        SEMANTIC
    }

    private final String resourceId;
    private final String captureId;
    private final String title;
    private final List<NavigationSection> chapters;
    private final Analysis analysis;
    private final String fallbackReason;

    public DocumentNavigation(String resourceId, String captureId, String title, List<NavigationSection> chapters,
                              Analysis analysis, String fallbackReason) {
        if (resourceId == null || captureId == null || analysis == null) {
            throw new IllegalArgumentException("resourceId, captureId und analysis sind Pflicht");
        }
        this.resourceId = resourceId;
        this.captureId = captureId;
        this.title = title == null ? "" : title;
        this.chapters = chapters == null ? Collections.<NavigationSection>emptyList()
                : Collections.unmodifiableList(new ArrayList<NavigationSection>(chapters));
        this.analysis = analysis;
        this.fallbackReason = fallbackReason == null ? "" : fallbackReason;
    }

    public String resourceId() {
        return resourceId;
    }

    /** Die Dokumentrevision: voller SHA-256 der gelesenen Fassung. */
    public String captureId() {
        return captureId;
    }

    public String title() {
        return title;
    }

    /** Die Hauptkapitel in Lesereihenfolge. */
    public List<NavigationSection> chapters() {
        return chapters;
    }

    public Analysis analysis() {
        return analysis;
    }

    /** Leer bei {@link Analysis#SEMANTIC}; sonst der Grund für den Rückfall (oder leer: noch keine Analyse). */
    public String fallbackReason() {
        return fallbackReason;
    }

    public boolean isEmpty() {
        return chapters.isEmpty();
    }

    /** Alle Abschnitte in Lesereihenfolge (Tiefensuche). */
    public List<NavigationSection> flatten() {
        List<NavigationSection> all = new ArrayList<NavigationSection>();
        collect(chapters, all);
        return all;
    }

    /** Der Abschnitt zur ID oder {@code null}. */
    public NavigationSection find(String sectionId) {
        for (NavigationSection section : flatten()) {
            if (section.id().equals(sectionId)) {
                return section;
            }
        }
        return null;
    }

    private static void collect(List<NavigationSection> sections, List<NavigationSection> into) {
        for (NavigationSection section : sections) {
            into.add(section);
            collect(section.children(), into);
        }
    }

    @Override
    public String toString() {
        return "DocumentNavigation{" + resourceId + ", " + analysis + ", " + chapters + "}";
    }
}

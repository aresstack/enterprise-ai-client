package com.aresstack.enterpriseai.source.api;

import com.aresstack.enterpriseai.astu.knowledge.KnowledgeResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Die Lese-Ansicht einer Seite ({@link PageViewSource}): bereinigter HTML-Körper, Basis für relative Adressen und
 * die Bilder in Dokumentreihenfolge. Jedes Bild nennt seine Adresse im HTML ({@code src}, gegen die Basis
 * aufgelöst; die Bytes liefert {@link PageViewSource#loadViewImage}) und, wenn die Quelle es als eigene Ressource
 * kennt (ein Anhang), diese Ressource mit Titel und Version.
 */
public final class PageView {

    private final String html;
    private final String baseUri;
    private final List<Image> images;

    public PageView(String html, String baseUri, List<Image> images) {
        this.html = html == null ? "" : html;
        this.baseUri = baseUri == null ? "" : baseUri;
        this.images = images == null ? Collections.<Image>emptyList()
                : Collections.unmodifiableList(new ArrayList<Image>(images));
    }

    /** Der HTML-Körper (ohne {@code <html>}/{@code <body>}), ohne Skripte und eingebettete Objekte. */
    public String html() {
        return html;
    }

    public String baseUri() {
        return baseUri;
    }

    public List<Image> images() {
        return images;
    }

    /** Ein Bild der Seite. */
    public static final class Image {
        private final String src;
        private final String title;
        private final KnowledgeResource resource;

        /** @param resource die Ressource des Bildes oder {@code null} (z. B. ein fremdes Bild) */
        public Image(String src, String title, KnowledgeResource resource) {
            if (src == null || src.isEmpty()) {
                throw new IllegalArgumentException("src is required");
            }
            this.title = title == null ? "" : title;
            this.src = src;
            this.resource = resource;
        }

        /** Die aufgelöste Adresse aus dem {@code <img>}. */
        public String src() {
            return src;
        }

        /** Alternativtext bzw. Titel; leer, wenn die Seite keinen nennt. */
        public String title() {
            return title;
        }

        /** Das Bild als Ressource der Quelle (ID, Titel, Medientyp, Version) oder {@code null}. */
        public KnowledgeResource resource() {
            return resource;
        }
    }
}

package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Bereitet {@code body.view} für die Lese-Ansicht auf (paketintern), mit den Regeln aus MainframeMate
 * {@code HtmlImageExtractor}/{@code ConfluenceReaderTab}: Bilder bleiben an ihrer Stelle, die Bildliste folgt der
 * Dokumentreihenfolge (jede Adresse einmal), {@code data:}-Bilder und Emoticons/Icons zählen nicht. Versteckte
 * Elemente ({@code .hidden}, {@code display:none}) fallen weg wie in {@link ConfluenceHtmlText}. Anders als
 * dort liest jsoup die Attribute; ist das Bild ein Anhang ({@code data-linked-resource-id}), kommen Anhang-ID,
 * Version und Medientyp mit. Entfernt werden Skripte, Formulare und eingebettete Objekte; Überschriften ohne
 * {@code id} bekommen eine aus ihrem Text (MainframeMate {@code injectHeadingAnchors}).
 */
final class ConfluencePageView {

    final String html;
    final List<InlineImage> images;

    private ConfluencePageView(String html, List<InlineImage> images) {
        this.html = html;
        this.images = images;
    }

    /** @param baseUri Basis-URL der Confluence-Instanz mit abschließendem {@code /} */
    static ConfluencePageView of(String bodyHtml, String baseUri) {
        Document doc = Jsoup.parseBodyFragment(bodyHtml == null ? "" : bodyHtml, baseUri);
        doc.select("script, noscript, iframe, object, embed, applet, form, input, button, select, textarea").remove();
        doc.select("style, svg").remove();
        // Wie ConfluenceHtmlText: Versteckte Lade-/Platzhalterzeilen und Bedienelemente nicht zeigen; Swings
        // HTML-Darstellung kennt display:none nicht zuverlässig.
        doc.select(".expand-control, .aui-icon, .hidden").remove();
        doc.select("[style~=(?i)display:\\s*none]").remove();
        List<InlineImage> images = new ArrayList<InlineImage>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Element img : doc.select("img")) {
            img.removeAttr("srcset");
            String raw = img.attr("src");
            String src = img.absUrl("src");
            if (src.isEmpty()) {
                src = raw;
            }
            img.attr("src", src);
            if (raw.startsWith("data:") || src.contains("/emoticons/") || src.contains("/icons/")) {
                continue;
            }
            if (!seen.add(src)) {
                continue;
            }
            String linkedType = img.attr("data-linked-resource-type").trim();
            String attachmentId = linkedType.isEmpty() || "attachment".equalsIgnoreCase(linkedType)
                    ? attachmentId(img.attr("data-linked-resource-id").trim()) : null;
            String title = img.attr("alt").trim();
            if (title.isEmpty()) {
                title = img.attr("title").trim();
            }
            if (title.isEmpty()) {
                title = img.attr("data-linked-resource-default-alias").trim();
            }
            images.add(new InlineImage(src, attachmentId, title,
                    img.attr("data-linked-resource-version").trim(),
                    img.attr("data-linked-resource-content-type").trim(),
                    img.attr("data-linked-resource-container-id").trim()));
        }
        Set<String> ids = new LinkedHashSet<String>();
        for (Element heading : doc.select("h1, h2, h3, h4, h5, h6")) {
            if (!heading.id().isEmpty()) {
                ids.add(heading.id());
                continue;
            }
            String anchor = heading.text().trim().replaceAll("[^\\p{L}\\p{N}]+", "-").toLowerCase(Locale.ROOT);
            if (anchor.isEmpty()) {
                continue;
            }
            String unique = anchor;
            for (int n = 2; !ids.add(unique); n++) {
                unique = anchor + "-" + n;
            }
            heading.attr("id", unique);
        }
        doc.outputSettings().prettyPrint(false);
        return new ConfluencePageView(doc.body().html(), images);
    }

    /**
     * Confluence nennt am {@code <img>} die nackte Content-ID, die REST-API die Form {@code att<id>}; {@code null}
     * ohne Anhang.
     */
    private static String attachmentId(String linked) {
        if (ConfluenceIds.isAttachmentId(linked)) {
            return linked;
        }
        return ConfluenceIds.isContentId(linked) ? "att" + linked : null;
    }

    /** Ein Bild der Seite mit den Angaben, die Confluence am {@code <img>} mitliefert. */
    static final class InlineImage {
        final String src;
        /** Anhang-ID ({@code att…}) oder {@code null}, wenn das Bild kein Anhang ist. */
        final String attachmentId;
        final String title;
        final String version;
        final String mediaType;
        final String containerId;

        InlineImage(String src, String attachmentId, String title, String version, String mediaType,
                    String containerId) {
            this.src = src;
            this.attachmentId = attachmentId;
            this.title = title;
            this.version = version;
            this.mediaType = mediaType;
            this.containerId = containerId;
        }
    }
}

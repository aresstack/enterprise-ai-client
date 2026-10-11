package com.aresstack.enterpriseai.proasteion.emporion.holkas.confluence;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

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

    private static final Pattern JIRA_PLACEHOLDER =
            Pattern.compile("(?i)^\\s*Getting issue details\\s*(\\.\\.\\.|\u2026)?\\s*$");

    /**
     * Jira-Makros, die Confluence nur im Browser per Script füllt ({@code body.view}, Rückfall ohne
     * {@code export_view}): „KEY - Getting issue details... STATUS“ wird zum Vorgangsschlüssel mit seinem Link,
     * ein Platzhalter ohne Vorgang fällt weg.
     */
    static void dropJiraPlaceholders(Document doc) {
        for (Element placeholder : doc.body().getElementsMatchingOwnText(JIRA_PLACEHOLDER)) {
            if (placeholder.parent() == null) {
                continue; // schon mit seinem Vorgang ersetzt
            }
            Element issue = placeholder.closest(".jira-issue, [data-jira-key]");
            if (issue == null) {
                placeholder.remove();
                continue;
            }
            Element key = issue.selectFirst("a.jira-issue-key, a[href*=/browse/]");
            issue.empty();
            if (key != null) {
                issue.appendChild(key);
            } else if (!issue.attr("data-jira-key").isEmpty()) {
                issue.appendText(issue.attr("data-jira-key"));
            }
        }
    }

    /** @param baseUri Basis-URL der Confluence-Instanz mit abschließendem {@code /} */
    static ConfluencePageView of(String bodyHtml, String baseUri) {
        return of(bodyHtml, baseUri, null);
    }

    /**
     * @param baseUri    eingestellte Basis-URL mit abschließendem {@code /}
     * @param serverBase {@code _links.base} der Antwort (die Basis, die Confluence selbst für sich eingestellt hat,
     *                   etwa anderer Hostname, Schema oder Port) oder {@code null}; Bildadressen darunter gehören zu
     *                   derselben Confluence und werden auf {@code baseUri} umgeschrieben (Pfad hinter dem
     *                   Kontextpfad und Query bleiben), damit sie über die eingestellte Adresse angemeldet laden.
     *                   Bilder anderer Hosts bleiben fremd.
     */
    static ConfluencePageView of(String bodyHtml, String baseUri, String serverBase) {
        Document doc = Jsoup.parseBodyFragment(bodyHtml == null ? "" : bodyHtml, baseUri);
        drawioPreviews(doc, baseUri); // vor dem Entfernen versteckter Elemente: die Diagrammdaten sind versteckt
        doc.select("script, noscript, iframe, object, embed, applet, form, input, button, select, textarea").remove();
        doc.select("style, svg").remove();
        // Wie ConfluenceHtmlText: Versteckte Lade-/Platzhalterzeilen und Bedienelemente nicht zeigen; Swings
        // HTML-Darstellung kennt display:none nicht zuverlässig.
        doc.select(".expand-control, .aui-icon, .hidden").remove();
        doc.select("[style~=(?i)display:\\s*none]").remove();
        dropJiraPlaceholders(doc);
        List<InlineImage> images = new ArrayList<InlineImage>();
        Set<String> seen = new LinkedHashSet<String>();
        for (Element img : doc.select("img")) {
            img.removeAttr("srcset");
            String raw = img.attr("src");
            String src = img.absUrl("src");
            if (src.isEmpty()) {
                src = raw;
            }
            src = ConfluenceRestClient.encodeUnsafe(src);
            src = onBase(onBase(src, serverBase, baseUri, true), baseUri, baseUri, false);
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
     * draw.io-Diagramme sind kein {@code <img>}: Der Browser zeichnet sie per Script aus den Diagrammdaten
     * ({@code /rest/drawio/...}), im HTML steht nur ein Platzhalter mit verstecktem {@code data-diagramdata}
     * (Base64-JSON). Dieses nennt die Vorschaudatei, die das Plugin als Anhang der Seite ablegt
     * ({@code previewName}, {@code owningPageId}, {@code revision}; bei {@code inc-drawio} eine fremde Seite). Für jeden Platzhalter ohne eigenes Bild wird diese Vorschau als
     * gewöhnliches Bild nach dem Makro eingefügt; sie lädt dann angemeldet wie jeder Anhang.
     */
    static void drawioPreviews(Document doc, String baseUri) {
        for (Element data : doc.select("[data-diagramdata]")) {
            JsonObject diagram;
            try {
                JsonElement parsed = JsonParser.parseString(new String(
                        Base64.getMimeDecoder().decode(data.attr("data-diagramdata")), StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) {
                    continue;
                }
                diagram = parsed.getAsJsonObject();
            } catch (RuntimeException e) {
                continue; // keine lesbaren Diagrammdaten: Platzhalter bleibt leer wie bisher
            }
            String preview = text(diagram, "previewName");
            String owner = text(diagram, "owningPageId");
            if (owner.isEmpty()) {
                owner = text(diagram, "ceoId");
            }
            if (preview.isEmpty() || !owner.matches("\\d+")) {
                continue;
            }
            Element macro = data.closest("[data-macro-name]");
            Element anchor = macro != null ? macro : data;
            if (!anchor.select("img").isEmpty()) {
                continue;
            }
            String name = text(diagram, "diagramName");
            String revision = text(diagram, "revision");
            // Wie das Plugin selbst (drawio-reader.js): die Vorschau genau der eingebundenen Diagrammrevision.
            String query = revision.matches("\\d+") ? "?version=" + revision + "&api=v2" : "";
            Element image = new Element("img")
                    .attr("src", baseUri + "download/attachments/" + owner + "/" + encodeSegment(preview) + query)
                    .attr("alt", name.isEmpty() ? preview : name)
                    .attr("data-linked-resource-default-alias", preview);
            anchor.after(new Element("p").appendChild(image));
        }
    }

    private static String text(JsonObject json, String key) {
        JsonElement value = json.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString().trim() : "";
    }

    /** Ein Pfadsegment: alles außer A–Z, a–z, 0–9 und {@code -._~} prozentkodiert (UTF-8). */
    static String encodeSegment(String segment) {
        StringBuilder out = new StringBuilder();
        for (byte b : segment.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xFF;
            if (c < 0x80 && (Character.isLetterOrDigit(c) || "-._~".indexOf(c) >= 0)) {
                out.append((char) c);
            } else {
                out.append('%').append(Character.toUpperCase(Character.forDigit(c >> 4, 16)))
                        .append(Character.toUpperCase(Character.forDigit(c & 0xF, 16)));
            }
        }
        return out.toString();
    }

    /**
     * {@code src} unter {@code from} (Host ohne Groß-/Kleinschreibung, Port mit Standardport gleichgesetzt, Pfad
     * unter dem Kontextpfad von {@code from}) als Adresse unter {@code to}; sonst {@code src} unverändert. {@code to} bestimmt das Schema der Anfrage. {@code anyScheme}: auch ein anderes Schema
     * gilt als dieselbe Basis (nur für die von Confluence selbst gemeldete Server-Basis); sonst muss es gleich sein.
     */
    static String onBase(String src, String from, String to, boolean anyScheme) {
        if (src == null || from == null || from.trim().isEmpty()) {
            return src;
        }
        URI image;
        URI base;
        try {
            image = new URI(src);
            base = new URI(from.trim());
        } catch (URISyntaxException e) {
            return src;
        }
        if (!anyScheme && (image.getScheme() == null || !image.getScheme().equalsIgnoreCase(base.getScheme()))) {
            return src;
        }
        if (image.getHost() == null || base.getHost() == null || !image.getHost().equalsIgnoreCase(base.getHost())
                || port(image) != port(base) && !(isDefaultPort(image) && isDefaultPort(base))) {
            return src;
        }
        String context = base.getRawPath() == null ? "" : base.getRawPath();
        context = context.endsWith("/") ? context.substring(0, context.length() - 1) : context;
        String path = image.getRawPath() == null ? "" : image.getRawPath();
        if (!path.startsWith(context + "/")) {
            return src;
        }
        String target = to.endsWith("/") ? to.substring(0, to.length() - 1) : to;
        return target + path.substring(context.length())
                + (image.getRawQuery() == null ? "" : "?" + image.getRawQuery());
    }

    private static int port(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean isDefaultPort(URI uri) {
        return uri.getPort() < 0 || uri.getPort() == 443 || uri.getPort() == 80;
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

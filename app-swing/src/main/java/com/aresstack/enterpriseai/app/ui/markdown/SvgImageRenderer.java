package com.aresstack.enterpriseai.app.ui.markdown;

import com.aresstack.Mermaid;

import java.awt.image.BufferedImage;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rendert rohes SVG aus Modellantworten über denselben Batik-Weg, den mermaid-java für Mermaid-Diagramme nutzt
 * ({@code Mermaid.svgToImage}). Damit erscheinen SVG-Grafiken wie Mermaid-Diagramme als Bild im Verlauf und öffnen
 * per Klick dasselbe Comic-Overlay.
 *
 * <p>Modelle schreiben oft „fast SVG“: HTML-Zeilenumbrüche ({@code <br/>}) in {@code <text>}, HTML-Entitäten,
 * nackte {@code &}. {@link #sanitize(String)} macht daraus gültiges SVG (Zeilen werden zu
 * {@code <tspan>}) und entfernt alles, was den Renderer ans Netz oder an Skripte bringen könnte: DOCTYPE/Entitäten,
 * {@code <script>}, {@code <foreignObject>}, {@code on*}-Attribute, externe {@code href}s und {@code url(...)}-
 * Verweise sowie {@code @import}. Der Renderer bleibt offline.</p>
 */
final class SvgImageRenderer implements MermaidImageRenderer {

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.DOTALL;
    private static final Pattern DOCTYPE = Pattern.compile("<!DOCTYPE(?:[^\\[>]*\\[.*?\\])?[^>]*>", FLAGS);
    private static final Pattern XML_PROLOG = Pattern.compile("<\\?xml.*?\\?>", FLAGS);
    private static final Pattern SCRIPT = Pattern.compile("<script\\b.*?(?:</script\\s*>|/>)", FLAGS);
    private static final Pattern FOREIGN_OBJECT =
            Pattern.compile("<foreignObject\\b.*?(?:</foreignObject\\s*>|/>)", FLAGS);
    private static final Pattern EVENT_ATTRIBUTE =
            Pattern.compile("\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*')", FLAGS);
    private static final Pattern HREF_ATTRIBUTE =
            Pattern.compile("\\s+((?:xlink:)?href)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')", FLAGS);
    private static final Pattern URL_REFERENCE = Pattern.compile("url\\(\\s*['\"]?([^)'\"]*)['\"]?\\s*\\)", FLAGS);
    private static final Pattern CSS_IMPORT = Pattern.compile("@import[^;]*;?", FLAGS);
    private static final Pattern TEXT_ELEMENT = Pattern.compile("<text\\b([^>]*)>(.*?)</text\\s*>", FLAGS);
    private static final Pattern LINE_BREAK = Pattern.compile("<br\\s*/?>|</br\\s*>", FLAGS);
    private static final Pattern X_ATTRIBUTE = Pattern.compile("\\sx\\s*=\\s*(\"[^\"]*\"|'[^']*')", FLAGS);
    private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!#\\d+;|#x[0-9a-fA-F]+;|[a-zA-Z][a-zA-Z0-9]*;)");
    private static final Pattern NAMED_ENTITY = Pattern.compile("&([a-zA-Z][a-zA-Z0-9]*);");

    @Override
    public BufferedImage render(String svgSource, int width) {
        String svg = sanitize(svgSource);
        return svg.isEmpty() ? null : Mermaid.svgToImage(svg, width);
    }

    /** @return {@code true}, wenn der Text (nach optionalem XML-Prolog und Kommentaren) mit {@code <svg} beginnt */
    static boolean looksLikeSvg(String source) {
        if (source == null) {
            return false;
        }
        String text = XML_PROLOG.matcher(source).replaceFirst("").trim();
        while (text.startsWith("<!--")) {
            int end = text.indexOf("-->");
            if (end < 0) {
                return false;
            }
            text = text.substring(end + 3).trim();
        }
        while (text.regionMatches(true, 0, "<!DOCTYPE", 0, 9)) {
            Matcher doctype = DOCTYPE.matcher(text);
            if (!doctype.lookingAt()) {
                return false;
            }
            text = text.substring(doctype.end()).trim();
        }
        return text.regionMatches(true, 0, "<svg", 0, 4)
                && (text.length() == 4 || !Character.isLetterOrDigit(text.charAt(4)));
    }

    /** Macht Modell-SVG zu gültigem, offline renderbarem SVG; {@code ""}, wenn kein {@code <svg>} enthalten ist. */
    static String sanitize(String source) {
        if (source == null) {
            return "";
        }
        int start = indexOfIgnoreCase(source, "<svg");
        int end = lastIndexOfIgnoreCase(source, "</svg");
        if (start < 0 || end < start) {
            return "";
        }
        int close = source.indexOf('>', end);
        String svg = source.substring(start, close < 0 ? source.length() : close + 1);
        if (close < 0) {
            svg = svg + ">";
        }
        svg = DOCTYPE.matcher(svg).replaceAll("");
        svg = SCRIPT.matcher(svg).replaceAll("");
        svg = FOREIGN_OBJECT.matcher(svg).replaceAll("");
        svg = EVENT_ATTRIBUTE.matcher(svg).replaceAll("");
        svg = CSS_IMPORT.matcher(svg).replaceAll("");
        svg = removeExternalHrefs(svg);
        svg = removeExternalUrls(svg);
        svg = replaceNamedEntities(svg);
        svg = BARE_AMPERSAND.matcher(svg).replaceAll("&amp;");
        svg = splitTextLines(svg);
        svg = LINE_BREAK.matcher(svg).replaceAll(" ");
        return svg;
    }

    private static String removeExternalHrefs(String svg) {
        Matcher matcher = HREF_ATTRIBUTE.matcher(svg);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
            matcher.appendReplacement(out, isLocalReference(value) ? Matcher.quoteReplacement(matcher.group()) : "");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String removeExternalUrls(String svg) {
        Matcher matcher = URL_REFERENCE.matcher(svg);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            matcher.appendReplacement(out, isLocalReference(value) ? Matcher.quoteReplacement(matcher.group()) : "none");
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /** Nur Verweise innerhalb des Dokuments ({@code #id}) und eingebettete Daten ({@code data:}) bleiben. */
    private static boolean isLocalReference(String value) {
        String trimmed = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("#") || trimmed.startsWith("data:image/");
    }

    private static String replaceNamedEntities(String svg) {
        Matcher matcher = NAMED_ENTITY.matcher(svg);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String name = matcher.group(1);
            int codePoint = entityCodePoint(name);
            String replacement = codePoint > 0 ? "&#" + codePoint + ";" : "&amp;" + name + ";";
            if (isXmlEntity(name)) {
                replacement = matcher.group();
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean isXmlEntity(String name) {
        return name.equals("amp") || name.equals("lt") || name.equals("gt") || name.equals("quot")
                || name.equals("apos");
    }

    private static int entityCodePoint(String name) {
        switch (name) {
            case "nbsp": return 160;
            case "auml": return 228;
            case "ouml": return 246;
            case "uuml": return 252;
            case "Auml": return 196;
            case "Ouml": return 214;
            case "Uuml": return 220;
            case "szlig": return 223;
            case "ndash": return 8211;
            case "mdash": return 8212;
            case "hellip": return 8230;
            case "bull": return 8226;
            case "middot": return 183;
            case "rarr": return 8594;
            case "larr": return 8592;
            case "harr": return 8596;
            case "uarr": return 8593;
            case "darr": return 8595;
            case "copy": return 169;
            case "reg": return 174;
            case "deg": return 176;
            case "times": return 215;
            case "euro": return 8364;
            case "laquo": return 171;
            case "raquo": return 187;
            case "bdquo": return 8222;
            case "ldquo": return 8220;
            case "rdquo": return 8221;
            default: return -1;
        }
    }

    /**
     * Ein {@code <br/>} in {@code <text>} ist kein SVG. Jede so getrennte Zeile wird ein {@code <tspan>} an derselben
     * x-Position; der Block bleibt um die ursprüngliche y-Position zentriert (so sitzen Beschriftungen weiter mittig
     * in ihren Kästen).
     */
    private static String splitTextLines(String svg) {
        Matcher matcher = TEXT_ELEMENT.matcher(svg);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String attributes = matcher.group(1);
            String body = matcher.group(2);
            if (!LINE_BREAK.matcher(body).find()) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            String[] lines = LINE_BREAK.split(body, -1);
            Matcher x = X_ATTRIBUTE.matcher(attributes);
            String xValue = x.find() ? x.group(1) : "\"0\"";
            StringBuilder text = new StringBuilder("<text").append(attributes).append('>');
            int count = 0;
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    count++;
                }
            }
            boolean first = true;
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String dy = first ? formatEm(-0.6d * (count - 1)) : "1.2em";
                text.append("<tspan x=").append(xValue).append(" dy=\"").append(dy).append("\">")
                        .append(trimmed).append("</tspan>");
                first = false;
            }
            text.append("</text>");
            matcher.appendReplacement(out, Matcher.quoteReplacement(text.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String formatEm(double value) {
        return String.format(Locale.ROOT, "%.1fem", value);
    }

    private static int indexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).indexOf(needle);
    }

    private static int lastIndexOfIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).lastIndexOf(needle);
    }
}

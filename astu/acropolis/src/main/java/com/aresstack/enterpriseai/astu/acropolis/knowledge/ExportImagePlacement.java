package com.aresstack.enterpriseai.astu.acropolis.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Places the figures of a page's reading view into its export Markdown (#132). The indexable text of a page carries no
 * images, so each figure is anchored by the words that precede it in the view HTML: the figure goes in after the
 * Markdown block (paragraph, list, table) where those words end, in the order of the page. A figure whose words cannot
 * be found goes after the last placed one; a figure before any text goes first. Works on any source's HTML, without
 * knowing the source.
 */
final class ExportImagePlacement {

    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]*>");
    private static final Pattern SKIPPED = Pattern.compile(
            "<(script|style|head)\\b.*?</\\1\\s*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+");
    /** How many preceding words anchor a figure. */
    private static final int ANCHOR_WORDS = 6;
    /** Fewer matching words than this are not trusted, unless the page has no more before the figure. */
    private static final int MIN_WORDS = 2;

    private ExportImagePlacement() {
    }

    /** Return {@code markdown} with a Markdown image for every figure of {@code images}. */
    static String place(String markdown, String html, List<ExportImage> images) {
        String text = markdown == null ? "" : markdown;
        if (images == null || images.isEmpty()) {
            return text;
        }
        String view = html == null ? "" : html;
        List<Integer> tags = imgTags(view);
        List<Word> words = words(text);
        StringBuilder out = new StringBuilder();
        int copied = 0; // characters of text already in out
        int searchFrom = 0; // word index after the last anchor
        int htmlFrom = 0;
        for (int i = 0; i < images.size(); i++) {
            ExportImage image = images.get(i);
            int at = position(view, image.src(), tags, i, htmlFrom);
            List<String> anchor = at < 0 ? null : lastWords(view.substring(0, at));
            if (at >= 0) {
                htmlFrom = at + 1;
            }
            int insert;
            if (anchor != null && anchor.isEmpty()) {
                insert = copied; // nothing precedes it on the page
            } else {
                int end = anchor == null ? -1 : find(words, anchor, searchFrom);
                if (end < 0) {
                    insert = Math.max(copied, text.length()); // unknown place: after what is there
                } else {
                    searchFrom = end + 1;
                    insert = Math.max(copied, blockEnd(text, words.get(end).end));
                }
            }
            out.append(text, copied, insert);
            copied = insert;
            out.append(separator(out)).append(image(image)).append("\n\n");
        }
        out.append(text.substring(copied));
        return out.toString().replaceAll("\\n{3,}", "\n\n").trim() + "\n";
    }

    /** Return the Markdown of one figure: {@code ![caption](<src>)}. */
    static String image(ExportImage image) {
        String caption = image.title().replaceAll("[\\[\\]\\r\\n]+", " ").trim();
        String src = image.src().replace("<", "%3C").replace(">", "%3E").replace("\n", "");
        return "![" + caption + "](<" + src + ">)";
    }

    private static String separator(StringBuilder out) {
        if (out.length() == 0) {
            return "";
        }
        return out.toString().endsWith("\n\n") ? "" : out.toString().endsWith("\n") ? "\n" : "\n\n";
    }

    private static List<Integer> imgTags(String html) {
        List<Integer> tags = new ArrayList<Integer>();
        Matcher matcher = IMG.matcher(html);
        while (matcher.find()) {
            tags.add(matcher.start());
        }
        return tags;
    }

    /** Where the figure stands in the HTML: by its address, its file name or else as the i-th image; -1 unknown. */
    private static int position(String html, String src, List<Integer> tags, int index, int from) {
        for (String candidate : candidates(src)) {
            int found = html.indexOf(candidate, from);
            if (found < 0) {
                found = html.indexOf(candidate);
            }
            if (found >= 0) {
                int tag = html.lastIndexOf('<', found);
                return tag < 0 ? found : tag;
            }
        }
        return index < tags.size() ? tags.get(index) : -1;
    }

    private static List<String> candidates(String src) {
        List<String> candidates = new ArrayList<String>();
        candidates.add(src);
        candidates.add(src.replace("&", "&amp;"));
        String path = src;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        if (file.length() >= 3) {
            candidates.add("/" + file);
        }
        return candidates;
    }

    private static List<String> lastWords(String html) {
        String visible = SKIPPED.matcher(html).replaceAll(" ");
        visible = TAG.matcher(visible).replaceAll(" ");
        visible = visible.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'");
        List<String> all = new ArrayList<String>();
        Matcher matcher = WORD.matcher(visible);
        while (matcher.find()) {
            all.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return new ArrayList<String>(all.subList(Math.max(0, all.size() - ANCHOR_WORDS), all.size()));
    }

    /** One word of the Markdown with where it ends. */
    private static final class Word {
        final String text;
        final int end;

        Word(String text, int end) {
            this.text = text;
            this.end = end;
        }
    }

    private static List<Word> words(String text) {
        List<Word> words = new ArrayList<Word>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            words.add(new Word(matcher.group().toLowerCase(Locale.ROOT), matcher.end()));
        }
        return words;
    }

    /** The index of the last word of the longest anchor suffix found from {@code from} on; -1 if none. */
    private static int find(List<Word> words, List<String> anchor, int from) {
        for (int length = anchor.size(); length >= Math.min(MIN_WORDS, anchor.size()); length--) {
            List<String> suffix = anchor.subList(anchor.size() - length, anchor.size());
            for (int start = from; start + length <= words.size(); start++) {
                boolean match = true;
                for (int k = 0; k < length && match; k++) {
                    match = words.get(start + k).text.equals(suffix.get(k));
                }
                if (match) {
                    return start + length - 1;
                }
            }
        }
        return -1;
    }

    /** The end of the Markdown block holding {@code offset}: the next blank line, or the end of the text. */
    private static int blockEnd(String text, int offset) {
        int blank = text.indexOf("\n\n", offset);
        if (isInFence(text, offset)) {
            int close = text.indexOf("\n```", offset);
            blank = close < 0 ? -1 : text.indexOf('\n', close + 4);
        }
        return blank < 0 ? text.length() : blank;
    }

    private static boolean isInFence(String text, int offset) {
        int fences = 0;
        int at = text.indexOf("```");
        while (at >= 0 && at < offset) {
            if (at == 0 || text.charAt(at - 1) == '\n') {
                fences++;
            }
            at = text.indexOf("```", at + 3);
        }
        return fences % 2 == 1;
    }
}

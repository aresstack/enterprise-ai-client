package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymForms;
import com.aresstack.enterpriseai.astu.acropolis.textmining.AcronymSettings;
import com.aresstack.enterpriseai.astu.acropolis.textmining.TermScanner;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;

import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Element;
import javax.swing.text.Highlighter;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The acronym marks of one rendered page (issue #107): pale pink highlights on the words of the rendered
 * {@link HTMLDocument} that are spelled exactly like an acronym the analysis found. The HTML is never rewritten; the
 * marks are highlighter entries next to those of the find bar, which stay visible through the translucent pink.
 *
 * <p>The analysis runs on the canonical text, the marks on the rendered text; they are joined only by exact spelling
 * at word boundaries ({@link TermScanner}), never by offsets. Words inside links, code, preformatted blocks and lines
 * in capitals are never marked, so an uncertain position marks nothing instead of the wrong text.</p>
 */
final class ReaderAcronymMarks {

    private static final Highlighter.HighlightPainter PAINTER =
            new DefaultHighlighter.DefaultHighlightPainter(ResearchUiPalette.ACRONYM_MARK);

    /** One marked word. */
    static final class Mark {
        final int start;
        final int end;
        final ReaderAcronym acronym;
        Object tag;

        Mark(int start, int end, ReaderAcronym acronym) {
            this.start = start;
            this.end = end;
            this.acronym = acronym;
        }
    }

    private final ReaderHtmlView view;
    private final List<Mark> marks;
    private boolean shown;

    private ReaderAcronymMarks(ReaderHtmlView view, List<Mark> marks) {
        this.view = view;
        this.marks = marks;
    }

    /** Find the words to mark in {@code view}; nothing is painted until {@link #show()}. */
    static ReaderAcronymMarks map(ReaderHtmlView view, List<ReaderAcronym> acronyms, AcronymSettings settings) {
        Map<String, ReaderAcronym> bySpelling = new HashMap<String, ReaderAcronym>();
        for (ReaderAcronym acronym : acronyms) {
            for (String spelling : acronym.spellings()) {
                bySpelling.put(spelling, acronym);
            }
        }
        List<Mark> marks = new ArrayList<Mark>();
        HTMLDocument document = (HTMLDocument) view.getDocument();
        String text;
        try {
            text = document.getText(0, document.getLength());
        } catch (BadLocationException impossible) {
            return new ReaderAcronymMarks(view, marks);
        }
        if (!bySpelling.isEmpty()) {
            AcronymSettings rules = settings == null ? AcronymSettings.defaults() : settings;
            Map<Element, Boolean> shouting = new HashMap<Element, Boolean>();
            for (TermScanner.Token token : TermScanner.tokens(text)) {
                List<TermScanner.Token> words = bySpelling.containsKey(token.text())
                        ? Collections.singletonList(token) : TermScanner.parts(token);
                for (TermScanner.Token word : words) {
                    ReaderAcronym acronym = bySpelling.get(word.text());
                    if (acronym != null && isProse(document, word.span().start(), word.span().end(), rules,
                            shouting)) {
                        marks.add(new Mark(word.span().start(), word.span().end(), acronym));
                    }
                }
            }
        }
        return new ReaderAcronymMarks(view, marks);
    }

    /** Whether every character of the word is ordinary text: no link, no code, no line in capitals. */
    private static boolean isProse(HTMLDocument document, int start, int end, AcronymSettings rules,
                                   Map<Element, Boolean> shouting) {
        for (int offset = start; offset < end; offset++) {
            Element character = document.getCharacterElement(offset);
            AttributeSet attributes = character.getAttributes();
            if (attributes.getAttribute(HTML.Tag.A) != null || attributes.getAttribute(HTML.Tag.CODE) != null
                    || attributes.getAttribute(HTML.Tag.KBD) != null || attributes.getAttribute(HTML.Tag.SAMP) != null
                    || attributes.getAttribute(HTML.Tag.TT) != null) {
                return false;
            }
        }
        Element paragraph = document.getParagraphElement(start);
        if (paragraph != document.getParagraphElement(end - 1)) {
            return false;
        }
        for (Element block = paragraph; block != null; block = block.getParentElement()) {
            Object name = block.getAttributes().getAttribute(StyleConstants.NameAttribute);
            Object whiteSpace = block.getAttributes().getAttribute(CSS.Attribute.WHITE_SPACE);
            if (name == HTML.Tag.PRE || whiteSpace != null && "pre".equals(whiteSpace.toString())) {
                return false;
            }
        }
        Boolean loud = shouting.get(paragraph);
        if (loud == null) {
            try {
                String line = document.getText(paragraph.getStartOffset(),
                        paragraph.getEndOffset() - paragraph.getStartOffset());
                loud = AcronymForms.isShouting(line, rules.shoutingPercent(), rules.shoutingMinWords());
            } catch (BadLocationException impossible) {
                loud = Boolean.TRUE;
            }
            shouting.put(paragraph, loud);
        }
        return !loud;
    }

    int count() {
        return marks.size();
    }

    boolean isShown() {
        return shown;
    }

    /** Paint the marks (again). */
    void show() {
        if (shown) {
            return;
        }
        Highlighter highlighter = view.getHighlighter();
        if (highlighter == null) {
            return;
        }
        for (Mark mark : marks) {
            try {
                mark.tag = highlighter.addHighlight(mark.start, mark.end, PAINTER);
            } catch (BadLocationException outdated) {
                mark.tag = null;
            }
        }
        shown = true;
        view.setTextTips(this::tooltipAt);
    }

    /** Remove the marks; the find bar's highlights stay. */
    void hide() {
        Highlighter highlighter = view.getHighlighter();
        for (Mark mark : marks) {
            if (highlighter != null && mark.tag != null) {
                highlighter.removeHighlight(mark.tag);
            }
            mark.tag = null;
        }
        shown = false;
        view.setTextTips(null);
    }

    /** The tooltip of a shown mark at the document offset, or {@code null}. */
    String tooltipAt(int offset) {
        if (!shown) {
            return null;
        }
        for (Mark mark : marks) {
            if (offset >= mark.start && offset < mark.end) {
                return mark.acronym.tooltip();
            }
        }
        return null;
    }

    // For tests.

    List<String> markedWords() {
        List<String> words = new ArrayList<String>();
        for (Mark mark : marks) {
            try {
                words.add(view.getDocument().getText(mark.start, mark.end - mark.start));
            } catch (BadLocationException impossible) {
                words.add("?");
            }
        }
        return words;
    }
}

package com.aresstack.enterpriseai.source.confluence;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ConfluenceHtmlTextTest {

    @Test
    public void headingsParagraphsAndInlineMarkup() {
        assertEquals("## Titel\n\nEin fetter Satz.\n\nZweiter Absatz.",
                ConfluenceHtmlText.toText("<h2>Titel</h2><p>Ein <b>fetter</b>   Satz.</p><p>Zweiter&nbsp;Absatz.</p>"));
    }

    @Test
    public void listsAreBulletedOrNumbered() {
        assertEquals("- eins\n- zwei\n\n1. erstens\n2. zweitens",
                ConfluenceHtmlText.toText("<ul><li>eins</li><li>zwei</li></ul><ol><li>erstens</li><li>zweitens</li></ol>"));
    }

    @Test
    public void tablesBecomePipeSeparatedRows() {
        assertEquals("Name | Wert\nA | 1",
                ConfluenceHtmlText.toText("<table><tbody><tr><th>Name</th><th>Wert</th></tr>"
                        + "<tr><td>A</td><td>1</td></tr></tbody></table>"));
    }

    @Test
    public void codeMacroBecomesFencedBlockWithLanguageAndKeepsIndentationAndBlankLines() {
        String html = "<p>Vorher</p><div class=\"code panel\"><div class=\"codeContent panelContent\">"
                + "<pre class=\"syntaxhighlighter-pre\" data-syntaxhighlighter-params=\"brush: java; gutter: false\">"
                + "class A {\n\n\n    int x;\n}\n</pre></div></div><p>Nachher</p>";
        assertEquals("Vorher\n\n```java\nclass A {\n\n\n    int x;\n}\n```\n\nNachher", ConfluenceHtmlText.toText(html));
    }

    @Test
    public void plainPreHasNoLanguage() {
        assertEquals("```\nls -l\n```", ConfluenceHtmlText.toText("<pre>ls -l</pre>"));
    }

    @Test
    public void scriptsImagesAndMacroChromeAreRemoved() {
        String html = "<p>Text<img src=\"x.png\" alt=\"Bild\"/></p><script>alert(1)</script><style>p{}</style>"
                + "<span class=\"aui-icon\">Icon</span><div style=\"display: none\">versteckt</div>"
                + "<span class=\"confluence-embedded-file-wrapper\"><img src=\"y\"/>Datei</span>";
        assertEquals("Text", ConfluenceHtmlText.toText(html));
    }

    @Test
    public void infoMacroContentIsKept() {
        assertEquals("Hinweis\n\nBitte beachten.",
                ConfluenceHtmlText.toText("<div class=\"confluence-information-macro\"><p class=\"title\">Hinweis</p>"
                        + "<div class=\"confluence-information-macro-body\"><p>Bitte beachten.</p></div></div>"));
    }

    @Test
    public void emptyAndNullInput() {
        assertEquals("", ConfluenceHtmlText.toText(null));
        assertEquals("", ConfluenceHtmlText.toText("<p>  </p>"));
    }
}

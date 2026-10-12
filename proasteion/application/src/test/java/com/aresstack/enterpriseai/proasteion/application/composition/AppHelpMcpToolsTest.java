package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.help.ClasspathHelpDocuments;
import com.aresstack.enterpriseai.proasteion.help.HelpCatalog;
import com.aresstack.enterpriseai.proasteion.help.HelpDocuments;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class AppHelpMcpToolsTest {

    private static final String SPEECH = "---\n"
            + "id: settings.speech\n"
            + "title: Sprachausgabe einstellen\n"
            + "keywords: Stimme, TTS, Vorlesen, voice\n"
            + "---\n\n"
            + "# Sprachausgabe einstellen\n\n"
            + "Im Reiter „Sprachausgabe“ wählst du Modell und Stimme.\n\n"
            + "## Stimme wechseln\n\n"
            + "Öffne Einstellungen → Sprachausgabe und wähle die Stimme.\n\n"
            + "## Name des Assistenten\n\nDer Anzeigename steht ganz oben.\n";
    private static final String READ_ALOUD = "---\n"
            + "id: chat.read-aloud\n"
            + "title: Antworten vorlesen lassen\n"
            + "keywords: Vorlesen, Lautsprecher\n"
            + "---\n\n"
            + "# Antworten vorlesen lassen\n\n"
            + "## Vorlesen starten\n\nKlicke auf den Lautsprecher unter der Antwort.\n";

    private static final HelpCatalog CATALOG = catalog();

    @Test
    public void toolsSearchAndReadWithIdsFromHits() {
        AppHelpMcpTools tools = AppHelpMcpTools.over(CATALOG, new AppHelpToolLimits(5000, 120, 4), "0.50.0");
        List<McpToolContribution> contributions = tools.contributions();
        assertEquals(AppHelpMcpTools.APP_HELP_SEARCH, contributions.get(0).getName());
        assertEquals(AppHelpMcpTools.APP_HELP_READ, contributions.get(1).getName());
        assertTrue(contributions.get(0).getDescription().contains("Prefer this documentation over assumptions"));

        McpToolResult found = call(contributions.get(0), "query", "Stimme ändern");
        assertFalse(found.isError());
        assertTrue(found.getText(), found.getText().contains("id: help:settings.speech#stimme-wechseln"));

        McpToolResult section = call(contributions.get(1), "id", "help:settings.speech#stimme-wechseln");
        assertTrue(section.getText().contains("version: 0.50.0"));
        assertTrue(section.getText().contains("## Stimme wechseln"));
        assertTrue(section.getText().contains("help:settings.speech#name-des-assistenten"));

        McpToolResult whole = call(contributions.get(1), "id", "settings.speech");
        assertTrue(whole.getText().contains("# Sprachausgabe einstellen"));
        assertTrue(whole.getText().contains("## Name des Assistenten {#name-des-assistenten}"));

        assertTrue(call(contributions.get(1), "id", "help:nope").isError());
        assertTrue(call(contributions.get(0), "query", " ").isError());
        McpToolResult nothing = call(contributions.get(0), "query", "Quantencomputer");
        assertTrue(nothing.getText().contains("help:chat.read-aloud: Antworten vorlesen lassen"));
    }

    @Test
    public void responseIsCutToTheLimit() {
        AppHelpMcpTools tools = AppHelpMcpTools.over(CATALOG, new AppHelpToolLimits(200, 80, 4), null);
        String text = call(tools.contributions().get(1), "id", "settings.speech").getText();
        assertTrue(text.length() <= 200);
        assertTrue(text.contains("gekürzt"));
    }

    private static McpToolResult call(McpToolContribution tool, String name, String value) {
        Map<String, Object> arguments = new HashMap<String, Object>();
        arguments.put(name, value);
        return tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
    }

    @Test
    public void bundledHelpLoadsAndContainsTheExampleId() throws IOException {
        HelpCatalog bundled = HelpCatalog.load(ClasspathHelpDocuments.bundled());
        assertNotNull(bundled.topic(AppHelpReadTool.EXAMPLE_ID));
    }

    private static HelpCatalog catalog() {
        final Map<String, String> byName = new LinkedHashMap<String, String>();
        byName.put("settings/speech.md", SPEECH);
        byName.put("chat/read-aloud.md", READ_ALOUD);
        try {
            return HelpCatalog.load(new HelpDocuments() {
                @Override
                public List<String> names() {
                    return new ArrayList<String>(byName.keySet());
                }

                @Override
                public String read(String name) {
                    return byName.get(name);
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

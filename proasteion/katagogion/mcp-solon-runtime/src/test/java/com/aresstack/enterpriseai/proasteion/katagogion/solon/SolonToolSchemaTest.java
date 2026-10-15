package com.aresstack.enterpriseai.proasteion.katagogion.solon;

import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/** Das selbst gebaute JSON Schema trägt Typen, Enum-Werte und Pflichtfelder und escaped Texte korrekt. */
public class SolonToolSchemaTest {

    private static final McpToolHandler NOOP = new McpToolHandler() {
        @Override
        public McpToolResult invoke(McpToolCall call) {
            return McpToolResult.ok("");
        }
    };

    @Test
    public void schemaCarriesTypesEnumValuesAndRequiredFields() {
        McpToolContribution tool = McpToolContribution.of("search", "d", NOOP,
                McpToolParameter.string("query", true, "Suchtext mit \"Zitat\" und \\ Pfad\nZeile"),
                McpToolParameter.integer("limit", false, ""),
                McpToolParameter.bool("exact", false, "genau"),
                McpToolParameter.enumeration("mode", true, "Modus", Arrays.asList("keyword", "semantic")));

        assertEquals("{\"type\":\"object\",\"properties\":{"
                        + "\"query\":{\"type\":\"string\",\"description\":\"Suchtext mit \\\"Zitat\\\" und \\\\ Pfad\\nZeile\"},"
                        + "\"limit\":{\"type\":\"integer\"},"
                        + "\"exact\":{\"type\":\"boolean\",\"description\":\"genau\"},"
                        + "\"mode\":{\"type\":\"string\",\"description\":\"Modus\",\"enum\":[\"keyword\",\"semantic\"]}"
                        + "},\"required\":[\"query\",\"mode\"]}",
                SolonMcpServerRuntime.inputSchema(tool));
    }

    @Test
    public void toolWithoutParametersHasAnEmptyObjectSchema() {
        assertEquals("{\"type\":\"object\",\"properties\":{},\"required\":[]}",
                SolonMcpServerRuntime.inputSchema(McpToolContribution.of("ping", "d", NOOP)));
    }

    @Test
    public void stringArrayIsARealArrayOfStrings() {
        McpToolContribution tool = McpToolContribution.of("read_many", "d", NOOP,
                McpToolParameter.stringArray("ids", true, "Ids"));
        assertEquals("{\"type\":\"object\",\"properties\":{"
                        + "\"ids\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"description\":\"Ids\"}"
                        + "},\"required\":[\"ids\"]}",
                SolonMcpServerRuntime.inputSchema(tool));
    }
}

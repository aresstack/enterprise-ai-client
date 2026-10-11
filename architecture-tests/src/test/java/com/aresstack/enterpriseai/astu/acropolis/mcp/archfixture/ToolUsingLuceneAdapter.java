package com.aresstack.enterpriseai.astu.acropolis.mcp.archfixture;

import com.aresstack.enterpriseai.astu.acropolis.chalcotheca.anagraphai.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: ein MCP-Werkzeug ruft einen konkreten Adapter auf. */
public final class ToolUsingLuceneAdapter {

    public String invoke(String query) {
        return new FakeLuceneAdapter().search(query);
    }
}

package com.aresstack.enterpriseai.application.mcp.archfixture;

import com.aresstack.enterpriseai.knowledge.lucene.archfixture.FakeLuceneAdapter;

/** Absichtlicher Verstoß: ein MCP-Werkzeug ruft einen konkreten Adapter auf. */
public final class ToolUsingLuceneAdapter {

    public String invoke(String query) {
        return new FakeLuceneAdapter().search(query);
    }
}

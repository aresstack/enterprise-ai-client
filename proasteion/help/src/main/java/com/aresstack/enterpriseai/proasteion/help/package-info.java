/**
 * Built-in, version-bound product help: chapters from {@code docs/help} ship as resources in the jar, are parsed into
 * {@link com.aresstack.enterpriseai.proasteion.help.HelpTopic topics} with sections per {@code ##} heading and are
 * searched with an own small BM25 index. Needs no network, no embedding model and no knowledge source.
 *
 * <p>The model reaches the help only through the read-only tools {@code app_help_search} and {@code app_help_read},
 * which the composition root builds on these use cases (MCP types stay out of this module). Product help stays separate from the user's
 * knowledge sources ({@code knowledge_search}).
 */
package com.aresstack.enterpriseai.proasteion.help;

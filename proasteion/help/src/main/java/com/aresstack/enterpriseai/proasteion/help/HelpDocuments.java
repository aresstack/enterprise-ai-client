package com.aresstack.enterpriseai.proasteion.help;

import java.io.IOException;
import java.util.List;

/** Where the help chapters come from: names relative to the help root and their Markdown text. */
public interface HelpDocuments {

    /** Chapter names such as {@code chat/attachments.md}, in a stable order. */
    List<String> names() throws IOException;

    /** Markdown text of one chapter. */
    String read(String name) throws IOException;
}

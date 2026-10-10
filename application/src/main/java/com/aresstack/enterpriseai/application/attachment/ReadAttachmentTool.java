package com.aresstack.enterpriseai.application.attachment;

import com.aresstack.enterpriseai.application.tool.AiTool;
import com.aresstack.enterpriseai.application.tool.ToolArguments;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;

/** {@code read_attachment}: liefert den Text eines Anhangs, bei langen Dateien abschnittsweise ({@code offset}). */
public final class ReadAttachmentTool implements AiTool {

    public static final String NAME = "read_attachment";
    static final int MAX_CHARS = 40000;

    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{"
            + "\"attachment_id\":{\"type\":\"string\",\"description\":\"Kennung des Anhangs, z. B. att-1a2b3c4d\"},"
            + "\"offset\":{\"type\":\"integer\",\"description\":\"Startzeichen für lange Dateien, Standard 0\"}},"
            + "\"required\":[\"attachment_id\"]}";

    private final AttachmentTexts texts;

    ReadAttachmentTool(AttachmentTexts texts) {
        this.texts = texts;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.function(NAME, "Liest den Textinhalt einer vom Nutzer angehängten Datei "
                + "(PDF, Word, Excel, PowerPoint, HTML, E-Mail, Text). Lange Dateien kommen abschnittsweise; "
                + "der nächste Abschnitt über offset.", SCHEMA);
    }

    @Override
    public String execute(String arguments) {
        ToolArguments args = ToolArguments.parse(arguments);
        Attachment attachment = texts.find(args.required("attachment_id"));
        String text = texts.text(attachment);
        int offset = Math.max(0, Math.min(args.integer("offset", 0), text.length()));
        int end = Math.min(text.length(), offset + MAX_CHARS);
        StringBuilder out = new StringBuilder();
        out.append("Anhang ").append(attachment.id()).append(" (").append(attachment.fileName()).append("), Zeichen ")
                .append(offset).append('–').append(end).append(" von ").append(text.length()).append(":\n\n");
        out.append(text, offset, end);
        if (end < text.length()) {
            out.append("\n\n[gekürzt; weiter mit offset=").append(end).append(']');
        }
        if (text.trim().isEmpty()) {
            out.append("(kein Text extrahierbar)");
        }
        return out.toString();
    }
}

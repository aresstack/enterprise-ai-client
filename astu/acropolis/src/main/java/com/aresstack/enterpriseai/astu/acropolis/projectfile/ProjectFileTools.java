package com.aresstack.enterpriseai.astu.acropolis.projectfile;

import com.aresstack.enterpriseai.astu.acropolis.attachment.Attachment;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentException;
import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.EffectiveProjectContext;
import com.aresstack.enterpriseai.astu.acropolis.chat.project.ProjectContextResolver;
import com.aresstack.enterpriseai.astu.acropolis.tool.AiTool;
import com.aresstack.enterpriseai.astu.acropolis.tool.ToolArguments;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.chat.api.ToolDefinition;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The project file tools of one chat: {@code list_project_files}, {@code read_project_file} and
 * {@code search_project_file}. The chat id comes from the turn, never from the model; every call resolves the chat's
 * current project and refuses when the chat has none or its assignment cannot be resolved (fail closed), so moving a
 * chat withdraws the old project's files at once.
 */
public final class ProjectFileTools {

    public static final String LIST = "list_project_files";
    public static final String READ = "read_project_file";
    public static final String SEARCH = "search_project_file";
    static final int MAX_CHARS = 40000;
    static final int MAX_HITS = 20;
    static final int MAX_HIT_CHARS = 600;

    private static final ToolDefinition LIST_DEFINITION = ToolDefinition.function(LIST,
            "Listet die gemeinsamen Dateien des Projekts, zu dem dieser Chat gehört (Kennung, Name, Größe).",
            "{\"type\":\"object\",\"properties\":{}}");
    private static final ToolDefinition READ_DEFINITION = ToolDefinition.function(READ,
            "Liest den Textinhalt einer gemeinsamen Projektdatei. Lange Dateien kommen abschnittsweise; "
            + "der nächste Abschnitt über offset.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"file_id\":{\"type\":\"string\",\"description\":\"Kennung der Projektdatei, z. B. att-1a2b3c4d\"},"
            + "\"offset\":{\"type\":\"integer\",\"description\":\"Startzeichen für lange Dateien, Standard 0\"}},"
            + "\"required\":[\"file_id\"]}");
    private static final ToolDefinition SEARCH_DEFINITION = ToolDefinition.function(SEARCH,
            "Sucht einen Begriff in den gemeinsamen Projektdateien und liefert die passenden Absätze.",
            "{\"type\":\"object\",\"properties\":{"
            + "\"query\":{\"type\":\"string\",\"description\":\"Suchbegriff oder Wortfolge\"},"
            + "\"file_id\":{\"type\":\"string\",\"description\":\"Kennung der Projektdatei; ohne Angabe alle\"}},"
            + "\"required\":[\"query\"]}");

    private final ProjectFileStore store;
    private final AttachmentTextExtractor extractor;
    private final ProjectContextResolver projects;
    private final ChatConversationId conversation;
    private final Map<String, String> texts = new HashMap<String, String>();

    public ProjectFileTools(ProjectFileStore store, AttachmentTextExtractor extractor, ProjectContextResolver projects,
                            ChatConversationId conversation) {
        if (store == null || extractor == null || projects == null || conversation == null) {
            throw new IllegalArgumentException("store, extractor, projects and conversation must not be null");
        }
        this.store = store;
        this.extractor = extractor;
        this.projects = projects;
        this.conversation = conversation;
    }

    /** Name, description and schema of the three tools without a chat, e.g. for the tool overview. */
    public static List<ToolDefinition> definitions() {
        return Arrays.asList(LIST_DEFINITION, READ_DEFINITION, SEARCH_DEFINITION);
    }

    /** The three tools bound to this chat. */
    public List<AiTool> tools() {
        return Arrays.<AiTool>asList(new Tool(LIST_DEFINITION) {
            @Override
            String run(String projectId, ToolArguments args) {
                return list(projectId);
            }
        }, new Tool(READ_DEFINITION) {
            @Override
            String run(String projectId, ToolArguments args) {
                return read(projectId, args.required("file_id"), args.integer("offset", 0));
            }
        }, new Tool(SEARCH_DEFINITION) {
            @Override
            String run(String projectId, ToolArguments args) {
                return search(projectId, args.required("query").trim(), args.text("file_id"));
            }
        });
    }

    /** The chat's current project id; throws when the chat has none or the assignment is unresolved. */
    String currentProject() {
        EffectiveProjectContext context;
        try {
            context = projects.resolve(conversation);
        } catch (RuntimeException e) {
            context = EffectiveProjectContext.unresolved();
        }
        if (context == null || !context.isResolved()) {
            throw new AttachmentException("Projektzuordnung dieses Chats nicht auflösbar; Zugriff verweigert.");
        }
        if (context.projectId() == null) {
            throw new AttachmentException("Dieser Chat gehört zu keinem Projekt; es gibt keine Projektdateien.");
        }
        return context.projectId();
    }

    private String list(String projectId) {
        List<Attachment> files = store.list(projectId);
        if (files.isEmpty()) {
            return "Das Projekt hat keine Dateien.";
        }
        StringBuilder out = new StringBuilder("Projektdateien:");
        for (Attachment file : files) {
            out.append("\n- ").append(file.id()).append(": ").append(file.fileName()).append(" (")
                    .append(file.sizeBytes()).append(" B)");
        }
        return out.toString();
    }

    private String read(String projectId, String fileId, int requestedOffset) {
        Attachment file = find(projectId, fileId);
        String text = text(projectId, file);
        int offset = Math.max(0, Math.min(requestedOffset, text.length()));
        int end = Math.min(text.length(), offset + MAX_CHARS);
        StringBuilder out = new StringBuilder();
        out.append("Projektdatei ").append(file.id()).append(" (").append(file.fileName()).append("), Zeichen ")
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

    private String search(String projectId, String query, String fileId) {
        List<Attachment> files = fileId == null ? store.list(projectId)
                : Collections.singletonList(find(projectId, fileId));
        String needle = query.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        int hits = 0;
        for (Attachment file : files) {
            String[] paragraphs = text(projectId, file).split("\\n\\s*\\n|\\r?\\n");
            for (int i = 0; i < paragraphs.length && hits < MAX_HITS; i++) {
                String paragraph = paragraphs[i].trim();
                int at = paragraph.toLowerCase(Locale.ROOT).indexOf(needle);
                if (at < 0) {
                    continue;
                }
                hits++;
                out.append(file.id()).append(" (").append(file.fileName()).append("), Absatz ").append(i + 1)
                        .append(": ").append(excerpt(paragraph, at)).append("\n\n");
            }
        }
        if (hits == 0) {
            return "Keine Treffer für \"" + query + "\".";
        }
        return hits + (hits >= MAX_HITS ? "+" : "") + " Treffer für \"" + query + "\":\n\n" + out.toString().trim();
    }

    private Attachment find(String projectId, String fileId) {
        for (Attachment file : store.list(projectId)) {
            if (file.id().equals(fileId) || file.fileName().equals(fileId)) {
                return file;
            }
        }
        throw new AttachmentException("Unbekannte Projektdatei " + fileId);
    }

    private synchronized String text(String projectId, Attachment file) {
        String key = projectId + '/' + file.id();
        String text = texts.get(key);
        if (text == null) {
            text = extractor.extractText(file.fileName(), store.read(projectId, file.id()));
            text = text == null ? "" : text;
            texts.put(key, text);
        }
        return text;
    }

    private static String excerpt(String paragraph, int at) {
        if (paragraph.length() <= MAX_HIT_CHARS) {
            return paragraph;
        }
        int start = Math.max(0, at - MAX_HIT_CHARS / 3);
        int end = Math.min(paragraph.length(), start + MAX_HIT_CHARS);
        return (start > 0 ? "…" : "") + paragraph.substring(start, end) + (end < paragraph.length() ? "…" : "");
    }

    private abstract class Tool implements AiTool {
        private final ToolDefinition definition;

        Tool(ToolDefinition definition) {
            this.definition = definition;
        }

        @Override
        public String name() {
            return definition.name();
        }

        @Override
        public ToolDefinition definition() {
            return definition;
        }

        @Override
        public String execute(String arguments) {
            ToolArguments args = ToolArguments.parse(arguments);
            return run(currentProject(), args);
        }

        abstract String run(String projectId, ToolArguments args);
    }
}

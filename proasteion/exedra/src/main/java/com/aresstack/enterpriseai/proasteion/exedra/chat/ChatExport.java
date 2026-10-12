package com.aresstack.enterpriseai.proasteion.exedra.chat;

import com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatMessageRecord;
import com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Exportiert einen gespeicherten Chat als Datei („Chat exportieren …“ im Chat-Menü), zum Nachvollziehen von
 * Fehlern. Quelle ist allein der persistierte Zustand, nie die Oberfläche: der {@link ChatRecord} aus dem
 * {@link ChatHistoryStore} (jedes gespeicherte Feld unverändert, ergänzt um Kennung, Zeitstempel, Zustand und Phase je
 * Nachricht) und, bei Agent-Chats, die Dateien des Research-Projekts des Chats (Phase, Phasen-Journal, Quellen,
 * vorgemerkte Treffer, Lauf, Konzept, Gliederung, Dokument). Was nie gespeichert wird (laufende und
 * fehlgeschlagene Antworten, Werkzeug- und Gedankenwolken), sagt der Export unter {@code notPersisted}, statt es zu
 * erfinden.
 *
 * <p>Der Export liest nur: Chat-Datei, Anhänge und Projekt bleiben unverändert, auch für den offenen Chat. Vor dem
 * Schreiben schwärzt {@link ChatExportRedaction} Zugangsdaten. JSON ist maßgeblich; Markdown ist dieselbe Sicht,
 * menschenlesbar.
 */
public final class ChatExport {

    public static final String FORMAT = "enterprise-ai-client-chat-export";
    public static final int VERSION = 1;

    /** Größte Projektdatei, die mit ihrem Inhalt exportiert wird; größere erscheinen nur mit ihrer Größe. */
    static final long MAX_PROJECT_FILE_BYTES = 4L * 1024 * 1024;

    private static final String STATE_FILE = "research-state";
    private static final String JOURNAL_FILE = "phase-journal.json";
    private static final String SOURCES_FILE = "sources.tsv";
    private static final String PARKED_FILE = "parked.tsv";
    private static final String RUN_FILE = "run-status";

    private final ChatHistoryStore store;
    private final Function<String, File> projectDirectories;
    private final LongSupplier clock;
    private final String applicationVersion;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    /**
     * @param store              die gespeicherten Chats
     * @param projectDirectories der Research-Projektordner eines Chats oder {@code null} (ohne Agent: immer null)
     * @param clock              Zeitpunkt des Exports
     * @param applicationVersion Version der Anwendung, oder {@code null}
     */
    public ChatExport(ChatHistoryStore store, Function<String, File> projectDirectories, LongSupplier clock,
                      String applicationVersion) {
        if (store == null || clock == null) {
            throw new IllegalArgumentException("store and clock must not be null");
        }
        this.store = store;
        this.projectDirectories = projectDirectories;
        this.clock = clock;
        this.applicationVersion = applicationVersion;
    }

    /** Der Export eines gespeicherten Chats als (geschwärzter) JSON-Baum, oder {@code null}, wenn es ihn nicht gibt. */
    public JsonObject export(String chatId) {
        ChatRecord record = chatId == null ? null : store.load(chatId);
        if (record == null) {
            return null;
        }
        File project = projectDirectories == null ? null : projectDirectories.apply(chatId);
        JsonObject agent = project != null && project.isDirectory() ? agent(project) : null;
        JsonObject journal = project != null ? messagePhases(parseJson(readText(new File(project, JOURNAL_FILE))))
                : null;

        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT);
        root.addProperty("version", VERSION);
        root.addProperty("exportedAt", iso(clock.getAsLong()));
        root.addProperty("application", applicationVersion);
        root.add("chat", chat(record, journal));
        root.add("agent", agent != null ? agent : JsonNull.INSTANCE);
        JsonArray notPersisted = new JsonArray();
        notPersisted.add("Laufende und fehlgeschlagene Antworten werden nicht gespeichert (failureMessage ist daher "
                + "immer null).");
        notPersisted.add("Werkzeug- und Gedankenwolken (Activities) gehören zum Lauf und werden nicht gespeichert.");
        notPersisted.add("Quellen (sources) und Werkzeugaufrufe (toolCalls) einer Antwort im normalen Chat werden "
                + "seit 0.30.0 gespeichert; bei älteren Antworten, Nutzernachrichten und Hinweisen sind sie null. "
                + "Ergebnisse von Werkzeugaufrufen sind auf ihren Anfang gekürzt (resultLength = volle Länge). "
                + "Die Quellen eines Agent-Chats stehen unter agent.sources.");
        root.add("notPersisted", notPersisted);

        ChatExportRedaction redaction = new ChatExportRedaction();
        redaction.apply(root);
        root.addProperty("redactions", redaction.count());
        return root;
    }

    /** Der Export als UTF-8-JSON. */
    public String toJson(JsonObject export) {
        return gson.toJson(export) + "\n";
    }

    /** Derselbe Export als Markdown (menschenlesbar; maßgeblich bleibt JSON). */
    public String toMarkdown(JsonObject export) {
        JsonObject chat = export.getAsJsonObject("chat");
        StringBuilder out = new StringBuilder();
        out.append("# ").append(string(chat, "title")).append("\n\n");
        out.append("- Chat-ID: `").append(string(chat, "id")).append("`\n");
        out.append("- Erstellt: ").append(string(chat, "createdAtIso")).append("\n");
        out.append("- Geändert: ").append(string(chat, "modifiedAtIso")).append("\n");
        out.append("- Exportiert: ").append(string(export, "exportedAt")).append("\n");
        JsonElement agentElement = export.get("agent");
        JsonObject agent = agentElement != null && agentElement.isJsonObject() ? agentElement.getAsJsonObject() : null;
        if (agent != null) {
            out.append("- Phase: ").append(string(agent, "phase")).append(" / ").append(string(agent, "state"))
                    .append("\n");
        }
        out.append("\n");
        for (JsonElement element : chat.getAsJsonArray("messages")) {
            JsonObject message = element.getAsJsonObject();
            out.append("## ").append(message.get("index").getAsInt()).append(". ").append(roleTitle(message))
                    .append(" · ").append(string(message, "timestamp"));
            if (!string(message, "phase").isEmpty()) {
                out.append(" · Phase ").append(string(message, "phase"));
            }
            if ("CANCELLED".equals(string(message, "state"))) {
                out.append(" · abgebrochen");
            }
            out.append("\n\n").append(string(message, "text")).append("\n\n");
            for (JsonElement attachment : message.getAsJsonArray("attachments")) {
                out.append("- Anhang: ").append(string(attachment.getAsJsonObject(), "fileName")).append("\n");
            }
            for (JsonElement page : message.getAsJsonArray("bookmarks")) {
                JsonObject bookmark = page.getAsJsonObject();
                out.append("- Seite: ").append(string(bookmark, "title")).append(" <")
                        .append(string(bookmark, "uri")).append(">\n");
            }
            JsonElement quotes = message.get("quotes");
            if (quotes != null && quotes.isJsonArray()) {
                for (JsonElement quote : quotes.getAsJsonArray()) {
                    out.append("- Zitat: „").append(string(quote.getAsJsonObject(), "text").trim()
                            .replaceAll("\\s+", " ")).append("“\n");
                }
            }
            JsonElement calls = message.get("toolCalls");
            if (calls != null && calls.isJsonArray()) {
                for (JsonElement callElement : calls.getAsJsonArray()) {
                    JsonObject call = callElement.getAsJsonObject();
                    out.append("- Werkzeug: `").append(string(call, "name")).append("` ")
                            .append(string(call, "arguments")).append(call.get("succeeded").getAsBoolean()
                                    ? "" : " (fehlgeschlagen)").append("\n");
                }
            }
            JsonElement answerSources = message.get("sources");
            if (answerSources != null && answerSources.isJsonArray()) {
                for (JsonElement sourceElement : answerSources.getAsJsonArray()) {
                    JsonObject source = sourceElement.getAsJsonObject();
                    out.append("- Quelle [").append(string(source, "number")).append("]: ")
                            .append(string(source, "title")).append(" <").append(string(source, "location"))
                            .append(">\n");
                }
            }
            out.append("\n");
        }
        if (agent != null && agent.getAsJsonArray("sources").size() > 0) {
            out.append("## Quellen des Research-Projekts\n\n");
            for (JsonElement element : agent.getAsJsonArray("sources")) {
                JsonObject source = element.getAsJsonObject();
                out.append("- ").append(string(source, "title")).append(" <").append(string(source, "url"))
                        .append(">\n");
            }
            out.append("\n");
        }
        return out.toString();
    }

    /**
     * Exportiert den Chat in die Datei (über eine Zwischendatei, nie halb geschrieben).
     *
     * @return {@code false}, wenn es den Chat nicht (mehr) gibt
     */
    public boolean write(String chatId, Path target, boolean markdown) throws IOException {
        JsonObject export = export(chatId);
        if (export == null) {
            return false;
        }
        byte[] bytes = (markdown ? toMarkdown(export) : toJson(export)).getBytes(StandardCharsets.UTF_8);
        Path directory = target.toAbsolutePath().getParent();
        if (directory != null) {
            Files.createDirectories(directory);
        }
        Path temp = target.resolveSibling(target.getFileName().toString() + ".tmp");
        try {
            Files.write(temp, bytes);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
        return true;
    }

    // ------------------------------------------------------------------ chat

    private JsonObject chat(ChatRecord record, JsonObject journal) {
        JsonObject chat = gson.toJsonTree(record).getAsJsonObject(); // jedes gespeicherte Feld, wie es liegt
        chat.remove("messages");
        chat.addProperty("createdAtIso", iso(record.getCreatedAt()));
        chat.addProperty("modifiedAtIso", iso(record.getModifiedAt()));
        chat.addProperty("messageCount", record.getMessages().size());
        chat.addProperty("attachmentCount", record.attachmentCount());
        JsonArray messages = new JsonArray();
        int index = 1;
        for (ChatMessageRecord message : record.getMessages()) {
            messages.add(message(message, index++, journal));
        }
        chat.add("messages", messages);
        return chat;
    }

    private JsonObject message(ChatMessageRecord record, int index, JsonObject journal) {
        JsonObject stored = gson.toJsonTree(record).getAsJsonObject();
        JsonObject message = new JsonObject();
        String id = record.getRole() + "@" + record.getCreatedAt(); // wie SavedChatHistory und Phasen-Journal
        message.addProperty("index", index);
        message.addProperty("id", id);
        message.addProperty("timestamp", iso(record.getCreatedAt()));
        message.addProperty("author", record.isUser() ? "USER" : record.isAssistant() ? "ASSISTANT"
                : record.isInfo() ? "NOTICE" : String.valueOf(record.getRole()).toUpperCase(Locale.ROOT));
        message.addProperty("state", record.isCancelled() ? "CANCELLED" : "COMPLETE");
        message.addProperty("textFormat", "markdown");
        JsonElement phase = journal == null ? null : journal.get(id);
        message.addProperty("phase", phase != null && phase.isJsonPrimitive() ? phase.getAsString() : null);
        for (Map.Entry<String, JsonElement> field : stored.entrySet()) {
            message.add(field.getKey(), field.getValue()); // role, text, createdAt, cancelled, attachments, bookmarks
        }
        if (!message.has("attachments")) {
            message.add("attachments", new JsonArray());
        }
        if (!message.has("bookmarks")) {
            message.add("bookmarks", new JsonArray());
        }
        // Quellen und Werkzeugaufrufe speichert der Chat seit 0.30.0; ältere Chats haben sie nicht (null).
        if (!message.has("sources")) {
            message.add("sources", JsonNull.INSTANCE);
        }
        if (!message.has("toolCalls")) {
            message.add("toolCalls", JsonNull.INSTANCE);
        }
        message.add("failureMessage", JsonNull.INSTANCE);
        return message;
    }

    // ------------------------------------------------------------------ agent (Research-Projekt des Chats)

    private JsonObject agent(File project) {
        JsonObject agent = new JsonObject();
        agent.addProperty("projectId", project.getName());
        List<String> state = lines(new File(project, STATE_FILE));
        agent.addProperty("phase", field(state, 0));
        agent.addProperty("state", field(state, 1));
        agent.addProperty("continuationState", field(state, 2));
        agent.addProperty("revision", field(state, 3));
        agent.addProperty("pendingApproval", field(state, 4));

        JsonElement journal = parseJson(readText(new File(project, JOURNAL_FILE)));
        agent.add("phaseJournal", journal != null ? journal : JsonNull.INSTANCE);

        JsonArray sources = new JsonArray();
        for (String line : lines(new File(project, SOURCES_FILE))) {
            String[] parts = line.split("\t", -1);
            JsonObject source = new JsonObject();
            source.addProperty("id", part(parts, 0));
            source.addProperty("title", part(parts, 1));
            source.addProperty("url", part(parts, 2));
            source.addProperty("bookmark", part(parts, 3));
            source.addProperty("revision", part(parts, 4));
            source.addProperty("contentHash", part(parts, 5));
            sources.add(source);
        }
        agent.add("sources", sources);

        JsonArray parked = new JsonArray();
        for (String line : lines(new File(project, PARKED_FILE))) {
            String[] parts = line.split("\t", -1);
            JsonObject hit = new JsonObject();
            hit.addProperty("url", part(parts, 0));
            hit.addProperty("title", part(parts, 1));
            hit.addProperty("score", part(parts, 2));
            hit.addProperty("excerpt", part(parts, 3));
            parked.add(hit);
        }
        agent.add("parked", parked);

        List<String> run = lines(new File(project, RUN_FILE));
        JsonObject runStatus = new JsonObject();
        runStatus.addProperty("activity", field(run, 0));
        runStatus.addProperty("query", field(run, 1));
        runStatus.addProperty("pageTitle", field(run, 2));
        runStatus.addProperty("url", field(run, 3));
        runStatus.addProperty("pagesVisited", field(run, 4));
        runStatus.addProperty("acceptedSources", field(run, 5));
        runStatus.addProperty("stopReason", field(run, 6));
        runStatus.addProperty("lastAccepted", field(run, 7));
        agent.add("runStatus", run.isEmpty() ? JsonNull.INSTANCE : runStatus);

        // Alle Dateien des Projekts, wie sie liegen (JSON als Baum, Text als Text, Binäres nur mit Größe).
        JsonObject files = new JsonObject();
        for (File file : files(project)) {
            String path = project.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/');
            files.add(path, fileContent(file));
        }
        agent.add("files", files);
        return agent;
    }

    private JsonElement fileContent(File file) {
        long size = file.length();
        if (size > MAX_PROJECT_FILE_BYTES) {
            JsonObject skipped = new JsonObject();
            skipped.addProperty("omitted", "zu groß");
            skipped.addProperty("size", size);
            return skipped;
        }
        String text = readUtf8(file);
        if (text == null) {
            JsonObject binary = new JsonObject();
            binary.addProperty("binary", true);
            binary.addProperty("size", size);
            return binary;
        }
        if (file.getName().endsWith(".json")) {
            JsonElement parsed = parseJson(text);
            if (parsed != null) {
                return parsed;
            }
        }
        return new com.google.gson.JsonPrimitive(text);
    }

    private static List<File> files(File directory) {
        List<File> result = new ArrayList<File>();
        File[] children = directory.listFiles();
        if (children == null) {
            return result;
        }
        List<File> sorted = new ArrayList<File>();
        Collections.addAll(sorted, children);
        Collections.sort(sorted);
        for (File child : sorted) {
            if (child.isDirectory()) {
                result.addAll(files(child));
            } else if (child.isFile()) {
                result.add(child);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ helpers

    /** Die Phase je Nachricht aus dem Phasen-Journal ({@code messagePhases}), Kennung = Rolle@Zeitpunkt. */
    private static JsonObject messagePhases(JsonElement journal) {
        if (journal == null || !journal.isJsonObject()) {
            return null;
        }
        JsonElement phases = journal.getAsJsonObject().get("messagePhases");
        return phases != null && phases.isJsonObject() ? phases.getAsJsonObject() : null;
    }

    private static String roleTitle(JsonObject message) {
        String author = string(message, "author");
        return "USER".equals(author) ? "Nutzer" : "ASSISTANT".equals(author) ? "Assistent"
                : "NOTICE".equals(author) ? "Hinweis" : author;
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static String iso(long millis) {
        return millis <= 0L ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    private static String field(List<String> lines, int index) {
        if (index >= lines.size()) {
            return null;
        }
        String value = lines.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private static String part(String[] parts, int index) {
        return index < parts.length ? parts[index] : "";
    }

    private static JsonElement parseJson(String text) {
        if (text == null || text.trim().isEmpty()) {
            return null;
        }
        try {
            return JsonParser.parseString(text);
        } catch (RuntimeException notJson) {
            return null;
        }
    }

    private static List<String> lines(File file) {
        String text = readText(file);
        List<String> lines = new ArrayList<String>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\r?\n", -1)) {
            if (!line.trim().isEmpty() || !lines.isEmpty()) {
                lines.add(line);
            }
        }
        while (!lines.isEmpty() && lines.get(lines.size() - 1).trim().isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    private static String readText(File file) {
        return file.isFile() ? readUtf8(file) : null;
    }

    /** Der Inhalt als UTF-8-Text, oder {@code null}, wenn die Datei kein gültiger UTF-8-Text ist. */
    private static String readUtf8(File file) {
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            for (byte b : bytes) {
                if (b == 0) {
                    return null;
                }
            }
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException binary) {
            return null;
        } catch (IOException unreadable) {
            return null;
        }
    }
}

package com.aresstack.enterpriseai.astu.acropolis.navigation;

import com.aresstack.enterpriseai.astu.chat.ChatMessage;
import com.aresstack.enterpriseai.astu.chat.ChatOptions;
import com.aresstack.enterpriseai.astu.chat.ChatRequest;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Kurze Navigationsbezeichnungen (z. B. „Belegannahme und Verarbeitung“) über den vorhandenen Chat-Port mit dem
 * gewählten Chat-Modell, eine Anfrage je Dokumentfassung für alle Themenabschnitte. Scheitert die Anfrage oder ist kein
 * Chat-Modell gewählt, bleiben die Bezeichnungen aus der Analyse.
 */
public final class ChatSectionLabeler implements SectionLabeler {

    static final int MAX_SECTIONS = 40;
    static final int MAX_CHARS_PER_SECTION = 700;
    static final int MAX_LABEL_CHARS = 60;
    static final String INSTRUCTION = "Du benennst Abschnitte eines Dokuments für eine Navigationsleiste. "
            + "Gib für jeden nummerierten Abschnitt eine kurze, sachliche Bezeichnung aus zwei bis fünf Wörtern in der "
            + "Sprache des Abschnitts, ohne Satzzeichen am Ende. Antworte nur mit Zeilen der Form "
            + "„Nummer: Bezeichnung“, eine Zeile je Abschnitt, nichts sonst.";
    private static final Pattern LINE = Pattern.compile("^\\s*\\[?(\\d{1,3})\\]?\\s*[:.)\\-]\\s*(.+?)\\s*$");

    private final ChatCompletionPort chat;
    private final Supplier<String> model;

    /**
     * @param model Kennung des gewählten Chat-Modells ({@code [katalog:]id}), {@code null}: keine Benennung
     */
    public ChatSectionLabeler(ChatCompletionPort chat, Supplier<String> model) {
        if (chat == null || model == null) {
            throw new IllegalArgumentException("chat and model must not be null");
        }
        this.chat = chat;
        this.model = model;
    }

    @Override
    public List<String> labels(String documentTitle, List<String> sectionTexts) {
        String selected = model.get();
        if (selected == null || selected.trim().isEmpty() || sectionTexts == null || sectionTexts.isEmpty()
                || sectionTexts.size() > MAX_SECTIONS) {
            return null;
        }
        StringBuilder prompt = new StringBuilder();
        if (documentTitle != null && !documentTitle.trim().isEmpty()) {
            prompt.append("Dokument: ").append(documentTitle.trim()).append("\n\n");
        }
        for (int i = 0; i < sectionTexts.size(); i++) {
            String text = sectionTexts.get(i).replace('\n', ' ').trim();
            if (text.length() > MAX_CHARS_PER_SECTION) {
                text = text.substring(0, MAX_CHARS_PER_SECTION) + " …";
            }
            prompt.append(i + 1).append(": ").append(text).append("\n");
        }
        ChatRequest request = new ChatRequest(Arrays.asList(ChatMessage.system(INSTRUCTION),
                ChatMessage.user(prompt.toString())), ChatOptions.builder().model(selected).build());
        return parse(chat.complete(request).content(), sectionTexts.size());
    }

    /** Zeilen „n: Bezeichnung“ → Liste in Abschnittsreihenfolge; fehlende Nummern bleiben {@code null}. */
    static List<String> parse(String answer, int count) {
        List<String> labels = new ArrayList<String>(count);
        for (int i = 0; i < count; i++) {
            labels.add(null);
        }
        if (answer == null) {
            return labels;
        }
        for (String line : answer.split("\\r?\\n")) {
            Matcher matcher = LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            int index = Integer.parseInt(matcher.group(1)) - 1;
            if (index < 0 || index >= count) {
                continue;
            }
            String label = matcher.group(2).replaceAll("^[\"'„“*]+|[\"'“”*.:;]+$", "").trim();
            if (label.length() > MAX_LABEL_CHARS) {
                label = label.substring(0, MAX_LABEL_CHARS).trim();
            }
            if (!label.isEmpty()) {
                labels.set(index, label);
            }
        }
        return labels;
    }
}

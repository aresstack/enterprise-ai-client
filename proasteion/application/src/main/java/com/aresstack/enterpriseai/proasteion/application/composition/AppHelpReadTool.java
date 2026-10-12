package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.help.HelpReadUseCase;
import com.aresstack.enterpriseai.proasteion.help.HelpReading;
import com.aresstack.enterpriseai.proasteion.help.HelpSection;
import com.aresstack.enterpriseai.proasteion.help.HelpText;
import com.aresstack.enterpriseai.proasteion.help.HelpTopic;
import com.aresstack.enterpriseai.proasteion.help.UnknownHelpTopicException;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

/** {@code app_help_read(id)}: a whole chapter, or one section when the id carries an anchor. */
final class AppHelpReadTool implements McpToolHandler {

    static final String NAME = "app_help_read";
    static final String PARAM_ID = "id";
    static final String EXAMPLE_ID = "help:settings.speech";
    static final String EXAMPLE = "id=\"" + EXAMPLE_ID + "\"";

    private final HelpReadUseCase read;
    private final AppHelpToolLimits limits;
    private final String version;

    AppHelpReadTool(HelpReadUseCase read, AppHelpToolLimits limits, String version) {
        this.read = read;
        this.limits = limits;
        this.version = version;
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Read a documentation topic of the Enterprise AI Client returned by " + AppHelpSearchTool.NAME
                        + ". Use the exact id from a search hit (line \"id:\"). With #anchor you get that section, "
                        + "without it the whole chapter. Example: " + EXAMPLE + ".",
                this,
                McpToolParameter.string(PARAM_ID, true, "Topic id from " + AppHelpSearchTool.NAME
                        + ". Example: " + EXAMPLE));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        String id = call.getString(PARAM_ID);
        if (id == null || id.trim().isEmpty()) {
            return McpToolResult.error("Missing argument: id — example: " + EXAMPLE);
        }
        HelpReading reading;
        try {
            reading = read.read(id);
        } catch (UnknownHelpTopicException unknown) {
            return McpToolResult.error(describe(id, unknown.topic()));
        }
        return McpToolResult.ok(HelpText.truncate(render(reading), limits.maxResponseChars()));
    }

    private static String describe(String id, HelpTopic topic) {
        if (topic == null) {
            return "Unknown help id '" + id.trim() + "'. Use an id from " + AppHelpSearchTool.NAME + " — example: "
                    + EXAMPLE;
        }
        StringBuilder message = new StringBuilder("Chapter ").append(topic.qualifiedId())
                .append(" has no such section. Sections:");
        for (HelpSection section : topic.sections()) {
            if (!section.isIntroduction()) {
                message.append("\n- ").append(topic.qualifiedId()).append('#').append(section.anchor());
            }
        }
        return message.toString();
    }

    private String render(HelpReading reading) {
        HelpTopic topic = reading.topic();
        StringBuilder out = new StringBuilder();
        out.append("title: ").append(topic.title()).append('\n');
        out.append("id: ").append(topic.qualifiedId()).append('\n');
        if (version != null && !version.trim().isEmpty()) {
            out.append("version: ").append(version.trim()).append(" (installed application)\n");
        }
        if (!topic.since().isEmpty()) {
            out.append("since: ").append(topic.since()).append('\n');
        }
        if (reading.isSection()) {
            HelpSection section = reading.section();
            out.append("section: ").append(section.heading()).append('\n');
            out.append('\n').append("## ").append(section.heading()).append("\n\n").append(section.markdown())
                    .append('\n');
            appendOtherSections(out, topic, section);
            return out.toString();
        }
        out.append('\n').append("# ").append(topic.title()).append('\n');
        for (HelpSection section : topic.sections()) {
            out.append('\n');
            if (!section.isIntroduction()) {
                out.append("## ").append(section.heading()).append(" {#").append(section.anchor()).append("}\n\n");
            }
            out.append(section.markdown()).append('\n');
        }
        return out.toString();
    }

    private static void appendOtherSections(StringBuilder out, HelpTopic topic, HelpSection shown) {
        StringBuilder others = new StringBuilder();
        for (HelpSection section : topic.sections()) {
            if (section != shown && !section.isIntroduction()) {
                others.append("- ").append(section.heading()).append(": ").append(topic.qualifiedId()).append('#')
                        .append(section.anchor()).append('\n');
            }
        }
        if (others.length() > 0) {
            out.append("\nOther sections of this chapter:\n").append(others);
        }
    }
}

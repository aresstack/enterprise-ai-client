package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.proasteion.help.HelpCatalog;
import com.aresstack.enterpriseai.proasteion.help.HelpReadUseCase;
import com.aresstack.enterpriseai.proasteion.help.HelpSearchUseCase;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The product help as two read-only tools for chat and agent, next to the knowledge tools but separate from them:
 * <ul>
 *   <li>{@value #APP_HELP_SEARCH}: search the bundled help ({@code query}, optional {@code limit}),</li>
 *   <li>{@value #APP_HELP_READ}: read a chapter or one section ({@code id}).</li>
 * </ul>
 * The handlers call only the help use cases; the help never changes settings and never calls a model.
 */
public final class AppHelpMcpTools {

    public static final String APP_HELP_SEARCH = AppHelpSearchTool.NAME;
    public static final String APP_HELP_READ = AppHelpReadTool.NAME;

    private final AppHelpSearchTool search;
    private final AppHelpReadTool read;

    /**
     * @param version installed application version for {@code app_help_read}, or {@code null} when unknown
     */
    public AppHelpMcpTools(HelpSearchUseCase search, HelpReadUseCase read, AppHelpToolLimits limits,
                           String version) {
        if (search == null || read == null || limits == null) {
            throw new IllegalArgumentException("search, read and limits are required");
        }
        this.search = new AppHelpSearchTool(search, read.catalog(), limits);
        this.read = new AppHelpReadTool(read, limits, version);
    }

    /** Builds both use cases on {@code catalog}. */
    public static AppHelpMcpTools over(HelpCatalog catalog, AppHelpToolLimits limits, String version) {
        return new AppHelpMcpTools(new HelpSearchUseCase(catalog), new HelpReadUseCase(catalog), limits, version);
    }

    /** Both tools in a fixed order: search, read. */
    public List<McpToolContribution> contributions() {
        return Collections.unmodifiableList(Arrays.asList(search.contribution(), read.contribution()));
    }
}

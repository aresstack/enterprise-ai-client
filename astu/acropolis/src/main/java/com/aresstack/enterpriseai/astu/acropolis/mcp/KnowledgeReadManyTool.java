package com.aresstack.enterpriseai.astu.acropolis.mcp;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeBookmarkIds;
import com.aresstack.enterpriseai.astu.acropolis.rag.KnowledgeAcquisition;
import com.aresstack.enterpriseai.astu.knowledge.KnowledgePassageId;
import com.aresstack.enterpriseai.proasteion.katagogion.McpContextResource;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolCall;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolContribution;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolHandler;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolParameter;
import com.aresstack.enterpriseai.proasteion.katagogion.McpToolResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * {@code knowledge_read_many(ids)} (issue #127): several already known resources in one call, read with the same
 * {@link KnowledgeResourceReader} as {@code knowledge_read}, at most {@link KnowledgeToolSettings#readManyParallelism()}
 * at a time on a pool this tool owns. Results keep the order of the input ids; equal ids (also a bookmark and the
 * resource id it maps to) are read once, and inputs that resolve to the same canonical resource are shown once.
 * A failed read never discards the others; the call is an error only when every read failed. The text budget
 * ({@link KnowledgeToolSettings#maxResponseChars()}) is shared fairly: short documents keep their full text, the rest
 * is split evenly among the long ones, each cut marked on its own.
 */
final class KnowledgeReadManyTool implements McpToolHandler {

    static final String NAME = KnowledgeAcquisition.READ_MANY_TOOL;
    static final String PARAM_IDS = "ids";

    private static final String EXAMPLE = "ids=[\"confluence:47110815\", \"confluence:47110816\"]";
    private static final String SEPARATOR = "\n\n---\n\n";
    /** Room each document's status line and the separators take; reserved before the text is shared. */
    private static final int FRAME_CHARS = 160;
    private static final int ID_CHARS = 200;

    private final KnowledgeResourceReader reader;
    private final KnowledgeToolSettings settings;
    private final ThreadPoolExecutor pool;

    KnowledgeReadManyTool(KnowledgeResourceReader reader, KnowledgeToolSettings settings) {
        this.reader = reader;
        this.settings = settings;
        int parallelism = settings.readManyParallelism();
        this.pool = new ThreadPoolExecutor(parallelism, parallelism, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(), new ReadThreads());
        this.pool.allowCoreThreadTimeOut(true); // idle tools hold no threads
    }

    McpToolContribution contribution() {
        return McpToolContribution.of(NAME,
                "Read several documents you already know in one call, when an answer needs them together (compare "
                        + "pages, collect facts from several hits). Pass the exact ids of knowledge_search hits, "
                        + "attachments or page bookmarks, at most " + settings.readManyMaxIds() + ". For one "
                        + "document use knowledge_read. This is no search: find unknown pages with knowledge_search "
                        + "or knowledge_tree first. Example: " + EXAMPLE + ".",
                this,
                McpToolParameter.stringArray(PARAM_IDS, true, "List of document ids from knowledge_search or page "
                        + "bookmarks, one id per element, at most " + settings.readManyMaxIds() + ". Example: "
                        + EXAMPLE));
    }

    @Override
    public McpToolResult invoke(McpToolCall call) {
        List<String> ids = new ArrayList<String>();
        for (String id : call.getStrings(PARAM_IDS)) {
            if (!ToolText.isBlank(id)) {
                ids.add(id.trim());
            }
        }
        if (ids.isEmpty()) {
            return McpToolResult.error("Missing argument: ids — example: " + EXAMPLE);
        }
        if (ids.size() > settings.readManyMaxIds()) {
            return McpToolResult.error("Too many ids: " + ids.size() + " (at most " + settings.readManyMaxIds()
                    + " per call). Read the most relevant ones first, the rest in a second call.");
        }
        List<McpToolResult> results = readAll(ids);
        return aggregate(ids, results);
    }

    /** One result per input id, in input order; each distinct key is read once, at most {@code parallelism} at once. */
    private List<McpToolResult> readAll(List<String> ids) {
        Map<String, Future<McpToolResult>> reads = new LinkedHashMap<String, Future<McpToolResult>>();
        for (final String id : ids) {
            String key = KnowledgeResourceReader.keyOf(id);
            if (!reads.containsKey(key)) {
                reads.put(key, pool.submit(() -> reader.read(id, Integer.MAX_VALUE)));
            }
        }
        List<McpToolResult> results = new ArrayList<McpToolResult>(ids.size());
        for (String id : ids) {
            results.add(await(id, reads.get(KnowledgeResourceReader.keyOf(id))));
        }
        return results;
    }

    private static McpToolResult await(String id, Future<McpToolResult> read) {
        try {
            McpToolResult result = read.get();
            return result == null ? McpToolResult.error("Reading '" + ToolText.oneLine(id, ID_CHARS)
                    + "' returned nothing.") : result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            read.cancel(true);
            return McpToolResult.error("Reading '" + ToolText.oneLine(id, ID_CHARS) + "' was interrupted.");
        } catch (ExecutionException e) {
            return McpToolResult.error("Reading '" + ToolText.oneLine(id, ID_CHARS) + "' failed. Try it alone "
                    + "with knowledge_read.");
        }
    }

    private McpToolResult aggregate(List<String> ids, List<McpToolResult> results) {
        int total = ids.size();
        // which position shows a canonical resource first; later positions refer to it
        Map<String, Integer> firstOf = new LinkedHashMap<String, Integer>();
        Integer[] sameAs = new Integer[total];
        List<Integer> shown = new ArrayList<Integer>();
        Set<String> provenanceKeys = new LinkedHashSet<String>();
        List<McpContextResource> provenance = new ArrayList<McpContextResource>();
        boolean anyOk = false;
        for (int i = 0; i < total; i++) {
            McpToolResult result = results.get(i);
            if (result.isError()) {
                continue;
            }
            anyOk = true;
            String canonical = canonical(ids.get(i), result);
            Integer first = firstOf.get(canonical);
            if (first != null) {
                sameAs[i] = first;
                continue;
            }
            firstOf.put(canonical, i);
            shown.add(i);
            for (McpContextResource resource : result.contextResources()) {
                if (provenanceKeys.add(resource.sourceId() + "\u0000" + resource.resourceId())) {
                    provenance.add(resource);
                }
            }
        }
        int[] budgets = share(results, shown, total);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < total; i++) {
            if (i > 0) {
                out.append(SEPARATOR);
            }
            String position = "[" + (i + 1) + "/" + total + "] ";
            McpToolResult result = results.get(i);
            if (result.isError()) {
                out.append(position).append("ERROR\nrequestedId: ").append(ToolText.oneLine(ids.get(i), ID_CHARS))
                        .append("\nerror: ").append(ToolText.oneLine(result.getText(), 400));
            } else if (sameAs[i] != null) {
                out.append(position).append("OK (same document as [").append(sameAs[i] + 1).append('/').append(total)
                        .append("], shown there)\nrequestedId: ").append(ToolText.oneLine(ids.get(i), ID_CHARS));
            } else {
                String text = result.getText();
                boolean cut = text.length() > budgets[i];
                out.append(position).append(cut ? "OK (truncated — read it alone with knowledge_read for more)" : "OK")
                        .append('\n').append(ToolText.truncate(text, budgets[i]));
            }
        }
        String text = ToolText.truncate(out.toString(), settings.maxResponseChars());
        if (!anyOk) {
            return McpToolResult.error("None of the " + total + " documents could be read.\n\n" + text);
        }
        return McpToolResult.ok(text, provenance);
    }

    /** The canonical resource a successful read names (its context resource), else the input's key. */
    private static String canonical(String id, McpToolResult result) {
        if (!result.contextResources().isEmpty()) {
            McpContextResource resource = result.contextResources().get(0);
            String key = resource.sourceId() + "\u0000" + resource.resourceId();
            // a passage read shows other text than the whole resource: keep it apart
            return isPassage(id) ? key + "\u0000" + KnowledgeResourceReader.keyOf(id) : key;
        }
        return KnowledgeResourceReader.keyOf(id);
    }

    private static boolean isPassage(String id) {
        return !KnowledgeBookmarkIds.isBookmark(id) && KnowledgePassageId.looksLike(id);
    }

    /**
     * Text budget per shown document: reserve the frames of all positions, then fill the documents from the shortest
     * up — a document needing less than an even share keeps its full text and leaves the rest to the longer ones.
     */
    private int[] share(List<McpToolResult> results, List<Integer> shown, int total) {
        int[] budgets = new int[total];
        if (shown.isEmpty()) {
            return budgets;
        }
        int available = settings.maxResponseChars() - total * FRAME_CHARS;
        List<Integer> open = new ArrayList<Integer>(shown);
        open.sort((a, b) -> Integer.compare(results.get(a).getText().length(), results.get(b).getText().length()));
        int remaining = open.size();
        for (int index : open) {
            int even = Math.max(ToolText.MARKER_RESERVE * 2, available / remaining);
            int need = results.get(index).getText().length();
            budgets[index] = Math.min(need, even);
            available -= budgets[index];
            remaining--;
        }
        return budgets;
    }

    /** Stops the read pool; reads already running finish. Idempotent. */
    void shutdown() {
        pool.shutdown();
    }

    private static final class ReadThreads implements ThreadFactory {
        private final AtomicInteger count = new AtomicInteger();

        @Override
        public Thread newThread(Runnable task) {
            Thread thread = new Thread(task, "knowledge-read-many-" + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}

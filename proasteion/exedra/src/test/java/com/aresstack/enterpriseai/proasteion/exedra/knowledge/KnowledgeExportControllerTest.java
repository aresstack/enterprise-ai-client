package com.aresstack.enterpriseai.proasteion.exedra.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.knowledge.KnowledgeExportUseCase;
import com.aresstack.enterpriseai.astu.acropolis.knowledge.PageNavigationUseCase;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportFormat;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportGraphics;
import com.aresstack.enterpriseai.proasteion.exedra.document.ExportSaveDialog;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;
import com.aresstack.enterpriseai.source.api.NavigationNode;
import com.aresstack.enterpriseai.source.api.testing.InMemoryNavigationSource;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** #115: the export asks for the file first, loads and writes off the EDT and never leaves a partial file. */
public class KnowledgeExportControllerTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private final InMemoryNavigationSource source = new InMemoryNavigationSource("wiki", 2)
            .space("DEV", "Development").page("DEV", "a", "Grobkonzept").page("a", "b", "Teil B")
            .page("a", "c", "Teil C");
    private final List<String> events = Collections.synchronizedList(new ArrayList<String>());
    private final List<Boolean> workOnEdt = Collections.synchronizedList(new ArrayList<Boolean>());
    private final CountDownLatch done = new CountDownLatch(1);
    private File target;
    private ExportFormat chosen;

    private final Executor work = runnable -> new Thread(() -> {
        workOnEdt.add(SwingUtilities.isEventDispatchThread());
        runnable.run();
    }).start();

    private final KnowledgeExportController.Dialogs dialogs = new KnowledgeExportController.Dialogs() {
        @Override
        public ExportSaveDialog.Choice<ExportFormat> chooseTarget(Component parent, String suggestedName) {
            events.add("choose " + suggestedName);
            return target == null ? null : new ExportSaveDialog.Choice<ExportFormat>(target, chosen);
        }

        @Override
        public KnowledgeExportController.ProgressView showProgress(Component parent, String title, Runnable cancel) {
            events.add("progress " + title);
            return new KnowledgeExportController.ProgressView() {
                @Override
                public void update(int loaded, int found, String page) {
                    events.add(loaded + "/" + found);
                }

                @Override
                public void close() {
                    events.add("closed");
                    done.countDown();
                }
            };
        }

        @Override
        public void showProblem(Component parent, String message) {
            events.add("problem " + message);
        }
    };

    private KnowledgeExportController controller() {
        return new KnowledgeExportController(new KnowledgeExportUseCase(new PageNavigationUseCase()),
                SwingUtilities::invokeLater, work, dialogs, () -> new ExportGraphics(null));
    }

    private NavigationNode node(String key) throws Exception {
        return source.position(source.idOf(key)).page();
    }

    @Test
    public void theSubtreeIsWrittenOffTheEdtAfterTheTargetWasChosen() throws Exception {
        target = new File(temp.getRoot(), "Grobkonzept.md");
        chosen = ExportFormat.MARKDOWN;
        final NavigationNode node = node("a");
        SwingUtilities.invokeAndWait(() -> controller().exportSubtree(null, source, node));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
        });
        assertEquals("the suggested name has no extension", "choose Grobkonzept", events.get(0));
        assertEquals(Collections.singletonList(Boolean.FALSE), workOnEdt);
        String markdown = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        assertTrue(markdown, markdown.startsWith("# Grobkonzept\n"));
        assertTrue(markdown, markdown.contains("## Teil B") && markdown.contains("## Teil C"));
        assertEquals("only the target, no temporary file", 1, temp.getRoot().listFiles().length);
    }

    @Test
    public void aMissingPageFailsTheExportWithItsNameAndWritesNothing() throws Exception {
        target = new File(temp.getRoot(), "Grobkonzept.pdf");
        chosen = ExportFormat.PDF;
        source.failWith("c", Kind.UNAVAILABLE);
        final NavigationNode node = node("a");
        SwingUtilities.invokeAndWait(() -> controller().exportSubtree(null, source, node));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> {
        });
        assertFalse(target.exists());
        assertEquals(0, temp.getRoot().listFiles().length);
        String problem = events.get(events.size() - 1);
        assertTrue(problem, problem.startsWith("problem " + KnowledgeExportController.INCOMPLETE));
        assertTrue(problem, problem.contains("Teil C: Quelle nicht erreichbar"));
    }

    @Test
    public void withoutATargetNothingIsLoaded() throws Exception {
        target = null;
        final NavigationNode node = node("a");
        SwingUtilities.invokeAndWait(() -> controller().exportSubtree(null, source, node));
        assertEquals(Collections.singletonList("choose Grobkonzept"), events);
        assertTrue(workOnEdt.isEmpty());
    }

    @Test
    public void aSinglePageExportsOnlyThatPage() throws Exception {
        target = new File(temp.getRoot(), "Seite.md");
        chosen = ExportFormat.MARKDOWN;
        SwingUtilities.invokeAndWait(() -> controller().exportPage(null, source, source.idOf("a"), "Grobkonzept"));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        String markdown = new String(Files.readAllBytes(target.toPath()), StandardCharsets.UTF_8);
        assertFalse(markdown, markdown.contains("Teil B"));
        for (String call : source.calls()) {
            assertFalse(call, call.startsWith("children"));
        }
    }

    @Test
    public void fileNamesKeepTheTitleWithoutForbiddenCharacters() {
        assertEquals("Grobkonzept Labor und Fabrik", ExportFileName.of("Grobkonzept Labor und Fabrik"));
        assertEquals("GK-LF 6. Bausteine Teil 1 2", ExportFileName.of("GK-LF 6. Bausteine: Teil 1/2"));
        assertEquals("Export", ExportFileName.of(" ... "));
        assertEquals("a.pdf", ExportFileName.withExtension(new File("a"), ExportFormat.PDF).getName());
        assertEquals("a.pdf", ExportFileName.withExtension(new File("a.PDF"), ExportFormat.PDF).getName());
        assertEquals("a.docx", ExportFileName.withExtension(new File("a.pdf"), ExportFormat.DOCX).getName());
    }

    @Test
    public void theChosenFileTypeDecidesTheWriter() throws Exception {
        target = new File(temp.getRoot(), "Grobkonzept.pdf");
        chosen = ExportFormat.PDF;
        SwingUtilities.invokeAndWait(() -> controller().exportPage(null, source, source.idOf("a"), "Grobkonzept"));
        assertTrue(done.await(10, TimeUnit.SECONDS));
        byte[] bytes = Files.readAllBytes(target.toPath());
        assertEquals("%PDF", new String(bytes, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    public void missingFiguresAreListedAfterSaving() {
        String message = KnowledgeExportController.listed(KnowledgeExportController.FIGURES_MISSING,
                java.util.Arrays.asList("Abbildung „a.png“: kein Zugriff"));
        assertTrue(message, message.startsWith(KnowledgeExportController.FIGURES_MISSING));
        assertTrue(message, message.contains("– Abbildung „a.png“: kein Zugriff"));
    }
}

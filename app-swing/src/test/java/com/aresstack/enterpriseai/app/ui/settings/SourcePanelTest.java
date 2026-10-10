package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Der Quellen-Dialog: Probleme blockieren das Speichern, Entfernen fragt einmal nach. */
public class SourcePanelTest {

    private final ComicPalette palette = ComicPalette.defaultPalette();

    private static final class Recording implements SourceActions {
        final List<String> calls = new ArrayList<String>();
        List<String> problems = Collections.emptyList();

        @Override
        public List<SourceForm> sources() {
            return Collections.emptyList();
        }

        @Override
        public List<String> validate(SourceForm draft, String originalId) {
            calls.add("validate " + draft.id() + "/" + originalId);
            return problems;
        }

        @Override
        public void save(SourceForm draft, String originalId) {
            calls.add("save " + draft.id() + " " + draft.url() + "/" + originalId);
        }

        @Override
        public void remove(String id) {
            calls.add("remove " + id);
        }

        @Override
        public void setEnabled(String id, boolean enabled) {
            calls.add("enabled " + id);
        }
    }

    @Test
    public void problemsBlockSavingUntilTheyAreFixed() throws Exception {
        final Recording actions = new Recording();
        final List<String> outcomes = new ArrayList<String>();
        onEdt(() -> {
            SourcePanel panel = new SourcePanel(SourceForm.builder("wiki", SourceForm.TYPE_MEDIAWIKI).build(), null,
                    actions, palette);
            panel.setListener((outcome, source) -> outcomes.add(outcome + (source == null ? "" : " " + source.id())));
            assertFalse("neue Quellen haben nichts zu entfernen", panel.removeButton().isVisible());
            actions.problems = Collections.singletonList("URL (source.wiki.apiUrl): fehlt");
            assertFalse(panel.save());
            assertTrue(panel.problemsText(), panel.problemsText().contains("source.wiki.apiUrl"));
            assertTrue(outcomes.isEmpty());

            actions.problems = Collections.emptyList();
            panel.editor().urlField().setText("http://127.0.0.1:9/w/api.php");
            panel.saveButton().doClick();
            assertEquals(Collections.singletonList("SAVED wiki"), outcomes);
            assertTrue(actions.calls.toString(), actions.calls.contains("save wiki http://127.0.0.1:9/w/api.php/null"));
        });
    }

    @Test
    public void removingAsksOnceBeforeItRemoves() throws Exception {
        final Recording actions = new Recording();
        final List<String> outcomes = new ArrayList<String>();
        onEdt(() -> {
            SourcePanel panel = new SourcePanel(SourceForm.builder("wiki", SourceForm.TYPE_MEDIAWIKI)
                    .url("http://127.0.0.1:9/w/api.php").build(), "wiki", actions, palette);
            panel.setListener((outcome, source) -> outcomes.add(String.valueOf(outcome)));
            assertTrue(panel.removeButton().isVisible());
            panel.removeButton().doClick();
            assertEquals(SourcePanel.CONFIRM_REMOVE_LABEL, panel.removeButton().getText());
            assertFalse(actions.calls.contains("remove wiki"));
            assertTrue(outcomes.isEmpty());
            panel.removeButton().doClick();
            assertTrue(actions.calls.contains("remove wiki"));
            assertEquals(Collections.singletonList("REMOVED"), outcomes);
        });
    }

    private static void onEdt(Runnable runnable) throws Exception {
        try {
            SwingUtilities.invokeAndWait(runnable);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException) {
                throw (RuntimeException) e.getCause();
            }
            if (e.getCause() instanceof Error) {
                throw (Error) e.getCause();
            }
            throw e;
        }
    }
}

package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.domain.source.SourceDefinition;
import com.aresstack.enterpriseai.domain.source.SourceSettings;
import com.aresstack.enterpriseai.source.mediawiki.MediaWikiSourceProvider;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Der Quellen-Dialog: Probleme blockieren das Speichern; die Felder kommen aus dem Quelltyp. */
public class SourcePanelTest {

    private final ComicPalette palette = ComicPalette.defaultPalette();

    private static final class Recording implements SourceActions {
        final List<String> calls = new ArrayList<String>();
        List<String> problems = Collections.emptyList();

        @Override
        public List<String> validate(SourceDefinition draft, String originalId) {
            calls.add("validate " + draft.id() + "/" + originalId);
            return problems;
        }

        @Override
        public void save(SourceDefinition draft, String originalId) {
            calls.add("save " + draft.id() + " " + draft.settings().get("apiUrl") + "/" + originalId);
        }

        @Override
        public SourceDefinition draft(String typeId) {
            calls.add("draft " + typeId);
            return new SourceDefinition("neu", typeId, true, SourceSettings.empty());
        }
    }

    @Test
    public void problemsBlockSavingUntilTheyAreFixed() throws Exception {
        final Recording actions = new Recording();
        final List<String> outcomes = new ArrayList<String>();
        onEdt(() -> {
            SourcePanel panel = new SourcePanel(new SourceDefinition("wiki", MediaWikiSourceProvider.TYPE_ID, true,
                    SourceSettings.empty()), null, Collections.singletonList(MediaWikiSourceProvider.sourceType()),
                    actions, palette);
            panel.setListener((outcome, source) -> outcomes.add(outcome + (source == null ? "" : " " + source.id())));
            actions.problems = Collections.singletonList("URL (source.wiki.apiUrl): fehlt");
            assertFalse(panel.save());
            assertTrue(panel.problemsText(), panel.problemsText().contains("source.wiki.apiUrl"));
            assertTrue(outcomes.isEmpty());

            actions.problems = Collections.emptyList();
            ((JTextField) panel.editor().input("apiUrl")).setText("http://127.0.0.1:9/w/api.php");
            panel.saveButton().doClick();
            assertEquals(Collections.singletonList("SAVED wiki"), outcomes);
            assertTrue(actions.calls.toString(), actions.calls.contains("save wiki http://127.0.0.1:9/w/api.php/null"));
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

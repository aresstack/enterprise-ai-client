package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.update.JarPatchBuilder;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatcher;
import com.aresstack.enterpriseai.proasteion.application.update.PatchChainExecutor;
import com.aresstack.enterpriseai.proasteion.application.update.PatchChainPlanner;
import com.aresstack.enterpriseai.proasteion.application.update.RunningJar;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Issues #92/#105: "Update einspielen …" in Merlins Kristallhöhle, eine Kette aus mehreren Paketen. */
public class JarUpdateActionTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void caveOffersTheUpdateButton() {
        MerlinCavePanel panel = new MerlinCavePanel(ComicPalette.defaultPalette());
        assertNotNull(find(panel, JarUpdateAction.BUTTON_TEXT));
    }

    @Test
    public void startFromClassDirectoriesExplainsWhyThereIsNothingToPatch() throws Exception {
        RunningJar classes = RunningJar.at(folder.newFolder("classes"));
        FakeDialogs dialogs = new FakeDialogs(java.util.Collections.<File>emptyList(), true);
        run(action(() -> classes, dialogs, busy -> {
        }, new ArrayList<String>()));
        assertEquals(Arrays.asList("info:" + classes.reason()), dialogs.shown);
        assertFalse("keine Dateiauswahl ohne Fat Jar", dialogs.chooserOpened);
    }

    @Test
    public void confirmedChainShowsStepsAndChangesAndThenTheNewFile() throws Exception {
        File install = folder.newFolder("install");
        File base = jar(new File(install, "enterprise-ai-client-0.56.0.jar"), "0.56.0", "old");
        File first = patch(base, jar(folder.newFile("r57.jar"), "0.57.0", "mid"), "- Lesebereich responsiv");
        File second = patch(jar(folder.newFile("b57.jar"), "0.57.0", "mid"),
                jar(folder.newFile("r58.jar"), "0.58.0", "new"), "- Tempo-Stufen");
        FakeDialogs dialogs = new FakeDialogs(Arrays.asList(second, first), true);
        List<Boolean> busy = new ArrayList<Boolean>();
        List<String> status = new ArrayList<String>();
        run(action(() -> RunningJar.at(base), dialogs, busy::add, status));

        File created = new File(install, "enterprise-ai-client-0.58.0.jar");
        assertTrue(created.isFile());
        assertEquals(dialogs.shown.toString(), 2, dialogs.shown.size());
        String summary = dialogs.shown.get(0);
        assertTrue(summary, summary.startsWith("confirm:Von: 0.56.0\nNach: 0.58.0\nSchritte: 2"));
        assertTrue(summary, summary.contains("0.56.0 → 0.57.0"));
        assertTrue(summary, summary.contains("- Lesebereich responsiv"));
        assertTrue(summary, summary.contains("- Tempo-Stufen"));
        assertTrue(summary, summary.contains(created.getAbsolutePath()));
        String done = dialogs.shown.get(1);
        assertTrue(done, done.startsWith("info:Version 0.58.0 liegt jetzt unter"));
        assertTrue(done, done.contains("Neue Version selbst starten"));
        assertTrue(status.toString(), status.contains("Schritt 2 von 2: 0.57.0 → 0.58.0"));
        assertEquals(Boolean.FALSE, busy.get(busy.size() - 1));
        assertEquals(2, install.list().length);
    }

    @Test
    public void cancellingOrAPatchForAnotherVersionCreatesNothing() throws Exception {
        File install = folder.newFolder("install");
        File base = jar(new File(install, "enterprise-ai-client-0.56.0.jar"), "0.56.0", "old");
        File patch = patch(base, jar(folder.newFile("released.jar"), "0.57.0", "new"), "");
        FakeDialogs cancel = new FakeDialogs(Arrays.asList(patch), false);
        run(action(() -> RunningJar.at(base), cancel, busy -> {
        }, new ArrayList<String>()));
        assertEquals(1, install.list().length);

        File other = jar(new File(folder.newFolder("other"), "enterprise-ai-client-0.55.0.jar"), "0.55.0", "x");
        FakeDialogs wrong = new FakeDialogs(Arrays.asList(patch), true);
        run(action(() -> RunningJar.at(other), wrong, busy -> {
        }, new ArrayList<String>()));
        assertEquals(1, wrong.shown.size());
        assertTrue(wrong.shown.get(0), wrong.shown.get(0).startsWith("error:Update nicht eingespielt"));
        assertTrue(wrong.shown.get(0), wrong.shown.get(0).contains("0.56.0"));
    }

    private File patch(File base, File target, String changes) throws Exception {
        File patch = folder.newFile();
        new JarPatchBuilder().build(base, target, changes, patch);
        return patch;
    }

    private static JarUpdateAction action(java.util.function.Supplier<RunningJar> running, FakeDialogs dialogs,
                                          java.util.function.Consumer<Boolean> busy, List<String> status) {
        return new JarUpdateAction(null, running, new PatchChainPlanner(), new PatchChainExecutor(new JarPatcher()),
                Runnable::run, dialogs, busy, status::add);
    }

    private static File jar(File file, String version, String content) throws IOException {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attributes.put(Attributes.Name.IMPLEMENTATION_TITLE, RunningJar.FAT_JAR_TITLE);
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, version);
        try (JarOutputStream out = new JarOutputStream(new FileOutputStream(file), manifest)) {
            out.putNextEntry(new ZipEntry("app/Main.class"));
            out.write(content.getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return file;
    }

    /** Runs the flow from the event dispatch thread and waits until all follow-up steps are done. */
    private static void run(JarUpdateAction action) throws Exception {
        SwingUtilities.invokeAndWait(action::run);
        for (int i = 0; i < 4; i++) {
            SwingUtilities.invokeAndWait(() -> {
            });
        }
    }

    private static AbstractButton find(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof AbstractButton && text.equals(((AbstractButton) child).getText())) {
                return (AbstractButton) child;
            }
            if (child instanceof Container) {
                AbstractButton found = find((Container) child, text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static final class FakeDialogs implements JarUpdateAction.Dialogs {
        final List<String> shown = new ArrayList<String>();
        private final List<File> patches;
        private final boolean answer;
        boolean chooserOpened;

        FakeDialogs(List<File> patches, boolean answer) {
            this.patches = patches;
            this.answer = answer;
        }

        @Override
        public List<File> choosePatches(Component parent) {
            chooserOpened = true;
            return patches;
        }

        @Override
        public boolean confirm(Component parent, String message) {
            shown.add("confirm:" + message);
            return answer;
        }

        @Override
        public void info(Component parent, String message) {
            shown.add("info:" + message);
        }

        @Override
        public void error(Component parent, String message) {
            shown.add("error:" + message);
        }
    }
}

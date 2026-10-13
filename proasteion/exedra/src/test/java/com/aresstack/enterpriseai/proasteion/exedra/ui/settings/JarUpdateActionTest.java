package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.proasteion.application.update.JarPatchBuilder;
import com.aresstack.enterpriseai.proasteion.application.update.JarPatcher;
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

/** Issue #92: "Update einspielen …" in Merlins Kristallhöhle. */
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
        FakeDialogs dialogs = new FakeDialogs(null, true);
        run(new JarUpdateAction(null, () -> classes, new JarPatcher(), Runnable::run, dialogs, busy -> {
        }));
        assertEquals(Arrays.asList("info:" + classes.reason()), dialogs.shown);
        assertFalse("keine Dateiauswahl ohne Fat Jar", dialogs.chooserOpened);
    }

    @Test
    public void confirmedUpdateShowsVersionAndChangesAndThenTheNewFile() throws Exception {
        File install = folder.newFolder("install");
        File base = jar(new File(install, "enterprise-ai-client-0.56.0.jar"), "0.56.0", "old");
        File patch = patch(base, "- Lesebereich responsiv");
        FakeDialogs dialogs = new FakeDialogs(patch, true);
        List<Boolean> busy = new ArrayList<Boolean>();
        run(new JarUpdateAction(null, () -> RunningJar.at(base), new JarPatcher(), Runnable::run, dialogs,
                busy::add));

        File created = new File(install, "enterprise-ai-client-0.57.0.jar");
        assertTrue(created.isFile());
        assertEquals(2, dialogs.shown.size());
        String summary = dialogs.shown.get(0);
        assertTrue(summary, summary.startsWith("confirm:Update 0.56.0 → 0.57.0"));
        assertTrue(summary, summary.contains("- Lesebereich responsiv"));
        assertTrue(summary, summary.contains(created.getAbsolutePath()));
        String done = dialogs.shown.get(1);
        assertTrue(done, done.startsWith("info:Version 0.57.0 liegt jetzt unter"));
        assertTrue(done, done.contains(created.getAbsolutePath()));
        assertEquals(Boolean.FALSE, busy.get(busy.size() - 1));
    }

    @Test
    public void cancellingOrAPatchForAnotherVersionCreatesNothing() throws Exception {
        File install = folder.newFolder("install");
        File base = jar(new File(install, "enterprise-ai-client-0.56.0.jar"), "0.56.0", "old");
        File patch = patch(base, "");
        FakeDialogs cancel = new FakeDialogs(patch, false);
        run(new JarUpdateAction(null, () -> RunningJar.at(base), new JarPatcher(), Runnable::run, cancel, busy -> {
        }));
        assertEquals(1, install.list().length);

        File other = jar(new File(folder.newFolder("other"), "enterprise-ai-client-0.55.0.jar"), "0.55.0", "x");
        FakeDialogs wrong = new FakeDialogs(patch, true);
        run(new JarUpdateAction(null, () -> RunningJar.at(other), new JarPatcher(), Runnable::run, wrong, busy -> {
        }));
        assertEquals(1, wrong.shown.size());
        assertTrue(wrong.shown.get(0), wrong.shown.get(0).startsWith("error:Update nicht eingespielt"));
        assertTrue(wrong.shown.get(0), wrong.shown.get(0).contains("0.56.0"));
    }

    private File patch(File base, String changes) throws Exception {
        File target = jar(folder.newFile("released.jar"), "0.57.0", "new");
        File patch = folder.newFile("patch.zip");
        new JarPatchBuilder().build(base, target, changes, patch);
        return patch;
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
        private final File patch;
        private final boolean answer;
        boolean chooserOpened;

        FakeDialogs(File patch, boolean answer) {
            this.patch = patch;
            this.answer = answer;
        }

        @Override
        public File choosePatch(Component parent) {
            chooserOpened = true;
            return patch;
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

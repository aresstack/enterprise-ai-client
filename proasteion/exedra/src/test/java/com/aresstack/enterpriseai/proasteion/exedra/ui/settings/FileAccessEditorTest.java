package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Issue #120: folders are granted and revoked; allow-all needs the second confirmation, turning it off does not. */
public class FileAccessEditorTest {

    @Test
    public void allowAllOnlyTurnsOnAfterTheSecondConfirmation() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            AtomicBoolean answer = new AtomicBoolean(false);
            AtomicInteger asked = new AtomicInteger();
            FileAccessEditor editor = new FileAccessEditor(ComicPalette.defaultPalette(), parent -> {
                asked.incrementAndGet();
                return answer.get();
            });

            editor.allowAll().doClick();
            assertFalse("declined: stays off", editor.allowAll().isSelected());
            assertEquals(1, asked.get());

            answer.set(true);
            editor.allowAll().doClick();
            assertTrue(editor.allowAll().isSelected());
            assertEquals(2, asked.get());

            editor.allowAll().doClick();
            assertFalse("turning off needs no confirmation", editor.allowAll().isSelected());
            assertEquals(2, asked.get());

            SettingsForm.Builder b = SettingsForm.builder();
            editor.store(b);
            assertFalse(b.build().fileAccessAllowAll());
        });
    }

    @Test
    public void foldersAreGrantedOnceAndRevoked() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            FileAccessEditor editor = new FileAccessEditor(ComicPalette.defaultPalette(), parent -> true);
            editor.load(SettingsForm.builder().fileAccessRoots(Collections.singletonList("C:\\Projekte")).build());

            editor.path().setText("  \\\\server\\team\\wissen ");
            editor.addButton().doClick();
            editor.path().setText("C:\\Projekte");
            editor.addButton().doClick();
            assertEquals(Arrays.asList("C:\\Projekte", "\\\\server\\team\\wissen"), editor.roots());
            assertEquals(2, editor.list().getComponentCount());

            editor.revoke("C:\\Projekte");
            SettingsForm.Builder b = SettingsForm.builder();
            editor.store(b);
            assertEquals(Collections.singletonList("\\\\server\\team\\wissen"), b.build().fileAccessRoots());
        });
    }
}

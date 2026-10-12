package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import org.junit.Test;

import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DownloadProgressTest {

    private static final long MB = 1024L * 1024;

    @Test
    public void barCarriesPercentAndBytesAndTheFileGoesBelow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DownloadProgress progress = new DownloadProgress("Download abbrechen");
            progress.reset("Vorbereiten …");
            assertTrue(progress.bar().isIndeterminate());
            assertEquals("Vorbereiten …", progress.bar().getString());
            progress.file("embed_tokens_q4.onnx_data", 10, 12, 740 * MB, 1720 * MB);
            assertFalse(progress.bar().isIndeterminate());
            assertEquals(43, progress.bar().getValue());
            assertEquals("43 % · 740,0 MB / 1,68 GB", progress.bar().getString());
            assertEquals("Datei 10 von 12 · embed_tokens_q4.onnx_data", progress.caption());
            progress.cancelling();
            assertEquals("Download wird abgebrochen …", progress.bar().getString());
            assertFalse(progress.cancelButton().isEnabled());
        });
    }

    @Test
    public void stopCrossIsExactlyAsHighAsTheThinBar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DownloadProgress progress = new DownloadProgress("Download abbrechen");
            int height = progress.bar().getPreferredSize().height;
            assertEquals(height, progress.cancelButton().getPreferredSize().height);
            assertEquals(height, progress.cancelButton().getPreferredSize().width);
            int textLine = progress.bar().getFontMetrics(progress.bar().getFont()).getHeight();
            assertTrue("one text line high, got " + height, height <= textLine + 4);
        });
    }
}

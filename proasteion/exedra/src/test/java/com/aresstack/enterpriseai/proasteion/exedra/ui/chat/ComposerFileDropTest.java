package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Dateien per Drag &amp; Drop auf den Composer: derselbe Anhangspfad wie die Büroklammer, Text bleibt Text. */
public class ComposerFileDropTest {

    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void oneDroppedFileBecomesAnAttachment() throws Exception {
        final File file = folder.newFile("bericht.pdf");
        Edt.run(() -> {
            ChatComposerPanel composer = composer();
            assertTrue(importInto(composer.editor(), files(file)));
            assertEquals(Collections.singletonList(normalized(file)), composer.attachments());
            assertEquals(ChatComposerPanel.ATTACH_TOOLTIP + " (1 vorgemerkt)", composer.attachButton().getToolTipText());
        });
    }

    @Test
    public void severalDroppedFilesAreAllAttached() throws Exception {
        final File first = folder.newFile("a.txt");
        final File second = folder.newFile("b.docx");
        final File third = folder.newFile("c.png");
        Edt.run(() -> {
            ChatComposerPanel composer = composer();
            assertTrue(importInto(composer, files(first, second, third)));
            assertEquals(Arrays.asList(normalized(first), normalized(second), normalized(third)),
                    composer.attachments());
        });
    }

    @Test
    public void unsupportedFlavorsNeverBecomeAttachments() throws Exception {
        Edt.run(() -> {
            ChatComposerPanel composer = composer();
            composer.editor().setText("Entwurf");
            assertFalse("Text auf der Fläche", canImport(composer, new StringSelection("C:\\temp\\a.txt")));
            assertFalse(importInto(composer, new StringSelection("C:\\temp\\a.txt")));
            Transferable internal = new LocalObject();
            assertFalse("internes Objekt am Editor", canImport(composer.editor(), internal));
            assertFalse(importInto(composer.editor(), internal));
            assertFalse(importInto(composer, internal));
            assertTrue(composer.attachments().isEmpty());
            assertEquals("Entwurf", composer.editor().getText());
        });
    }

    @Test
    public void dropAndFileChooserTakeTheSamePath() throws Exception {
        final File chosen = folder.newFile("gewaehlt.txt");
        final File dropped = folder.newFile("gezogen.txt");
        final File directory = folder.newFolder("ordner");
        Edt.run(() -> {
            ChatComposerPanel viaChooser = composer();
            viaChooser.setFileChooser(() -> Arrays.asList(chosen.toPath(), directory.toPath()));
            viaChooser.attachButton().doClick();

            ChatComposerPanel viaDrop = composer();
            assertTrue(importInto(viaDrop.editor(), files(dropped, directory)));

            // Gleiche Regeln: Ordner übergangen, Ordner gemerkt, Statuszeile und Tooltip wie nach der Auswahl.
            assertEquals(Collections.singletonList(normalized(chosen)), viaChooser.attachments());
            assertEquals(Collections.singletonList(normalized(dropped)), viaDrop.attachments());
            assertEquals(viaChooser.lastDirectory(), viaDrop.lastDirectory());
            assertEquals(viaChooser.attachButton().getToolTipText(), viaDrop.attachButton().getToolTipText());
            assertEquals(viaChooser.getToolTipText(), viaDrop.getToolTipText());

            // Was die Büroklammer sperrt, sperrt auch den Drop.
            ChatComposerPanel without = new ChatComposerPanel(new ChatShellModel(() -> 0L), new RecordingActions(),
                    ComicPalette.defaultPalette());
            assertFalse(canImport(without.editor(), files(dropped)));
            assertFalse(importInto(without, files(dropped)));
            assertTrue(without.attachments().isEmpty());
        });
    }

    @Test
    public void droppingFilesLeavesTheDraftUntouched() throws Exception {
        final File file = folder.newFile("notiz.md");
        Edt.run(() -> {
            ChatComposerPanel composer = composer();
            composer.editor().setText("Bitte zusammenfassen:\nzweite Zeile");
            composer.editor().setCaretPosition(5);
            assertTrue(importInto(composer.editor(), files(file)));
            assertTrue(importInto(composer, files(file)));
            assertEquals("Bitte zusammenfassen:\nzweite Zeile", composer.editor().getText());
            assertEquals(1, composer.attachments().size());
        });
    }

    private static ChatComposerPanel composer() {
        return new ChatComposerPanel(new ChatShellModel(() -> 0L), new AttachingActions(),
                ComicPalette.defaultPalette());
    }

    private static boolean canImport(JComponent target, Transferable data) {
        return target.getTransferHandler().canImport(new TransferHandler.TransferSupport(target, data));
    }

    private static boolean importInto(JComponent target, Transferable data) {
        return target.getTransferHandler().importData(new TransferHandler.TransferSupport(target, data));
    }

    private static Path normalized(File file) {
        return file.toPath().toAbsolutePath().normalize();
    }

    private static Transferable files(File... files) {
        final List<File> list = new ArrayList<File>(Arrays.asList(files));
        return new Transferable() {
            @Override
            public DataFlavor[] getTransferDataFlavors() {
                return new DataFlavor[] {DataFlavor.javaFileListFlavor};
            }

            @Override
            public boolean isDataFlavorSupported(DataFlavor flavor) {
                return DataFlavor.javaFileListFlavor.equals(flavor);
            }

            @Override
            public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException {
                if (!isDataFlavorSupported(flavor)) {
                    throw new UnsupportedFlavorException(flavor);
                }
                return list;
            }
        };
    }

    /** Ein internes Swing-Objekt (JVM-lokal), wie es beim Ziehen innerhalb der Anwendung entsteht. */
    private static final class LocalObject implements Transferable {
        private final DataFlavor flavor;

        LocalObject() {
            try {
                flavor = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + ";class=java.lang.Object");
            } catch (ClassNotFoundException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[] {flavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor candidate) {
            return flavor.equals(candidate);
        }

        @Override
        public Object getTransferData(DataFlavor candidate) {
            return new Object();
        }
    }

    private static final class AttachingActions implements ChatShellActions {
        @Override
        public void sendRequested(String text, boolean ragEnabled) {
        }

        @Override
        public boolean supportsAttachments() {
            return true;
        }

        @Override
        public void stopRequested() {
        }
    }
}

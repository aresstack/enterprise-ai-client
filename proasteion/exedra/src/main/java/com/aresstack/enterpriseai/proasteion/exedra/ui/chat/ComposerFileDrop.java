package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.event.InputEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TooManyListenersException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Dateien aus dem Explorer (oder der Zwischenablage) auf den Composer: übersetzt eine Dateiliste
 * ({@link DataFlavor#javaFileListFlavor}) in {@code List<Path>} und reicht sie an denselben Anhangspfad wie die
 * Büroklammer weiter. Alles andere (Text, interne Swing-Objekte) wird nie zum Anhang: am Editor geht es an dessen
 * eigenen Handler (Text einfügen wie bisher), sonst wird es abgelehnt. Gezogene Dateien werden immer nur kopiert,
 * nie verschoben, damit der Explorer das Original behält.
 */
final class ComposerFileDrop extends TransferHandler {

    private static final long serialVersionUID = 1L;

    private final transient Consumer<List<Path>> target;
    private final transient BooleanSupplier accepting;
    private final transient Consumer<Boolean> hover;
    private final TransferHandler fallback;

    /**
     * @param target    der bestehende Anhangspfad (derselbe wie nach der Dateiauswahl)
     * @param accepting ob Anhänge gerade angenommen werden (wie die Büroklammer: sichtbar und aktiv)
     * @param hover     Rückmeldung, ob gültige Dateien über dem Ziel schweben
     * @param fallback  der ursprüngliche Handler der Komponente für alles außer Dateien, oder {@code null}
     */
    ComposerFileDrop(Consumer<List<Path>> target, BooleanSupplier accepting, Consumer<Boolean> hover,
                     TransferHandler fallback) {
        this.target = target;
        this.accepting = accepting;
        this.hover = hover;
        this.fallback = fallback;
    }

    /** Setzt den Handler auf die Komponente und meldet das Ende des Schwebens (Verlassen, Ablegen) zurück. */
    static void install(JComponent component, Consumer<List<Path>> target, BooleanSupplier accepting,
                        Consumer<Boolean> hover, boolean keepOwnHandler) {
        TransferHandler own = keepOwnHandler ? component.getTransferHandler() : null;
        component.setTransferHandler(new ComposerFileDrop(target, accepting, hover, own));
        DropTarget dropTarget = component.getDropTarget(); // headless gibt es keinen
        if (dropTarget == null) {
            return;
        }
        try {
            dropTarget.addDropTargetListener(new DropTargetAdapter() {
                @Override
                public void dragExit(DropTargetEvent event) {
                    hover.accept(Boolean.FALSE);
                }

                @Override
                public void drop(DropTargetDropEvent event) {
                    hover.accept(Boolean.FALSE);
                }
            });
        } catch (TooManyListenersException ignored) {
            // Swings DropTarget nimmt mehrere Beobachter; ohne ihn fehlt nur das Ende der Hervorhebung.
        }
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return fallback != null && fallback.canImport(support);
        }
        boolean accepted = accepting.getAsBoolean();
        if (accepted && support.isDrop()) {
            if ((support.getSourceDropActions() & COPY) == COPY) {
                support.setDropAction(COPY);
            } else {
                accepted = false;
            }
        }
        if (support.isDrop()) {
            hover.accept(Boolean.valueOf(accepted));
        }
        return accepted;
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return fallback != null && fallback.importData(support);
        }
        if (support.isDrop()) {
            hover.accept(Boolean.FALSE);
        }
        if (!canImport(support)) {
            return false;
        }
        List<Path> files = files(support.getTransferable());
        if (files.isEmpty()) {
            return false;
        }
        target.accept(files);
        return true;
    }

    private static List<Path> files(Transferable transferable) {
        List<Path> files = new ArrayList<Path>();
        try {
            Object data = transferable.getTransferData(DataFlavor.javaFileListFlavor);
            if (data instanceof List) {
                for (Object item : (List<?>) data) {
                    if (item instanceof File) {
                        files.add(((File) item).toPath());
                    }
                }
            }
        } catch (UnsupportedFlavorException | IOException ex) {
            return new ArrayList<Path>();
        }
        return files;
    }

    // Kopieren/Ausschneiden im Editor bleibt Sache seines eigenen Handlers.

    @Override
    public int getSourceActions(JComponent component) {
        return fallback == null ? NONE : fallback.getSourceActions(component);
    }

    @Override
    public void exportToClipboard(JComponent component, Clipboard clipboard, int action) {
        if (fallback != null) {
            fallback.exportToClipboard(component, clipboard, action);
        } else {
            super.exportToClipboard(component, clipboard, action);
        }
    }

    @Override
    public void exportAsDrag(JComponent component, InputEvent event, int action) {
        if (fallback != null) {
            fallback.exportAsDrag(component, event, action);
        } else {
            super.exportAsDrag(component, event, action);
        }
    }
}

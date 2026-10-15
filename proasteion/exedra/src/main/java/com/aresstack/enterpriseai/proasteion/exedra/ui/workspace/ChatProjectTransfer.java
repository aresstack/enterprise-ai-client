package com.aresstack.enterpriseai.proasteion.exedra.ui.workspace;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;

/**
 * Drag and drop in the chat list (#143): a row can be dragged (a chat or a project) and/or be a drop target (a
 * project, the top level of the project tree, or "Chats ohne Projekt"). Payloads are plain strings
 * ({@code chat:<id>}, {@code project:<id>}); what a drop does decides the {@link Drops} of the workspace.
 */
final class ChatProjectTransfer extends TransferHandler {

    static final String CHAT = "chat:";
    static final String PROJECT = "project:";

    /** Where something is dropped. */
    static final class Target {

        /** Onto the "Chats ohne Projekt" headers: a chat leaves its project. */
        static final Target NO_PROJECT = new Target(null, false);
        /** Onto the "PROJEKTE" header: a project moves to the top level. */
        static final Target TOP_LEVEL = new Target(null, true);

        final String projectId;
        final boolean topLevel;

        private Target(String projectId, boolean topLevel) {
            this.projectId = projectId;
            this.topLevel = topLevel;
        }

        static Target project(String projectId) {
            return new Target(projectId, false);
        }
    }

    /** Decides and performs drops. */
    interface Drops {
        boolean accepts(String payload, Target target);

        boolean drop(String payload, Target target);
    }

    private final String payload;
    private final Target target;
    private final Drops drops;

    /**
     * @param payload what dragging this row carries, or {@code null} (not draggable)
     * @param target  what dropping onto this row means, or {@code null} (no drop target)
     */
    ChatProjectTransfer(String payload, Target target, Drops drops) {
        this.payload = payload;
        this.target = target;
        this.drops = drops;
    }

    @Override
    public int getSourceActions(JComponent component) {
        return payload == null ? NONE : MOVE;
    }

    @Override
    protected Transferable createTransferable(JComponent component) {
        return payload == null ? null : new StringSelection(payload);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (target == null || !support.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return false;
        }
        String dropped = read(support.getTransferable());
        return dropped != null && !dropped.equals(payload) && drops.accepts(dropped, target);
    }

    @Override
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        return drops.drop(read(support.getTransferable()), target);
    }

    private static String read(Transferable transferable) {
        try {
            Object data = transferable.getTransferData(DataFlavor.stringFlavor);
            String text = data == null ? null : data.toString();
            return text != null && (text.startsWith(CHAT) || text.startsWith(PROJECT)) ? text : null;
        } catch (UnsupportedFlavorException | IOException | RuntimeException unreadable) {
            return null;
        }
    }
}

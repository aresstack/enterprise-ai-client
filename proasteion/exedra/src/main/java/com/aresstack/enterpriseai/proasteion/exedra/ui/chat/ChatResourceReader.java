package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

/**
 * Der Weg einer Ressource des Chats in den Lesebereich, wie die Ressourcenübersicht im Composer ihn anbietet
 * („Im Lesebereich öffnen“). Getrennt vom Anhängen: Öffnen nimmt die Ressource nie in den Entwurf. Erfüllt wird das
 * von der Anbindung an den vorhandenen Lesebereich; die Ansicht kennt keine Kennungsformate.
 */
public interface ChatResourceReader {

    /** Whether the resource maps to something the reader can show; otherwise no reader action is offered. */
    boolean canRead(ChatResource resource);

    /** Show the resource in the reader as a normal preview (UI thread); ignore resources {@link #canRead} rejects. */
    void read(ChatResource resource);
}

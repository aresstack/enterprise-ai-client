package com.aresstack.enterpriseai.proasteion.exedra.ui.reader;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;

/** Öffnet eine Datei mit dem Programm, das das System dafür vorsieht (Anhang-Overlay „Öffnen“). */
public interface ReaderFileOpener {

    void open(File file) throws IOException;

    /** {@link Desktop#open}; ohne Desktop-Unterstützung ein Fehler mit Grund. */
    static ReaderFileOpener system() {
        return file -> {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                throw new IOException("Das System kann Dateien hier nicht öffnen");
            }
            Desktop.getDesktop().open(file);
        };
    }
}

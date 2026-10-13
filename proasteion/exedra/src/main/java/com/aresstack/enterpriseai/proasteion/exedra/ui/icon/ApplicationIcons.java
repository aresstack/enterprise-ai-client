package com.aresstack.enterpriseai.proasteion.exedra.ui.icon;

import javax.imageio.ImageIO;
import java.awt.Image;
import java.awt.Window;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Das Anwendungsicon (dunkle Kachel, weiße Sprechblase, goldenes Funkeln) für Taskleiste, Alt+Tab und
 * Fensterkopf. Die Größen liegen als PNG neben {@code app-icon-source.png}, dem unveränderten freigegebenen
 * Motiv; {@code tools/app-icon/generate_app_icons.py} erzeugt sie daraus, ebenso {@code app-icon.ico} für
 * Verknüpfungen und eine spätere Windows-Verpackung. Java 8 kennt {@code java.awt.Taskbar} nicht: das Fenstericon
 * ist der Weg, über den Windows das Symbol des Taskleistenknopfs bekommt.
 */
public final class ApplicationIcons {

    /** Ordner der Icon-Ressourcen im Klassenpfad. */
    public static final String RESOURCE_DIRECTORY = "/com/aresstack/enterpriseai/proasteion/exedra/ui/icon/";

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private static final Logger LOG = Logger.getLogger(ApplicationIcons.class.getName());

    private ApplicationIcons() {
    }

    /** Alle ausgelieferten Kantenlängen, klein nach groß; Windows wählt daraus je Darstellung die passende. */
    public static int[] sizes() {
        return SIZES.clone();
    }

    /** Ressourcenpfad der PNG-Datei mit der Kantenlänge {@code size}. */
    public static String resource(int size) {
        return RESOURCE_DIRECTORY + "app-icon-" + size + ".png";
    }

    /**
     * Lädt alle Größen. Fehlt eine Datei oder ist sie unlesbar, wird das protokolliert (ein Java-Logo in der
     * Taskleiste ist kein normaler Zustand) und die übrigen Größen werden trotzdem geliefert.
     */
    public static List<Image> load() {
        List<Image> images = new ArrayList<Image>(SIZES.length);
        for (int size : SIZES) {
            String resource = resource(size);
            try (InputStream in = ApplicationIcons.class.getResourceAsStream(resource)) {
                Image image = in == null ? null : ImageIO.read(in);
                if (image == null) {
                    LOG.warning("Anwendungsicon fehlt oder ist unlesbar: " + resource);
                } else {
                    images.add(image);
                }
            } catch (IOException exception) {
                LOG.log(Level.WARNING, "Anwendungsicon nicht lesbar: " + resource, exception);
            }
        }
        if (images.isEmpty()) {
            LOG.severe("Kein Anwendungsicon geladen; Windows zeigt das Java-Logo.");
        }
        return Collections.unmodifiableList(images);
    }

    /** Setzt das Anwendungsicon auf {@code window}. Vor {@code setVisible(true)} aufrufen. */
    public static void apply(Window window) {
        if (window == null) {
            return;
        }
        List<Image> images = load();
        if (!images.isEmpty()) {
            window.setIconImages(images);
        }
    }
}

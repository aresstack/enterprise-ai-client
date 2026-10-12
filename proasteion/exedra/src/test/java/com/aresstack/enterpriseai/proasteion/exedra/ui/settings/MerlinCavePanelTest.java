package com.aresstack.enterpriseai.proasteion.exedra.ui.settings;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.io.InputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Merlins Kristallhöhle: das Originalbild ersetzt den Platzhalter, Merlin selbst ist anklickbar. */
public class MerlinCavePanelTest {

    @Test
    public void caveImageIsOnTheClasspath() throws Exception {
        try (InputStream stream = MerlinCavePanel.class.getResourceAsStream("/merlin-crystal-cave.jpg")) {
            assertNotNull("merlin-crystal-cave.jpg fehlt auf dem Klassenpfad", stream);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image);
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
        }
    }

    @Test
    public void panelShowsClickableMerlinInsteadOfPlaceholderAndKeepsStatus() {
        MerlinCavePanel panel = new MerlinCavePanel(ComicPalette.defaultPalette());
        assertFalse(containsLabel(panel, "Merlins Kristallhöhle wartet auf ihr Bild."));
        assertTrue(containsLabel(panel, "Aufenthaltsort: Kristallhöhle  ·  Status: Standby seit Einführung der KI"));
        Component scene = findByAccessibleName(panel, "Merlin um Rat fragen");
        assertNotNull("Merlin muss in der Höhle anklickbar sein", scene);
        assertTrue(scene.isFocusable());
    }

    private static boolean containsLabel(Container container, String text) {
        for (Component child : container.getComponents()) {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText())) {
                return true;
            }
            if (child instanceof Container && containsLabel((Container) child, text)) {
                return true;
            }
        }
        return false;
    }

    private static Component findByAccessibleName(Container container, String name) {
        for (Component child : container.getComponents()) {
            if (child.getAccessibleContext() != null
                    && name.equals(child.getAccessibleContext().getAccessibleName())) {
                return child;
            }
            if (child instanceof Container) {
                Component found = findByAccessibleName((Container) child, name);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The globe popover is a comic speech bubble opening upwards: pointer at the bottom, round corners that let the
 * backdrop show through, and comic-styled check boxes, sliders and spinners.
 */
public class BrowserBudgetPopupComicTest {

    private static final Color BACKDROP = Color.MAGENTA;

    @Test
    public void bubbleHasRoundTransparentCornersAndThePointerAtTheBottom() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserBudgetPopup popup = popup();
            BufferedImage image = render(popup);
            int w = image.getWidth();
            int h = image.getHeight();
            assertEquals("top left corner is see-through", BACKDROP.getRGB(), image.getRGB(1, 1));
            assertEquals("top right corner is see-through", BACKDROP.getRGB(), image.getRGB(w - 2, 1));
            assertNotEquals("the pointer's tip reaches the bottom", BACKDROP.getRGB(),
                    image.getRGB(popup.pointerX(), h - 3));

            Shape shape = BrowserBudgetPopup.bubbleShape(200, 100, 30);
            Rectangle bounds = shape.getBounds();
            assertTrue("the pointer hangs below the body", shape.contains(30, 100 - 3));
            assertFalse("no pointer above the body", shape.contains(30, 0.5));
            assertTrue(bounds.height <= 100);
        });
    }

    @Test
    public void controlsUseTheComicDelegates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            BrowserBudgetPopup popup = popup();
            assertTrue(popup.checkBox(0).getIcon().getClass().getName().contains("ComicFormControls"));
            assertTrue(popup.slider(0).getUI().getClass().getName().contains("ComicFormControls"));
            List<Component> spinners = new ArrayList<Component>();
            collect(popup, javax.swing.JSpinner.class, spinners);
            assertEquals(2, spinners.size());
            for (Component spinner : spinners) {
                assertTrue(((javax.swing.JSpinner) spinner).getUI().getClass().getName()
                        .contains("ComicFormControls"));
            }
            BufferedImage image = render(popup);
            assertTrue("the selected check box shows the yellow accent",
                    contains(image, ComicPalette.defaultPalette().getAccentYellow()));
        });
    }

    private static BrowserBudgetPopup popup() {
        List<BrowserBudgetPopup.Row> rows = new ArrayList<BrowserBudgetPopup.Row>();
        rows.add(new BrowserBudgetPopup.Row(BrowserBudgetPopup.TITLE, 20, 4, 60, 4, 60, null));
        rows.add(new BrowserBudgetPopup.Row(BrowserBudgetPopup.TABS_TITLE, 4, 1, 8, 1, 8, null));
        List<BrowserBudgetPopup.Check> checks = new ArrayList<BrowserBudgetPopup.Check>();
        checks.add(new BrowserBudgetPopup.Check(BrowserBudgetPopup.HUMAN_TITLE, "", true, null));
        checks.add(new BrowserBudgetPopup.Check(BrowserBudgetPopup.MOUSE_TITLE, "", false, null));
        return new BrowserBudgetPopup(ComicPalette.defaultPalette(), rows, checks);
    }

    private static BufferedImage render(BrowserBudgetPopup popup) {
        Dimension size = popup.getPreferredSize();
        popup.setSize(size);
        layout(popup);
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(BACKDROP);
        g.fillRect(0, 0, size.width, size.height);
        popup.paint(g);
        g.dispose();
        return image;
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) {
            if (child instanceof Container) {
                layout((Container) child);
            }
        }
    }

    private static void collect(Container container, Class<?> type, List<Component> found) {
        for (Component child : container.getComponents()) {
            if (type.isInstance(child)) {
                found.add(child);
            } else if (child instanceof Container) {
                collect((Container) child, type, found);
            }
        }
    }

    private static boolean contains(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0xFFFFFF) == (color.getRGB() & 0xFFFFFF)) {
                    return true;
                }
            }
        }
        return false;
    }
}

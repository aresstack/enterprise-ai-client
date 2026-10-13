package com.aresstack.enterpriseai.proasteion.exedra.ui.icon;

import org.junit.Test;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

public class ApplicationIconsTest {

    @Test
    public void loadsEverySizeWithItsEdgeLength() {
        List<Image> images = ApplicationIcons.load();
        assertEquals(ApplicationIcons.sizes().length, images.size());
        for (int i = 0; i < images.size(); i++) {
            assertEquals(ApplicationIcons.sizes()[i], images.get(i).getWidth(null));
            assertEquals(ApplicationIcons.sizes()[i], images.get(i).getHeight(null));
        }
    }

    @Test
    public void tileFillsTheIconAndCornersStayTransparent() throws Exception {
        for (int size : ApplicationIcons.sizes()) {
            BufferedImage image = read(ApplicationIcons.resource(size));
            assertTrue("transparent corner at " + size, alpha(image, 0, 0) < 16);
            int middle = size / 2;
            int inset = Math.max(1, size / 32);
            assertTrue("opaque top edge at " + size, alpha(image, middle, inset) >= 240);
            assertTrue("opaque left edge at " + size, alpha(image, inset, middle) >= 240);
            assertEquals("opaque centre at " + size, 255, alpha(image, middle, middle));
        }
    }

    @Test
    public void sourceAndWindowsIconAreBundled() throws Exception {
        assertNotNull(read(ApplicationIcons.RESOURCE_DIRECTORY + "app-icon-source.png"));
        try (InputStream in = ApplicationIcons.class.getResourceAsStream(
                ApplicationIcons.RESOURCE_DIRECTORY + "app-icon.ico")) {
            assertNotNull(in);
            byte[] header = new byte[6];
            assertEquals(6, in.read(header));
            assertEquals("ICO type", 1, header[2]);
            assertEquals("ICO image count", ApplicationIcons.sizes().length, header[4] & 0xff);
        }
    }

    @Test
    public void frameCarriesTheIconListBeforeItIsShown() {
        assumeFalse("braucht ein Display", GraphicsEnvironment.isHeadless());
        JFrame frame = new JFrame();
        try {
            ApplicationIcons.apply(frame);
            assertEquals(ApplicationIcons.sizes().length, frame.getIconImages().size());
        } finally {
            frame.dispose();
        }
    }

    private static BufferedImage read(String resource) throws Exception {
        try (InputStream in = ApplicationIcons.class.getResourceAsStream(resource)) {
            assertNotNull(resource, in);
            return ImageIO.read(in);
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }
}

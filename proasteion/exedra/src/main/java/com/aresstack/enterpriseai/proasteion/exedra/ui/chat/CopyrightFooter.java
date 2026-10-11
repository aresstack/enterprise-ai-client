package com.aresstack.enterpriseai.proasteion.exedra.ui.chat;

import com.aresstack.enterpriseai.proasteion.exedra.ui.markdown.DesktopLinkOpener;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ResearchUiTypography;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Properties;

/**
 * Kleine Fußzeile rechts unter dem Composer, bündig mit dessen rechtem Rand: „© 2026 AresStack“, ganz grau; der Name
 * wird erst beim Hovern zum blauen Link und öffnet die Release-Seite. Die Adresse steht in {@code copyright-footer.properties} neben dieser Klasse.
 */
public final class CopyrightFooter extends JPanel {

    static final String RESOURCE = "copyright-footer.properties";
    /** Abstand zum rechten (und linken) Rand der Ansicht; gleich dem seitlichen Rand des Composers. */
    public static final int SIDE_MARGIN = 12;
    /** Abstand zum unteren Rand der Ansicht. */
    public static final int BOTTOM_MARGIN = 6;
    /** Luft über der Zeile (zum Composer). */
    public static final int TOP_GAP = 3;
    private static final float FONT_SIZE = 11f;

    private final JLabel link;

    public CopyrightFooter() {
        this(releasesUrl(), DesktopLinkOpener.systemDefault());
    }

    CopyrightFooter(final String url, final DesktopLinkOpener opener) {
        super(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        setOpaque(false);
        Font font = ResearchUiTypography.regular(FONT_SIZE);
        JLabel copyright = new JLabel("© 2026 ");
        copyright.setFont(font);
        copyright.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        link = new JLabel("AresStack");
        link.setFont(font);
        link.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
        if (url != null && !url.isEmpty()) {
            final Font underlined = font.deriveFont(
                    Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON));
            final Font plain = font;
            link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            link.setToolTipText(url);
            link.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    opener.open(url);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    link.setFont(underlined);
                    link.setForeground(ResearchUiPalette.ACCENT_BLUE);
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    link.setFont(plain);
                    link.setForeground(ResearchUiPalette.LIGHT_TEXT_MUTED);
                }
            });
        }
        add(copyright);
        add(link);
        setBorder(BorderFactory.createEmptyBorder(TOP_GAP, 0, 0, 0));
    }

    /**
     * Die Fußzeile mit den Rändern der Chat-Ansicht, für Ansichten ohne Composer (Lesebereich): unten rechts in
     * {@link BorderLayout#SOUTH} eingehängt steht sie pixelgenau dort, wo sie im Chat steht, solange beide Ansichten
     * Karten desselben Decks sind.
     */
    public static JPanel placedLikeChat() {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.setBorder(BorderFactory.createEmptyBorder(0, SIDE_MARGIN, BOTTOM_MARGIN, SIDE_MARGIN));
        wrap.add(new CopyrightFooter(), BorderLayout.CENTER);
        return wrap;
    }

    JLabel link() {
        return link;
    }

    /** Release-Seite aus der Ressource; leer, wenn sie fehlt (dann ist der Name nur Text). */
    static String releasesUrl() {
        Properties properties = new Properties();
        InputStream in = CopyrightFooter.class.getResourceAsStream(RESOURCE);
        if (in == null) {
            return "";
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            return "";
        }
        return properties.getProperty("releases.url", "").trim();
    }
}

package com.aresstack.enterpriseai.app.ui.agent;

import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Rechts in der Reiterleiste ist Platz für Bedienelemente der Anwendung (Knopf „Einstellungen“). */
public class ModeSwitchBarTest {

    @Test
    public void trailingComponentsSitRightOfTheModeButtons() throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                ModeSwitchBar bar = new ModeSwitchBar(new ShellModeModel(false), ComicPalette.defaultPalette());
                assertEquals(0, bar.trailingComponents().getComponentCount());
                JLabel settings = new JLabel("Einstellungen");
                bar.addTrailing(settings);
                assertEquals(1, bar.trailingComponents().getComponentCount());
                assertSame(settings, bar.trailingComponents().getComponent(0));
                assertTrue(bar.button(ShellMode.CHAT).isSelected());
                assertTrue("Modus-Knöpfe bleiben in der Leiste", bar.isAncestorOf(bar.button(ShellMode.AGENT)));
                assertTrue(bar.isAncestorOf(settings));
            }
        });
    }
}

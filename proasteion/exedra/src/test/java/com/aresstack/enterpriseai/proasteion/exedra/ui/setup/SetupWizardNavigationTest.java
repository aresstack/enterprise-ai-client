package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Freie Navigation im Einrichtungsassistenten: Ein Klick in der Schrittleiste springt ohne Prüfung zu jedem Schritt,
 * „Überspringen“ geht in jedem Schritt zum nächsten; Eingaben bleiben stehen, und der Erfüllungsstatus bleibt echt
 * (übersprungene Schritte gelten nicht als erledigt, „Weiter“ prüft weiterhin).
 */
public class SetupWizardNavigationTest {

    private static final int STEPS = SetupWizardPanel.STEP_SPEECH + 1;

    private SetupWizardRun openRun() {
        return openRun(new WizardFakes.Settings());
    }

    private SetupWizardRun openRun(WizardFakes.Settings settings) {
        SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings,
                new WizardFakes.Setup(), ComicPalette.defaultPalette(), completed -> {
                });
        run.overlay().setSize(new Dimension(SetupWizardWindow.WIDTH, SetupWizardWindow.HEIGHT));
        layoutTree(run.overlay());
        return run;
    }

    private static void layoutTree(Component component) {
        if (component instanceof Container) {
            Container container = (Container) component;
            container.doLayout();
            for (Component child : container.getComponents()) {
                layoutTree(child);
            }
        }
    }

    private static void click(SetupStepTrail trail, int index) {
        int x = trail.centerOf(index);
        trail.dispatchEvent(new MouseEvent(trail, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(),
                InputEvent.BUTTON1_DOWN_MASK, x, 10, 1, false, MouseEvent.BUTTON1));
    }

    @Test
    public void clickingAStepJumpsThereWithoutCheckAndKeepsInput() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            // KeePass nicht erreichbar: dann ist wirklich nichts erledigt (gelesene Einträge erledigen „KeePass“).
            WizardFakes.Settings unreachable = new WizardFakes.Settings();
            unreachable.listFailure = "KeePass antwortet nicht";
            SetupWizardPanel wizard = openRun(unreachable).wizard();
            wizard.keePassHostField().setText("keepass.example");
            assertFalse("nichts erledigt", wizard.isDone(SetupWizardPanel.STEP_KEEPASS));
            click(wizard.trail(), SetupWizardPanel.STEP_CONFLUENCE);
            assertEquals(SetupWizardPanel.STEP_CONFLUENCE, wizard.step());
            for (int step = SetupWizardPanel.STEP_KEEPASS; step < SetupWizardPanel.STEP_CONFLUENCE; step++) {
                assertFalse("übersprungen ist nicht erledigt: " + step, wizard.isDone(step));
            }
            assertFalse("„Weiter“ prüft weiterhin", wizard.nextButton().isEnabled());
            click(wizard.trail(), SetupWizardPanel.STEP_KEEPASS);
            assertEquals(SetupWizardPanel.STEP_KEEPASS, wizard.step());
            assertEquals("Eingabe erhalten", "keepass.example", wizard.keePassHostField().getText());
            assertEquals("keepass.example", wizard.form().keePassHost());
        });
    }

    @Test
    public void skipGoesToTheNextStepInEveryStepWithoutFakingCompletion() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            for (int step = 0; step < SetupWizardPanel.STEP_SPEECH; step++) {
                assertEquals(step, wizard.step());
                assertTrue("sichtbar in Schritt " + step, wizard.skipButton().isVisible());
                assertTrue("frei in Schritt " + step, wizard.skipButton().isEnabled());
                wizard.skipButton().doClick();
            }
            assertEquals(SetupWizardPanel.STEP_SPEECH, wizard.step());
            assertFalse("Abschlussprüfung nicht bestanden", wizard.isDone(SetupWizardPanel.STEP_FINISH));
            assertTrue(wizard.skipButton().isVisible());
            assertFalse("abschließen ohne bestandene Prüfung geht nicht", wizard.skipButton().isEnabled());
            assertFalse(wizard.finishButton().isEnabled());
        });
    }

    @Test
    public void everyStepIsReachableFromTheTrail() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            for (int step = STEPS - 1; step >= 0; step--) {
                click(wizard.trail(), step);
                assertEquals(step, wizard.step());
            }
        });
    }
}

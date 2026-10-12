package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.settings.SettingsMapper;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import org.junit.Test;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Merlin im Einrichtungsassistenten: schweigt bis zum verbundenen KI-Dienst, stellt sich dann einmal vor (beim
 * späteren Aufruf als Wiedersehen), meldet sich je Schritt kurz, reagiert knapp auf Fehler, beantwortet über sein
 * Menü die festen Fragen und, sobald ein Chat-Modell feststeht, eine freie Frage. Gesprochen wird nur mit Stimme.
 */
public class SetupWizardMerlinTest {

    private final WizardFakes.Settings settings = new WizardFakes.Settings();
    private final WizardFakes.Setup setup = new WizardFakes.Setup();

    private SetupWizardRun openRun() {
        SetupWizardRun run = new SetupWizardRun(SettingsMapper.firstStartDefaults(), settings, setup,
                ComicPalette.defaultPalette(), completed -> {
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

    private static String spoken(SetupWizardPanel wizard) {
        return wizard.mascotOf(wizard.step()).spokenText();
    }

    private static JMenuItem item(JPopupMenu menu, String text) {
        for (Component c : menu.getComponents()) {
            if (c instanceof JMenuItem && text.equals(((JMenuItem) c).getText())) {
                return (JMenuItem) c;
            }
        }
        throw new AssertionError("kein Menüpunkt " + text);
    }

    /** Eine Stimme zum Mitschreiben; mit {@link #wizard} merkt sie sich auch, wer in der Sprechblase steht. */
    private static final class Voice implements ReadAloudControl {
        final List<String> spoken = new ArrayList<String>();
        final List<String> speakers = new ArrayList<String>();
        boolean available;
        int stops;
        SetupWizardPanel wizard;

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public String description() {
            return "Test";
        }

        @Override
        public boolean autoStart() {
            return false;
        }

        @Override
        public void speak(String markdown) {
            spoken.add(markdown);
            if (wizard != null) {
                speakers.add(wizard.mascotOf(wizard.step()).speaker());
            }
        }

        @Override
        public void stop() {
            stops++;
        }

        @Override
        public void addListener(Listener listener) {
        }
    }

    @Test
    public void merlinStaysSilentUntilTheServiceWorksThenIntroducesHimselfOnce() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            assertFalse(wizard.isMerlinAwake());
            assertEquals("KeePass: Merlin schweigt", "", spoken(wizard));
            wizard.pairButton().doClick();
            assertEquals("Erfolg wird gemeldet", WizardGuide.KEEPASS_OK, spoken(wizard));
            wizard.nextButton().doClick(); // KI-Dienst: Verbindungstest läuft beim Betreten (Fakes: sofort grün)
            assertTrue(wizard.isDone(SetupWizardPanel.STEP_SERVICE));
            assertTrue("nach erfolgreichem Verbindungstest wach", wizard.isMerlinAwake());
            assertEquals(WizardGuide.FIRST_GREETING + "\n\n" + WizardGuide.SERVICE_OK, spoken(wizard));
            wizard.nextButton().doClick();
            assertEquals(new WizardGuide().enteredStep(SetupWizardPanel.STEP_MODELS), spoken(wizard));
            wizard.backButton().doClick();
            assertFalse("keine zweite Vorstellung", WizardGuide.FIRST_GREETING.equals(spoken(wizard)));
        });
    }

    @Test
    public void laterCallWelcomesBackAndSpeaksOnlyWithAVoice() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            Voice voice = new Voice();
            wizard.setVoice(voice);
            wizard.welcomeBack();
            assertEquals(WizardGuide.WELCOME_BACK, spoken(wizard));
            assertTrue("ohne gewähltes TTS-Modell nur Sprechblase", voice.spoken.isEmpty());
            voice.available = true;
            wizard.skipButton().doClick();
            assertEquals(new WizardGuide().enteredStep(SetupWizardPanel.STEP_SERVICE), voice.spoken.get(0));
            wizard.stopVoice();
            assertEquals(1, voice.stops);
        });
    }

    @Test
    public void menuAnswersTheFixedQuestionsAndSkips() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            WizardGuide guide = new WizardGuide();
            JPopupMenu menu = wizard.merlinMenu();
            item(menu, "Was muss ich hier machen?").doClick();
            assertEquals(guide.whatToDo(SetupWizardPanel.STEP_KEEPASS), spoken(wizard));
            wizard.merlinMenu();
            item(wizard.merlinMenu(), "Warum brauche ich das?").doClick();
            assertEquals(guide.whyNeeded(SetupWizardPanel.STEP_KEEPASS), spoken(wizard));
            assertFalse("freie Frage erst mit Chat-Modell und verbundenem Dienst",
                    item(wizard.merlinMenu(), "Eigene Frage stellen …").isEnabled());
            item(wizard.merlinMenu(), "Diesen Schritt überspringen").doClick();
            assertEquals(SetupWizardPanel.STEP_SERVICE, wizard.step());
        });
    }

    @Test
    public void freeQuestionGoesToTheChatModelOnceItIsReady() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            wizard.welcomeBack();
            wizard.chatModelChoice().setSelectedItem("chat-model");
            assertTrue(wizard.canAskMerlin());
            item(wizard.merlinMenu(), "Eigene Frage stellen …").doClick();
            SetupWizardMascot mascot = wizard.mascotOf(wizard.step());
            assertTrue("Fragefeld offen", mascot.isAskingQuestion());
            mascot.questionField().setText("Wofür ist der Port?");
            mascot.questionField().getTextField().postActionEvent();
            assertFalse(mascot.isAskingQuestion());
            assertEquals("Wofür ist der Port?", setup.questions.get(0));
            assertTrue("Rahmen nennt Merlin und den Schritt", setup.systems.get(0).contains("Merlin")
                    && setup.systems.get(0).contains("KeePass"));
            assertEquals(setup.answer, spoken(wizard));
            setup.answer = null;
            wizard.askMerlin("Und jetzt?");
            assertEquals("Fehler: kurze feste Antwort statt technischer Meldung", WizardGuide.NO_ANSWER,
                    spoken(wizard));
        });
    }

    @Test
    public void failureGetsAShortReactionOnceMerlinIsAwake() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardPanel wizard = openRun().wizard();
            wizard.welcomeBack();
            wizard.jumpTo(SetupWizardPanel.STEP_CONFLUENCE);
            wizard.confluenceTestButton().doClick(); // ohne Adresse: Fehler im Schritt
            assertNotNull(wizard.errorOf(SetupWizardPanel.STEP_CONFLUENCE));
            assertEquals(WizardGuide.FAILURE, spoken(wizard));
        });
    }

    @Test
    public void bubbleStandsBesideMerlinInsideTheFreeSpace() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun();
            SetupWizardPanel wizard = run.wizard();
            wizard.welcomeBack();
            layoutTree(run.overlay());
            SetupWizardMascot mascot = wizard.mascotOf(SetupWizardPanel.STEP_KEEPASS);
            assertTrue(mascot.fits());
            assertTrue("Sprechblase sichtbar", mascot.bubble().isVisible());
            Rectangle bubble = mascot.bubble().getBounds();
            assertFalse("nicht über dem Zauberer", bubble.intersects(mascot.figureBounds()));
            assertTrue("ganz in der Fläche", new Rectangle(0, 0, mascot.getWidth(), mascot.getHeight())
                    .contains(bubble));
        });
    }

    @Test
    public void firstCompletionHandsOverToTheDaughterAndMerlinHasTheLastWord() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun();
            SetupWizardPanel wizard = run.wizard();
            Voice merlin = new Voice();
            Voice daughter = new Voice();
            merlin.available = true;
            daughter.available = true;
            merlin.wizard = wizard;
            daughter.wizard = wizard;
            setup.voices = new SetupVoices(merlin, daughter, "Testtochter", null);
            wizard.setHandoff(true);
            SetupWizardDriver.throughBasicSetup(wizard);
            SetupWizardDriver.skip(wizard); // fertig ohne Spracheingabe
            assertEquals(1, setup.voicesRequested);
            List<String> merlinLines = new ArrayList<String>();
            List<String> daughterLines = new ArrayList<String>();
            for (int i = 0; i < WizardGuide.handoffLines(); i++) {
                WizardGuide.Line line = WizardGuide.handoff(i);
                (line.merlin ? merlinLines : daughterLines).add(line.text);
            }
            assertEquals("Merlin mit seiner Stimme", merlinLines, merlin.spoken);
            assertEquals("die Tochter mit der Chat-Stimme", daughterLines, daughter.spoken);
            assertEquals("Testtochter", daughter.speakers.get(0));
            assertEquals(WizardGuide.NAME, merlin.speakers.get(merlin.speakers.size() - 1));
            assertTrue("erst nach der letzten Zeile geschlossen", run.isFinished() && run.isCompleted());
        });
    }

    @Test
    public void laterCompletionClosesWithoutHandoff() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun();
            SetupWizardDriver.throughBasicSetup(run.wizard());
            SetupWizardDriver.skip(run.wizard());
            assertEquals(0, setup.voicesRequested);
            assertTrue(run.isCompleted());
        });
    }

    @Test
    public void silentHandoffWaitsForReadingAndCanStillBeCancelled() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun();
            SetupWizardPanel wizard = run.wizard();
            wizard.setHandoff(true);
            SetupWizardDriver.throughBasicSetup(wizard);
            SetupWizardDriver.skip(wizard);
            assertTrue("Übergabe läuft", wizard.isHandingOff());
            assertFalse("schließt erst nach der Übergabe", run.isFinished());
            assertEquals(WizardGuide.handoff(0).text, spoken(wizard));
            assertFalse("Knöpfe gesperrt", wizard.skipButton().isEnabled() || wizard.finishButton().isEnabled()
                    || wizard.backButton().isEnabled());
            wizard.cancel();
            assertTrue(run.isFinished());
            assertFalse(run.isCompleted());
        });
    }

    @Test
    public void silentHandoffWithoutReadingPauseRunsThrough() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            SetupWizardRun run = openRun();
            SetupWizardPanel wizard = run.wizard();
            wizard.setHandoff(true);
            wizard.setReadingPace(0);
            SetupWizardDriver.throughBasicSetup(wizard);
            SetupWizardDriver.skip(wizard);
            assertTrue(run.isCompleted());
        });
    }
}

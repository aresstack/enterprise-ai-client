package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ReadAloudControl;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.function.Consumer;

/**
 * Der Einrichtungsassistent aus der laufenden Anwendung: das Overlay eines {@link SetupWizardRun} auf der
 * Modal-Ebene der {@link JLayeredPane} des Hauptfensters. Es deckt das ganze Fenster, unabhängig davon, welche
 * Karte der Arbeitsfläche gerade sichtbar ist (Chat, Agent, Lesebereich, Drawer-Reiter); vorher hing der Assistent
 * am Antwortfenster des Chats und blieb hinter dem Lesebereich unsichtbar. Abbrechen lässt die Anwendung
 * unverändert weiterlaufen.
 */
public final class SetupWizardOverlay {

    /** Name des Overlays in der Ebene (Tests, Diagnose). */
    public static final String NAME = "setupWizardOverlay";

    private SetupWizardOverlay() {
    }

    /**
     * Legt den Assistenten über das Fenster (nur auf dem EDT); ein noch offener Lauf wird ersetzt.
     *
     * @param ended genau einmal: {@code true} nach „Fertig“, {@code false} nach Abbruch; das Overlay ist dann
     *              schon entfernt
     */
    public static SetupWizardRun show(JRootPane window, SettingsForm initial, SettingsDialogActions settings,
                                      SetupActions setup, ComicPalette palette, final Consumer<Boolean> ended) {
        return show(window, initial, settings, setup, palette, null, ended);
    }

    /**
     * Wie {@link #show(JRootPane, SettingsForm, SettingsDialogActions, SetupActions, ComicPalette, Consumer)}, als
     * späterer Aufruf: Merlin begrüßt als Wiedersehen und spricht mit der Stimme der Anwendung, falls ein TTS-Modell
     * gewählt ist ({@code voice} darf {@code null} sein).
     */
    public static SetupWizardRun show(JRootPane window, SettingsForm initial, SettingsDialogActions settings,
                                      SetupActions setup, ComicPalette palette, ReadAloudControl voice,
                                      final Consumer<Boolean> ended) {
        if (window == null || ended == null) {
            throw new IllegalArgumentException("window and ended must not be null");
        }
        final JLayeredPane layers = window.getLayeredPane();
        Object open = layers.getClientProperty(NAME);
        if (open instanceof SetupWizardRun && !((SetupWizardRun) open).isFinished()) {
            ((SetupWizardRun) open).wizard().cancel(); // ein zweiter Aufruf ersetzt den offenen Lauf
        }
        final ComponentListener[] resize = new ComponentListener[1];
        final SetupWizardRun[] run = new SetupWizardRun[1];
        run[0] = new SetupWizardRun(initial, settings, setup, palette, completed -> {
            layers.removeComponentListener(resize[0]);
            layers.putClientProperty(NAME, null);
            layers.remove(run[0].overlay());
            layers.revalidate();
            layers.repaint();
            ended.accept(completed);
        });
        run[0].overlay().setName(NAME);
        resize[0] = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                fit(layers, run[0]);
            }
        };
        layers.addComponentListener(resize[0]);
        layers.putClientProperty(NAME, run[0]);
        layers.add(run[0].overlay(), JLayeredPane.MODAL_LAYER);
        fit(layers, run[0]);
        run[0].wizard().setVoice(voice);
        run[0].wizard().welcomeBack();
        run[0].wizard().requestFocusInWindow();
        return run[0];
    }

    private static void fit(JLayeredPane layers, SetupWizardRun run) {
        run.overlay().setBounds(0, 0, layers.getWidth(), layers.getHeight());
        run.overlay().revalidate();
        run.overlay().repaint();
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;

/**
 * Ein Lauf des Einrichtungsassistenten, gleich für den Erststart ({@link SetupWizardWindow}) und den Aufruf aus den
 * Einstellungen ({@link SetupWizardOverlay}): {@link SetupWizardPanel} in einem {@link ComicOverlayPanel}
 * (abgedunkelter Hintergrund, Comic-Platte, Comic-✕ = Abbrechen, Escape = Abbrechen). Schritte, Prüfungen und
 * Speichern stecken im Panel; der Gastgeber zeigt nur das Overlay und erfährt genau einmal, wie der Lauf endete
 * ({@code true} nach „Fertig“ bzw. „Überspringen“ im letzten Schritt, sonst Abbruch).
 */
public final class SetupWizardRun {

    /** Überschrift des Overlays. */
    public static final String TITLE = "Willkommen beim Enterprise AI Client: Einrichtung";
    private static final String CANCEL_ACTION = "cancelSetup";

    private final SetupWizardPanel wizard;
    private final ComicOverlayPanel overlay;
    private final Consumer<Boolean> ended;
    private boolean finished;
    private boolean completed;

    /**
     * @param ended bekommt genau einmal {@code true} (eingerichtet) oder {@code false} (abgebrochen); auf dem EDT
     */
    public SetupWizardRun(SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                          ComicPalette palette, Consumer<Boolean> ended) {
        if (ended == null) {
            throw new IllegalArgumentException("ended must not be null");
        }
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        this.ended = ended;
        this.wizard = new SetupWizardPanel(initial, settings, setup, colors);
        wizard.setOnFinished(() -> end(true));
        wizard.setOnCancel(() -> end(false));
        this.overlay = new ComicOverlayPanel(TITLE, wizard, wizard::cancel, colors);
        overlay.setPlateLimit(new Dimension(SetupWizardWindow.WIDTH, SetupWizardWindow.HEIGHT));
        AbstractAction cancel = new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                wizard.cancel();
            }
        };
        KeyStroke escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
        overlay.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escape, CANCEL_ACTION);
        overlay.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(escape, CANCEL_ACTION);
        overlay.getActionMap().put(CANCEL_ACTION, cancel);
    }

    private void end(boolean done) {
        if (finished) {
            return;
        }
        finished = true;
        completed = done;
        ended.accept(done);
    }

    public SetupWizardPanel wizard() {
        return wizard;
    }

    /** Das Overlay, das der Gastgeber über seine Fläche legt. */
    public ComicOverlayPanel overlay() {
        return overlay;
    }

    public boolean isFinished() {
        return finished;
    }

    /** {@code true}, wenn der Lauf mit „Fertig“ endete. */
    public boolean isCompleted() {
        return completed;
    }
}

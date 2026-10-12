package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JDialog;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Das modale Fenster des Einrichtungsassistenten für den Erststart, wenn es noch kein Hauptfenster gibt: rahmenlos,
 * gefüllt vom Overlay eines {@link SetupWizardRun} (derselbe Lauf wie aus den Einstellungen über
 * {@link SetupWizardOverlay}). Der abgedunkelte Rand zieht das Fenster; Escape und Schließen brechen ab.
 */
public final class SetupWizardWindow extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int WIDTH = 860;
    static final int HEIGHT = 700;

    private final SetupWizardRun run;

    private SetupWizardWindow(Window owner, SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                              ComicPalette palette) {
        super(owner, SetupWizardPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        run = new SetupWizardRun(initial, settings, setup, palette, completed -> dispose());
        ComicWindowDragger.install(run.overlay());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                run.wizard().cancel();
            }
        });
        setContentPane(run.overlay());
        setMinimumSize(new Dimension(720, 600));
        setSize(new Dimension(WIDTH, HEIGHT));
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Assistenten modal (nur auf dem EDT).
     *
     * @return {@code true} nach „Fertig“ (Abschlussprüfung bestanden, Konfiguration geschrieben), sonst {@code false}
     */
    public static boolean show(Window owner, SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                               ComicPalette palette) {
        SetupWizardWindow window = new SetupWizardWindow(owner, initial, settings, setup, palette);
        window.setVisible(true);
        return window.run.isCompleted();
    }

    public SetupWizardPanel wizard() {
        return run.wizard();
    }
}

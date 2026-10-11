package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;
import com.aresstack.enterpriseai.ui.comic.control.ComicOverlayPanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowDragger;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Das modale Fenster des Einrichtungsassistenten für den Erststart, wenn es noch kein Hauptfenster (und damit kein
 * Antwortfenster für ein Overlay) gibt: rahmenlos, gefüllt vom {@link ComicOverlayPanel} wie das Mermaid-Overlay
 * (abgedunkelter Hintergrund, Comic-Platte mit Kontur, Überschrift, Comic-✕ = Abbrechen), darin
 * {@link SetupWizardPanel}. Der abgedunkelte Rand zieht das Fenster; Escape und Schließen brechen ab.
 */
public final class SetupWizardWindow extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final String CANCEL_ACTION = "cancelSetup";

    private final SetupWizardPanel wizard;
    private boolean completed;

    private SetupWizardWindow(Window owner, SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                              ComicPalette palette) {
        super(owner, SetupWizardPanel.TITLE, ModalityType.APPLICATION_MODAL);
        setUndecorated(true);
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        wizard = new SetupWizardPanel(initial, settings, setup, palette);
        wizard.setOnFinished(() -> {
            completed = true;
            dispose();
        });
        wizard.setOnCancel(() -> {
            completed = false;
            dispose();
        });
        ComicOverlayPanel overlay = new ComicOverlayPanel("Willkommen beim Enterprise AI Client: Einrichtung",
                wizard, wizard::cancel, palette);
        overlay.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), CANCEL_ACTION);
        overlay.getActionMap().put(CANCEL_ACTION, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                wizard.cancel();
            }
        });
        ComicWindowDragger.install(overlay);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                wizard.cancel();
            }
        });
        setContentPane(overlay);
        setMinimumSize(new Dimension(720, 600));
        setSize(new Dimension(860, 700));
        setLocationRelativeTo(owner);
    }

    /**
     * Zeigt den Assistenten modal (nur auf dem EDT).
     *
     * @return {@code true} nach „Fertig“ (Abschlussprüfung bestanden, Konfiguration geschrieben), sonst {@code false}
     */
    public static boolean show(Window owner, SettingsForm initial, SettingsDialogActions settings, SetupActions setup,
                               ComicPalette palette) {
        ComicPalette colors = palette == null ? ComicPalette.defaultPalette() : palette;
        ComicTheme.installMenuDefaults(colors);
        SetupWizardWindow window = new SetupWizardWindow(owner, initial, settings, setup, colors);
        window.setVisible(true);
        return window.completed;
    }

    public SetupWizardPanel wizard() {
        return wizard;
    }
}

package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import com.aresstack.enterpriseai.ui.comic.control.ComposerButton;
import com.aresstack.enterpriseai.ui.comic.paint.ComposerIcons;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.FlowLayout;

/**
 * Die Fußzeile eines Artefakt-Reiters mit seiner Play-Aktion (unten mittig, unaufdringlich): Quellen „Recherche
 * starten“, Gliederung „Gliederung erstellen“, Dokument „Dokument schreiben“. Aktiv nur, solange die eigene Phase
 * gewählt ist und der Agent nicht arbeitet; deaktiviert nennt der Tooltip die Phase, die dafür zu wählen ist. Der
 * Knopf meldet nur den Klick, die Phase ändert er nie. Nur auf dem EDT.
 */
public final class ResearchPlayBar extends JPanel {

    private final ComposerButton button;
    private Runnable action;

    public ResearchPlayBar(String name, String tooltip) {
        super(new FlowLayout(FlowLayout.CENTER, 0, 4));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        button = ComposerButton.iconButton(ComposerIcons.play(), tooltip);
        button.setName(name);
        button.getAccessibleContext().setAccessibleName(tooltip);
        button.addActionListener(event -> {
            if (action != null && button.isEnabled()) {
                action.run();
            }
        });
        add(button);
        render(false, tooltip);
    }

    /** Was ein Klick auslöst. */
    public void setAction(Runnable action) {
        this.action = action;
    }

    /** Aktiv oder nicht, mit dem Tooltip des Zustands. */
    public void render(boolean enabled, String tooltip) {
        button.setEnabled(enabled);
        button.setToolTipText(tooltip);
        button.getAccessibleContext().setAccessibleDescription(tooltip);
    }

    /** Der Knopf (Tests, Tastatur). */
    public ComposerButton button() {
        return button;
    }
}

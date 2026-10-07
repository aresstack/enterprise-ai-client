package com.aresstack.enterpriseai.app.ui.agent;

import com.aresstack.enterpriseai.ui.comic.control.ComicToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import java.awt.FlowLayout;
import java.util.EnumMap;
import java.util.Map;

/**
 * Die Reiterleiste {@code [ Chat ] [ Agent ]} aus Comic-Umschaltern (derselbe {@link ComicToggleButton} wie der
 * RAG-Schalter). Genau einer ist gewählt; ohne konfigurierten Agenten ist "Agent" deaktiviert.
 */
public final class ModeSwitchBar extends JPanel implements ShellModeModel.Listener {

    private final ShellModeModel model;
    private final Map<ShellMode, ComicToggleButton> buttons = new EnumMap<ShellMode, ComicToggleButton>(ShellMode.class);

    public ModeSwitchBar(ShellModeModel model, ComicPalette palette) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        if (model == null || palette == null) {
            throw new IllegalArgumentException("model and palette must not be null");
        }
        this.model = model;
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        ButtonGroup group = new ButtonGroup();
        for (final ShellMode mode : ShellMode.values()) {
            ComicToggleButton button = new ComicToggleButton(mode.label(), palette);
            button.getAccessibleContext().setAccessibleName("Modus " + mode.label());
            button.addActionListener(event -> {
                if (!model.select(mode)) {
                    modeChanged(model.getMode()); // abgelehnt: Auswahl zurücksetzen
                }
            });
            group.add(button);
            buttons.put(mode, button);
            add(button);
        }
        ComicToggleButton agent = buttons.get(ShellMode.AGENT);
        agent.setEnabled(model.isAgentAvailable());
        agent.setToolTipText(model.isAgentAvailable()
                ? "Aufträge an den externen Agenten (ACP) schicken"
                : "Kein Agent konfiguriert");
        buttons.get(ShellMode.CHAT).setToolTipText("Normaler Chat mit dem KI-Modell");
        model.addListener(this);
        modeChanged(model.getMode());
    }

    @Override
    public void modeChanged(ShellMode mode) {
        ComicToggleButton selected = buttons.get(mode);
        if (!selected.isSelected()) {
            selected.setSelected(true);
        }
    }

    ComicToggleButton button(ShellMode mode) {
        return buttons.get(mode);
    }
}

package com.aresstack.enterpriseai.app.ui.agent;

import com.aresstack.enterpriseai.ui.comic.control.ComicToggleButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.EnumMap;
import java.util.Map;

/**
 * Die Reiterleiste {@code [ Chat ] [ Agent ]} aus Comic-Umschaltern (derselbe {@link ComicToggleButton} wie der
 * RAG-Schalter). Genau einer ist gewählt; ohne konfigurierten Agenten ist "Agent" deaktiviert. Rechts ist Platz
 * für Bedienelemente der Anwendung, die zu keinem Modus gehören ({@link #addTrailing}, z. B. „Einstellungen“);
 * die Leiste selbst bleibt reine Oberfläche.
 */
public final class ModeSwitchBar extends JPanel implements ShellModeModel.Listener {

    private final ShellModeModel model;
    private final Map<ShellMode, ComicToggleButton> buttons = new EnumMap<ShellMode, ComicToggleButton>(ShellMode.class);
    private final JPanel leading = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel trailing = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));

    public ModeSwitchBar(ShellModeModel model, ComicPalette palette) {
        super(new BorderLayout());
        if (model == null || palette == null) {
            throw new IllegalArgumentException("model and palette must not be null");
        }
        this.model = model;
        setBackground(palette.getSurface());
        setBorder(BorderFactory.createEmptyBorder(8, 10, 6, 10));
        leading.setOpaque(false);
        trailing.setOpaque(false);
        add(leading, BorderLayout.CENTER);
        add(trailing, BorderLayout.EAST);
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
            leading.add(button);
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

    /** Hängt ein Bedienelement rechts an die Leiste (z. B. den Knopf „Einstellungen“). */
    public void addTrailing(JComponent component) {
        if (component == null) {
            throw new IllegalArgumentException("component must not be null");
        }
        trailing.add(component);
        trailing.revalidate();
        trailing.repaint();
    }

    /** Die rechts angehängten Bedienelemente (für Tests). */
    public JComponent trailingComponents() {
        return trailing;
    }

    ComicToggleButton button(ShellMode mode) {
        return buttons.get(mode);
    }
}

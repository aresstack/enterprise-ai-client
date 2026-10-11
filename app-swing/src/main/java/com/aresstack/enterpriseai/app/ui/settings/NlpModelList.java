package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.control.ComicButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Die OpenNLP-Modelle des Vorlesens wie askai arch {@code NlpModelsPanel}: je Modell Name, Stand und „Installieren“;
 * installierte Zeilen zeigen nur ihren Stand. Installiert wird im Hintergrund, ein Fehler steht in der Zeile.
 */
final class NlpModelList {

    static final String INSTALL_LABEL = "Installieren";
    static final String INSTALLING = "Lade …";
    static final String NOT_INSTALLED = "Nicht installiert";
    static final String INSTALLED = "✓ installiert";

    private final SettingsDialogActions actions;
    private final Supplier<SettingsForm> form;
    private final ComicPalette palette;
    private final JPanel lines = new JPanel();

    NlpModelList(SettingsDialogActions actions, Supplier<SettingsForm> form, ComicPalette palette) {
        this.actions = actions;
        this.form = form;
        this.palette = palette;
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        reload();
    }

    JPanel panel() {
        return lines;
    }

    private void reload() {
        actions.nlpModels(new Consumer<List<NlpModelOffer>>() {
            @Override
            public void accept(List<NlpModelOffer> offers) {
                lines.removeAll();
                for (NlpModelOffer offer : offers) {
                    lines.add(line(offer));
                }
                lines.revalidate();
                lines.repaint();
            }
        });
    }

    private JComponent line(final NlpModelOffer offer) {
        JLabel name = new WrappingLabel(offer.name());
        name.putClientProperty("html.disable", Boolean.TRUE);
        name.setForeground(palette.getInk());
        final JLabel state = new JLabel(offer.isInstalled() ? INSTALLED : NOT_INSTALLED);
        state.setForeground(offer.isInstalled() ? palette.getAgentPetrol() : FormRows.MUTED);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        right.add(state);
        if (!offer.isInstalled()) {
            final ComicButton install = new ComicButton(INSTALL_LABEL, null, ComicButton.Accent.ACTION, palette);
            install.setToolTipText("Lädt das Modell über die Netzwerkeinstellungen; Größe und SHA-256 werden geprüft");
            install.getAccessibleContext().setAccessibleName(INSTALL_LABEL + ": " + offer.name());
            install.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    install.setEnabled(false);
                    state.setText(INSTALLING);
                    state.setForeground(FormRows.MUTED);
                    actions.installNlpModel(form.get(), offer.id(), new BiConsumer<Boolean, String>() {
                        @Override
                        public void accept(Boolean ok, String message) {
                            if (Boolean.TRUE.equals(ok)) {
                                reload();
                                return;
                            }
                            install.setEnabled(true);
                            state.setText(message);
                            state.setForeground(palette.getAccentOrange());
                        }
                    });
                }
            });
            right.add(install);
        }
        JPanel line = new JPanel(new BorderLayout(8, 0));
        line.setOpaque(false);
        line.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
        line.add(name, BorderLayout.CENTER);
        line.add(right, BorderLayout.EAST);
        line.setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return line;
    }
}

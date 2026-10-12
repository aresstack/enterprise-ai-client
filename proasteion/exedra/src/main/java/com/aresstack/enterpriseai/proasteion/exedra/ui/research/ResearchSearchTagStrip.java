package com.aresstack.enterpriseai.proasteion.exedra.ui.research;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Die gelben Suchvorschläge des Agenten über dem Composer (askai arch {@code ScopingComposerAccessory}): ein Klick
 * recherchiert genau diese Anfrage direkt, danach Haken „schon gesucht“ oder Kreispfeil zum Wiederholen. Die Leiste
 * gehört nicht zur Phasensteuerung: keine Phasenanzeige, keine Aktions-Tags, ein Klick ändert keine Phase und startet
 * keine autonome Recherche. Ohne Vorschläge ist sie unsichtbar. Nur auf dem EDT.
 */
public final class ResearchSearchTagStrip extends JPanel {

    /** Ein gelber Suchvorschlag; {@code status} ist OFFERED, RUNNING, DONE oder FAILED. */
    public static final class SuggestionItem {
        final String query;
        final String purpose;
        final String status;

        public SuggestionItem(String query, String purpose, String status) {
            this.query = query == null ? "" : query;
            this.purpose = purpose == null ? "" : purpose;
            this.status = status == null ? "OFFERED" : status;
        }

        /**
         * Die Beschriftung des Tags: die Anfrage, mit Vermerk, wenn sie schon gesucht ist oder gerade läuft. Eine
         * „laufende“ Suche, während der Chat nicht mehr arbeitet (Turn beendet, Agentensitzung weg), ist
         * unterbrochen und wird wie ein Fehler wiederholbar.
         */
        String label(boolean busy) {
            if ("DONE".equals(status)) {
                return query + " (schon gesucht)";
            }
            if ("RUNNING".equals(status)) {
                return query + (busy ? " (sucht …)" : " (unterbrochen)");
            }
            return query;
        }
    }

    /** Was die Leiste zeigt. */
    public static final class Snapshot {
        final List<SuggestionItem> suggestions;
        final boolean busy;

        public Snapshot(List<SuggestionItem> suggestions, boolean busy) {
            this.suggestions = suggestions == null ? Collections.<SuggestionItem>emptyList() : suggestions;
            this.busy = busy;
        }
    }

    private final JPanel suggestionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
    private Consumer<String> searchHandler;
    private Snapshot snapshot = new Snapshot(null, false);

    public ResearchSearchTagStrip() {
        super(new BorderLayout());
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        suggestionRow.setOpaque(false);
        add(suggestionRow, BorderLayout.CENTER);
        setVisible(false);
    }

    /** Wer einen gelben Suchvorschlag ausführt (die Anfrage). */
    public void setSearchHandler(Consumer<String> handler) {
        this.searchHandler = handler;
    }

    public void render(Snapshot next) {
        snapshot = next == null ? new Snapshot(null, false) : next;
        renderSuggestions();
        setVisible(!snapshot.suggestions.isEmpty());
        revalidate();
        repaint();
    }

    private void renderSuggestions() {
        suggestionRow.removeAll();
        for (final SuggestionItem suggestion : snapshot.suggestions) {
            ResearchTagButton button = new ResearchTagButton(suggestion.label(snapshot.busy));
            button.setName("research-search-" + suggestion.query);
            boolean done = "DONE".equals(suggestion.status);
            boolean failed = "FAILED".equals(suggestion.status);
            boolean running = "RUNNING".equals(suggestion.status);
            // läuft, aber der Chat arbeitet nicht mehr: die Suche kam nie zu Ende, also wiederholbar
            boolean stalled = running && !snapshot.busy;
            if (done || failed || stalled) {
                javax.swing.Icon icon = done ? new SearchTagIcons.Check() : new SearchTagIcons.Retry();
                button.setIcon(icon);
                button.setDisabledIcon(icon);
            }
            String purpose = suggestion.purpose.isEmpty() ? "" : suggestion.purpose + " - ";
            button.setToolTipText(done ? purpose + "Schon gesucht; die Seiten stehen im Reiter „Quellen“."
                    : failed ? purpose + "Die Suche ist fehlgeschlagen. Klicken, um sie zu wiederholen."
                    : stalled ? purpose + "Die Suche wurde nicht abgeschlossen. Klicken, um sie zu wiederholen."
                    : running ? purpose + "Die Suche läuft." : purpose + "In den Wissensquellen suchen");
            button.setEnabled(!done && !snapshot.busy);
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (searchHandler != null) {
                        searchHandler.accept(suggestion.query);
                    }
                }
            });
            suggestionRow.add(button);
        }
    }

    /** Die Beschriftungen der gelben Suchvorschläge (Tests, Barrierefreiheit). */
    public List<String> suggestionLabels() {
        List<String> labels = new ArrayList<String>();
        for (SuggestionItem suggestion : snapshot.suggestions) {
            labels.add(suggestion.label(snapshot.busy));
        }
        return labels;
    }

    /** Der Tag eines Suchvorschlags (Tests): klickbar, Symbol. */
    public javax.swing.JButton suggestionButton(String query) {
        for (java.awt.Component component : suggestionRow.getComponents()) {
            if (("research-search-" + query).equals(component.getName())) {
                return (javax.swing.JButton) component;
            }
        }
        return null;
    }

}

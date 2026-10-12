package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateIds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Die Benutzerführung des Research-Workflows in drei getrennten Ebenen (wie askai arch, ohne Wizard):
 * <ol>
 *   <li><b>Phase</b> — allein vom Nutzer gewählt ({@link #selectPhase}, Dropdown im Composer), in beliebiger
 *       Reihenfolge; sie bestimmt, welche MCP-Werkzeuge der Agent angeboten bekommt. Weder Agent noch Aktion noch
 *       Reiter wechseln sie.</li>
 *   <li><b>Artefakt-Reiter</b> Konzept, Quellen, Gliederung, Dokument — reine Ansicht und Bearbeitung, unabhängig
 *       von der Phase.</li>
 *   <li><b>Play-Aktion</b> eines Reiters ({@link #play}, {@link #perform}) — ein ausdrücklicher autonomer
 *       Arbeitsauftrag innerhalb der aktiven Phase: Quellen recherchiert, Gliederung erstellt das Inhaltsverzeichnis,
 *       Dokument schreibt die Kapitel. Sie ist nur in ihrer eigenen Phase aktiv und ändert die Phase nicht.</li>
 * </ol>
 * Abgeleitet wird alles aus {@link ResearchAgentHost#phaseId()} (keine zweite Zustandsverwaltung).
 */
public final class ResearchWorkflow {

    /** Phasen-Kennungen für die Oberfläche (technische IDs aus askai arch). */
    public static final String PHASE_CONCEPT = ResearchStateIds.SCOPING;
    public static final String PHASE_SOURCES = ResearchStateIds.RESEARCH;
    public static final String PHASE_OUTLINE = ResearchStateIds.OUTLINE;
    public static final String PHASE_DOCUMENT = ResearchStateIds.DRAFT;

    /** Der Auftrag an den Agenten hinter einer Play-Aktion; der Agent richtet sich nach Phase und Zustand. */
    static final String RESEARCH_PROMPT = "Recherche starten";
    static final String OUTLINE_PROMPT = "Gliederung erstellen";
    static final String DOCUMENT_PROMPT = "Dokument schreiben";
    /** Der Auftrag eines gelben Suchvorschlags an den Agenten: die Direktrecherche genau dieser Anfrage. */
    public static final String SEARCH_TAG_PREFIX = "Wissenssuche:";

    /** Ob ein Text der Auftrag eines gelben Suchvorschlags ist. */
    public static boolean isSearchTagPrompt(String text) {
        return text != null && text.trim().startsWith(SEARCH_TAG_PREFIX);
    }

    /** Die gelben Suchvorschläge mit ihrem Stand (nur in der Phase Konzept). */
    public List<ResearchAgentHost.SearchTag> searchTags() {
        return host.searchTags();
    }

    /**
     * Ein Klick auf einen gelben Vorschlag (askai arch: ein Klick IST die Suche): kein Phasenwechsel, der Agent
     * recherchiert genau diese Anfrage direkt (Recherche-Karte) und meldet danach Haken oder Retry.
     */
    public Result search(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return Result.rejected("Leere Suche.");
        }
        // Ein „laufender“ Vorschlag darf erneut starten: die Oberfläche bietet ihn nur an, wenn der Chat nicht mehr
        // arbeitet, die Suche also nie zu Ende kam (Turn abgebrochen, Agentensitzung beendet).
        host.searchTagStarted(trimmed);
        return Result.prompt(SEARCH_TAG_PREFIX + " " + trimmed, "Suche: " + trimmed);
    }

    /** Phasen in Produktreihenfolge (technische IDs aus askai arch). */
    static List<String> phaseOrder() {
        return Arrays.asList(ResearchStateIds.SCOPING, ResearchStateIds.RESEARCH, ResearchStateIds.OUTLINE,
                ResearchStateIds.DRAFT);
    }

    /** Eine wählbare Phase für das Dropdown. */
    public static final class PhaseChoice {
        private final String id;
        private final String title;

        PhaseChoice(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }
    }

    /** Die Play-Aktion eines Artefakt-Reiters. */
    public static final class Play {
        private final String phaseId;
        private final String tooltip;
        private final boolean enabled;

        Play(String phaseId, String tooltip, boolean enabled) {
            this.phaseId = phaseId;
            this.tooltip = tooltip;
            this.enabled = enabled;
        }

        /** Die Phase, in der die Aktion arbeitet (und nur dort aktiv ist). */
        public String phaseId() {
            return phaseId;
        }

        /** Was die Aktion tut, oder (deaktiviert) welche Phase dafür zu wählen ist. */
        public String tooltip() {
            return tooltip;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    /** Ergebnis einer Aktion: abgelehnt (Grund) oder ein Auftrag an den Agenten. */
    public static final class Result {
        private final String rejection;
        private final String prompt;
        private final String visibleText;

        private Result(String rejection, String prompt, String visibleText) {
            this.rejection = rejection;
            this.prompt = prompt;
            this.visibleText = visibleText;
        }

        static Result rejected(String reason) {
            return new Result(reason, null, null);
        }

        static Result prompt(String prompt, String visibleText) {
            return new Result(null, prompt, visibleText);
        }

        /** Grund, warum nichts geschah; {@code null} bei Erfolg. */
        public String rejection() {
            return rejection;
        }

        /** Der Auftrag an den Agenten; {@code null}, wenn keiner nötig ist. */
        public String prompt() {
            return prompt;
        }

        /** Was im Chat als Nachricht des Nutzers erscheint (der Name der Aktion). */
        public String visibleText() {
            return visibleText;
        }
    }

    private final ResearchAgentHost host;

    public ResearchWorkflow(ResearchAgentHost host) {
        if (host == null) {
            throw new IllegalArgumentException("host must not be null");
        }
        this.host = host;
    }

    public ResearchAgentHost host() {
        return host;
    }

    /** Die vier Phasen des Dropdowns in Produktreihenfolge; jede ist jederzeit wählbar. */
    public List<PhaseChoice> phaseChoices() {
        List<PhaseChoice> choices = new ArrayList<PhaseChoice>();
        for (String id : phaseOrder()) {
            choices.add(new PhaseChoice(id, titleOf(id)));
        }
        return choices;
    }

    /** Die aktive Phase (vom Nutzer gewählt). */
    public String currentPhaseId() {
        return host.phaseId();
    }

    /**
     * Der Nutzer wählt eine Phase. Nur hier wechselt die Phase; keine Aktion, kein Reiter und kein Agent tut es.
     *
     * @return {@code null} bei Erfolg, sonst der Grund
     */
    public String selectPhase(String phaseId) {
        return host.selectPhase(phaseId);
    }

    /** Die Play-Aktion des Reiters einer Phase (Quellen, Gliederung, Dokument); {@code null} für das Konzept. */
    public Play play(String phaseId) {
        String action = actionNameOf(phaseId);
        if (action == null) {
            return null;
        }
        boolean active = phaseId.equals(host.phaseId());
        return new Play(phaseId, active ? action : selectHint(phaseId), active);
    }

    /**
     * Führt die Play-Aktion einer Phase aus: nur, wenn genau diese Phase aktiv ist; die Phase bleibt. Was der Agent
     * danach tun soll, steht im Ergebnis.
     */
    public Result perform(String phaseId) {
        String action = actionNameOf(phaseId);
        if (action == null) {
            return Result.rejected("Unbekannte Aktion: " + phaseId);
        }
        if (!phaseId.equals(host.phaseId())) {
            return Result.rejected(selectHint(phaseId));
        }
        if (ResearchStateIds.RESEARCH.equals(phaseId)) {
            String rejection = host.startAutonomousResearch();
            return rejection != null ? Result.rejected(rejection) : Result.prompt(RESEARCH_PROMPT, action);
        }
        String rejection = host.preparePhaseAction(phaseId);
        if (rejection != null) {
            return Result.rejected(rejection);
        }
        return Result.prompt(ResearchStateIds.OUTLINE.equals(phaseId) ? OUTLINE_PROMPT : DOCUMENT_PROMPT, action);
    }

    /** Der Name der Play-Aktion (zugleich ihr Tooltip); {@code null} ohne Aktion. */
    static String actionNameOf(String phaseId) {
        if (ResearchStateIds.RESEARCH.equals(phaseId)) {
            return "Recherche starten";
        } else if (ResearchStateIds.OUTLINE.equals(phaseId)) {
            return "Gliederung erstellen";
        } else if (ResearchStateIds.DRAFT.equals(phaseId)) {
            return "Dokument schreiben";
        }
        return null;
    }

    /** Der Hinweis einer deaktivierten Play-Aktion: welche Phase zu wählen ist. */
    static String selectHint(String phaseId) {
        String goal = ResearchStateIds.RESEARCH.equals(phaseId) ? "die Recherche zu starten"
                : ResearchStateIds.OUTLINE.equals(phaseId) ? "die Gliederung zu erstellen" : "das Dokument zu schreiben";
        return "Phase ‚" + titleOf(phaseId) + "‘ auswählen, um " + goal + ".";
    }

    /** Ob die Gliederung im Reiter bearbeitet werden darf (Phase Gliederung). */
    public boolean isOutlineEditable() {
        return ResearchStateIds.OUTLINE.equals(host.phaseId());
    }

    /** Ob die Fortschrittszeile des Quellenlaufs gezeigt wird (sobald ein Lauf begonnen hat). */
    public boolean showsRunProgress() {
        return host.runStatus().hasRun();
    }

    /** Ob das Dokument abgeschlossen ist (vorgelegt und nicht mehr in Arbeit). */
    public boolean isDocumentFinal() {
        return ResearchStateIds.DRAFT.equals(host.phaseId())
                && (ResearchStateIds.COMPLETED.equals(host.stateId())
                || ResearchStateIds.WAITING_APPROVAL.equals(host.stateId()));
    }

    /**
     * Die Statuszeile des Reiters „Dokument“, solange nur ein Zwischenstand vorliegt (abgebrochener Schreiblauf);
     * sonst leer.
     */
    public String documentDraftLine() {
        if (host.hasDocument()) {
            return "";
        }
        int saved = host.documentDraftSectionCount();
        if (saved == 0) {
            return "";
        }
        int total = Math.max(saved, host.outlineChapterCount());
        String line = "Zwischenstand: " + saved + " von " + total + " Kapiteln gespeichert.";
        return saved < total ? line + " Mit „Dokument schreiben“ geht es ab Kapitel " + (saved + 1) + " weiter."
                : line;
    }

    /** Titel einer Phase in der Oberfläche. */
    public static String titleOf(String phaseId) {
        if (ResearchStateIds.SCOPING.equals(phaseId)) {
            return "Konzept";
        } else if (ResearchStateIds.RESEARCH.equals(phaseId)) {
            return "Quellen";
        } else if (ResearchStateIds.OUTLINE.equals(phaseId)) {
            return "Gliederung";
        } else if (ResearchStateIds.DRAFT.equals(phaseId)) {
            return "Dokument";
        }
        return phaseId;
    }
}

package com.aresstack.enterpriseai.proasteion.application.agent;

import com.aresstack.enterpriseai.research.domain.state.ResearchCommandType;
import com.aresstack.enterpriseai.research.domain.state.oo.ResearchStateIds;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Die Benutzerführung des Research-Lifecycles, abgeleitet allein aus {@link ResearchAgentHost#phaseId()} und
 * {@link ResearchAgentHost#stateId()} (keine zweite Zustandsverwaltung): die vier Produktphasen Konzept → Quellen →
 * Gliederung → Dokument mit ihrem Zustand und die Aktionen, die im aktuellen Zustand möglich sind (askai arch: rote
 * Aktions-Tags aus dem Live-Zustand, {@code ResearchActionTag}). Eine Aktion löst nur die vorhandenen Übergänge der
 * Zustandsmaschine aus und nennt den Auftrag, den die Oberfläche danach an den Agenten schickt.
 */
public final class ResearchWorkflow {

    /** Aktionen; dieselben Namen wie die Kommandos in askai arch ({@code approve-evidence} …). */
    public static final String START_RESEARCH = "start-research";
    public static final String CONTINUE_RESEARCH = "continue-research";
    public static final String APPROVE_EVIDENCE = "approve-evidence";
    public static final String APPROVE_OUTLINE = "approve-outline";
    public static final String CHANGE_OUTLINE = "change-outline";
    public static final String REVISE_DOCUMENT = "revise-document";
    public static final String FINISH_DOCUMENT = "finish-document";

    /** Der Auftrag an den Agenten nach einem Übergang; der Agent richtet sich nach dem Zustand, nicht nach dem Text. */
    static final String RESEARCH_PROMPT = "Recherche starten";
    static final String OUTLINE_PROMPT = "Gliederung erstellen";
    static final String DOCUMENT_PROMPT = "Dokument erstellen";

    /** Phasen in Produktreihenfolge (technische IDs aus askai arch). */
    static List<String> phaseOrder() {
        return Arrays.asList(ResearchStateIds.SCOPING, ResearchStateIds.RESEARCH, ResearchStateIds.OUTLINE,
                ResearchStateIds.DRAFT);
    }

    /** Wie weit eine Phase ist. */
    public enum PhaseProgress {
        DONE, ACTIVE, OPEN
    }

    /** Eine Phase für die Anzeige. */
    public static final class Phase {
        private final String id;
        private final String title;
        private final PhaseProgress progress;
        private final String stateLabel;

        Phase(String id, String title, PhaseProgress progress, String stateLabel) {
            this.id = id;
            this.title = title;
            this.progress = progress;
            this.stateLabel = stateLabel;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public PhaseProgress progress() {
            return progress;
        }

        /** Nur für die aktive Phase: „läuft“, „wartet auf Freigabe“ …; sonst leer. */
        public String stateLabel() {
            return stateLabel;
        }
    }

    /** Eine mögliche Aktion (ein roter Tag). */
    public static final class Action {
        private final String id;
        private final String label;
        private final String tooltip;
        private final boolean enabled;

        Action(String id, String label, String tooltip, boolean enabled) {
            this.id = id;
            this.label = label;
            this.tooltip = tooltip;
            this.enabled = enabled;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }

        public String tooltip() {
            return tooltip;
        }

        public boolean isEnabled() {
            return enabled;
        }
    }

    /** Ergebnis einer Aktion: abgelehnt (Grund) oder ein Auftrag an den Agenten (oder nur eine Ansicht). */
    public static final class Result {
        private final String rejection;
        private final String prompt;
        private final String visibleText;
        private final String view;

        private Result(String rejection, String prompt, String visibleText, String view) {
            this.rejection = rejection;
            this.prompt = prompt;
            this.visibleText = visibleText;
            this.view = view;
        }

        static Result rejected(String reason) {
            return new Result(reason, null, null, null);
        }

        static Result prompt(String prompt, String visibleText) {
            return new Result(null, prompt, visibleText, null);
        }

        static Result view(String view) {
            return new Result(null, null, null, view);
        }

        static Result done() {
            return new Result(null, null, null, null);
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

        /** Eine Ansicht, die die Oberfläche zeigen soll ({@link #CHANGE_OUTLINE}); sonst {@code null}. */
        public String view() {
            return view;
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

    /** Die vier Phasen mit ihrem Fortschritt; die aktive trägt ihren Zustand in Worten. */
    public List<Phase> phases() {
        String phase = host.phaseId();
        String state = host.stateId();
        List<String> order = phaseOrder();
        int current = order.indexOf(phase);
        boolean completed = ResearchStateIds.COMPLETED.equals(state);
        List<Phase> phases = new ArrayList<Phase>();
        for (int index = 0; index < order.size(); index++) {
            String id = order.get(index);
            PhaseProgress progress = index < current || (index == current && completed) ? PhaseProgress.DONE
                    : index == current ? PhaseProgress.ACTIVE : PhaseProgress.OPEN;
            phases.add(new Phase(id, titleOf(id), progress, index == current ? stateLabel(phase, state) : ""));
        }
        return phases;
    }

    /** Ob die Gliederung gerade korrigiert werden darf (Phase Gliederung, nicht während sie entsteht). */
    public boolean isOutlineEditable() {
        return ResearchStateIds.OUTLINE.equals(host.phaseId()) && !ResearchStateIds.RUNNING.equals(host.stateId());
    }

    /** Ob die Fortschrittszeile des Quellenlaufs gezeigt wird (nach dem Konzept, sobald ein Lauf begonnen hat). */
    public boolean showsRunProgress() {
        return host.runStatus().hasRun() && !ResearchStateIds.SCOPING.equals(host.phaseId());
    }

    /** Ob das Dokument abgeschlossen ist. */
    public boolean isDocumentFinal() {
        return ResearchStateIds.DRAFT.equals(host.phaseId()) && ResearchStateIds.COMPLETED.equals(host.stateId());
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
        return saved < total ? line + " Mit „Dokument fortsetzen“ schreibe ich ab Kapitel " + (saved + 1) + " weiter."
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

    /** Der Zustand der aktiven Phase in Worten: läuft, wartet auf Eingabe, wartet auf Freigabe, … */
    String stateLabel(String phase, String state) {
        if (ResearchStateIds.NEW.equals(state)) {
            return "wartet auf deine Frage";
        } else if (ResearchStateIds.RUNNING.equals(state)) {
            if (ResearchStateIds.SCOPING.equals(phase)) {
                return "im Gespräch";
            }
            if (ResearchStateIds.RESEARCH.equals(phase) && !host.runStatus().isActive()) {
                return "unterbrochen";
            }
            return "läuft";
        } else if (ResearchStateIds.WAITING.equals(state)) {
            return "wartet auf Start";
        } else if (ResearchStateIds.WAITING_APPROVAL.equals(state)) {
            return "wartet auf Freigabe";
        } else if (ResearchStateIds.COMPLETED.equals(state)) {
            return "abgeschlossen";
        } else if (ResearchStateIds.PAUSED.equals(state)) {
            return "pausiert";
        } else if (ResearchStateIds.FAILED.equals(state)) {
            return "fehlgeschlagen";
        } else if (ResearchStateIds.BLOCKED.equals(state)) {
            return "blockiert";
        } else if (ResearchStateIds.CANCELLED.equals(state)) {
            return "abgebrochen";
        }
        return state;
    }

    /** Die Aktionen des aktuellen Zustands (in askai arch die roten Tags über dem Composer). */
    public List<Action> actions() {
        String phase = host.phaseId();
        String state = host.stateId();
        List<Action> actions = new ArrayList<Action>();
        if (ResearchStateIds.SCOPING.equals(phase)) {
            String gap = host.researchBriefGap();
            actions.add(new Action(START_RESEARCH, "Recherche starten", gap == null
                    ? "Konzept abschließen und die aktivierten Wissensquellen autonom durchsuchen"
                    : "Erst einen Auftrag beschreiben oder eine Konzeptkarte anlegen", true));
        } else if (ResearchStateIds.RESEARCH.equals(phase)) {
            if (ResearchStateIds.WAITING.equals(state)) {
                actions.add(new Action(START_RESEARCH, "Recherche starten",
                        "Die aktivierten Wissensquellen durchsuchen", true));
            } else if ((ResearchStateIds.RUNNING.equals(state) && !host.runStatus().isActive())
                    || ResearchStateIds.PAUSED.equals(state)) {
                actions.add(new Action(START_RESEARCH, "Recherche fortsetzen",
                        "Den unterbrochenen Lauf neu starten", true));
            } else if (ResearchStateIds.WAITING_APPROVAL.equals(state)) {
                boolean any = !host.sourceRefs().isEmpty();
                actions.add(new Action(APPROVE_EVIDENCE, "Quellen freigeben", any
                        ? "Die übernommenen Quellen freigeben und daraus die Gliederung erstellen"
                        : "Noch keine Quelle übernommen", any));
                actions.add(new Action(CONTINUE_RESEARCH, "Weiter recherchieren",
                        "Weitere Seiten in den aktivierten Wissensquellen suchen", true));
            }
        } else if (ResearchStateIds.OUTLINE.equals(phase)) {
            if (ResearchStateIds.WAITING_APPROVAL.equals(state)) {
                actions.add(new Action(APPROVE_OUTLINE, "Gliederung freigeben",
                        "Die Gliederung freigeben und das Dokument schreiben", true));
                actions.add(new Action(CHANGE_OUTLINE, "Ändern",
                        "Die Gliederung im Reiter „Gliederung“ bearbeiten oder Änderungen im Chat beschreiben", true));
            } else if (ResearchStateIds.RUNNING.equals(state) && !host.hasOutline()) {
                actions.add(new Action(APPROVE_EVIDENCE, "Gliederung erstellen",
                        "Die Gliederung aus Konzept und Quellen erstellen", true));
            }
        } else if (ResearchStateIds.DRAFT.equals(phase)) {
            int saved = ResearchStateIds.RUNNING.equals(state) ? host.documentDraftSectionCount() : 0;
            if (saved > 0) {
                // ein abgebrochener Schreiblauf: die gespeicherten Kapitel bleiben, es geht beim nächsten weiter
                actions.add(new Action(APPROVE_OUTLINE, "Dokument fortsetzen", saved
                        + (saved == 1 ? " Kapitel ist" : " Kapitel sind") + " gespeichert; ab Kapitel " + (saved + 1)
                        + " weiterschreiben", true));
            } else if (ResearchStateIds.WAITING.equals(state)
                    || (ResearchStateIds.RUNNING.equals(state) && !host.hasDocument())) {
                actions.add(new Action(APPROVE_OUTLINE, "Dokument erstellen",
                        "Das Dokument aus Gliederung und Quellen schreiben", true));
            } else if (ResearchStateIds.WAITING_APPROVAL.equals(state)) {
                actions.add(new Action(FINISH_DOCUMENT, "Dokument abschließen",
                        "Das Dokument als fertig markieren; Nachfragen bleiben möglich", true));
                actions.add(new Action(REVISE_DOCUMENT, "Dokument überarbeiten",
                        "Das Dokument neu schreiben lassen", true));
            }
        }
        return actions;
    }

    /**
     * Führt eine Aktion über die vorhandenen Übergänge aus. Was danach der Agent tun soll, steht im Ergebnis.
     */
    public Result perform(String actionId) {
        String phase = host.phaseId();
        String state = host.stateId();
        if (START_RESEARCH.equals(actionId)) {
            // nur mit sinnvollem Auftrag; sonst bleibt die Phase Konzept mit einem Hinweis
            String rejection = host.startAutonomousResearch();
            return rejection != null ? Result.rejected(rejection) : Result.prompt(RESEARCH_PROMPT,
                    ResearchStateIds.RESEARCH.equals(phase) && (ResearchStateIds.RUNNING.equals(state)
                            || ResearchStateIds.PAUSED.equals(state)) ? "Recherche fortsetzen" : "Recherche starten");
        } else if (CONTINUE_RESEARCH.equals(actionId)) {
            String rejection = host.dispatch(ResearchCommandType.REQUEST_REVISION);
            return rejection != null ? Result.rejected(rejection)
                    : Result.prompt(RESEARCH_PROMPT, "Weiter recherchieren");
        } else if (APPROVE_EVIDENCE.equals(actionId)) {
            if (ResearchStateIds.OUTLINE.equals(phase) && ResearchStateIds.RUNNING.equals(state)) {
                return Result.prompt(OUTLINE_PROMPT, "Gliederung erstellen");
            }
            if (host.sourceRefs().isEmpty()) {
                return Result.rejected("Es ist noch keine Quelle übernommen.");
            }
            String rejection = host.dispatch(ResearchCommandType.APPROVE_EVIDENCE);
            return rejection != null ? Result.rejected(rejection) : Result.prompt(OUTLINE_PROMPT, "Quellen freigeben");
        } else if (APPROVE_OUTLINE.equals(actionId)) {
            String rejection = null;
            String visible = "Gliederung freigeben";
            if (ResearchStateIds.OUTLINE.equals(phase)) {
                rejection = host.dispatch(ResearchCommandType.APPROVE_OUTLINE);
                state = host.stateId();
                phase = host.phaseId();
            } else {
                visible = host.documentDraftSectionCount() > 0 ? "Dokument fortsetzen" : "Dokument erstellen";
            }
            if (rejection == null && ResearchStateIds.DRAFT.equals(phase) && ResearchStateIds.WAITING.equals(state)) {
                rejection = host.dispatch(ResearchCommandType.START_DRAFTING);
            }
            return rejection != null ? Result.rejected(rejection) : Result.prompt(DOCUMENT_PROMPT, visible);
        } else if (CHANGE_OUTLINE.equals(actionId)) {
            return Result.view(CHANGE_OUTLINE);
        } else if (REVISE_DOCUMENT.equals(actionId)) {
            String rejection = host.dispatch(ResearchCommandType.REQUEST_REVISION);
            return rejection != null ? Result.rejected(rejection)
                    : Result.prompt(DOCUMENT_PROMPT, "Dokument überarbeiten");
        } else if (FINISH_DOCUMENT.equals(actionId)) {
            String rejection = host.dispatch(ResearchCommandType.APPROVE_FINAL);
            return rejection != null ? Result.rejected(rejection) : Result.done();
        }
        return Result.rejected("Unbekannte Aktion: " + actionId);
    }
}

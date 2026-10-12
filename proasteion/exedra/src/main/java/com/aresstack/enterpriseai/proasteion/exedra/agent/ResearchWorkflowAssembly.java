package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.ResearchAgentHost;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchDocument;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchRunStatus;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchSourceRef;
import com.aresstack.enterpriseai.proasteion.application.agent.ResearchWorkflow;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModelListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchDocumentView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchOutlineView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchSourcesView;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchPlayBar;
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchSearchTagStrip;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Verdrahtet den Research-Workflow des Agent-Modus in drei getrennten Ebenen ({@link ResearchWorkflow}): die vom
 * Nutzer gewählte <b>Phase</b> (Dropdown im Composer, über {@link Parts#selectPhase} und {@link Parts#onPhase}), die
 * <b>Artefakt-Reiter</b> „Quellen“, „Gliederung“ und „Dokument“ (reine Ansicht; der Reiter „Konzept“ kommt aus
 * {@link ResearchConceptViewAssembly}) und je Reiter seine <b>Play-Aktion</b>, aktiv nur in der eigenen Phase.
 * Keine Ebene schaltet eine andere um: Play ändert keine Phase, ein Reiterwechsel auch nicht. Über dem Composer
 * stehen nur noch die gelben Suchvorschläge (Direktsuche, kein Phasenwechsel). Alles wird aus dem
 * {@link ResearchAgentHost} abgeleitet und bei jeder Zustandsänderung neu gezeichnet.
 */
public final class ResearchWorkflowAssembly {

    /** Drawer-Reiter (Titel). */
    public static final String SOURCES_TAB = "Quellen";
    public static final String OUTLINE_TAB = "Gliederung";
    public static final String DOCUMENT_TAB = "Dokument";

    /** Öffnet eine Quelle des Projekts im Lesebereich. */
    public interface SourceOpener {
        void open(String resourceId, String title);
    }

    /** Schickt den Auftrag einer Aktion an den Agenten ({@link AgentServiceBinding#sendAction}). */
    public interface ActionSender {
        void send(String visibleText, String prompt);
    }

    /** Die gebauten Teile. */
    public static final class Parts {
        private final ResearchSearchTagStrip strip;
        private final ResearchSourcesView sources;
        private final ResearchOutlineView outline;
        private final ResearchDocumentView document;
        private final java.util.Map<String, ResearchPlayBar> plays;
        private final ResearchWorkflow workflow;
        private final Runnable refresh;
        private final Consumer<String> perform;
        private final Consumer<String> search;
        private final Consumer<String> select;
        private final List<Consumer<String>> phaseListeners;

        Parts(ResearchSearchTagStrip strip, ResearchSourcesView sources, ResearchOutlineView outline,
              ResearchDocumentView document, java.util.Map<String, ResearchPlayBar> plays, ResearchWorkflow workflow,
              Runnable refresh, Consumer<String> perform, Consumer<String> search, Consumer<String> select,
              List<Consumer<String>> phaseListeners) {
            this.strip = strip;
            this.sources = sources;
            this.outline = outline;
            this.document = document;
            this.plays = plays;
            this.workflow = workflow;
            this.refresh = refresh;
            this.perform = perform;
            this.search = search;
            this.select = select;
            this.phaseListeners = phaseListeners;
        }

        /** Führt einen gelben Suchvorschlag aus wie ein Klick auf ihn (auf dem UI-Thread). */
        public void search(String query) {
            search.accept(query);
        }

        public ResearchSearchTagStrip strip() {
            return strip;
        }

        public ResearchSourcesView sources() {
            return sources;
        }

        public ResearchOutlineView outline() {
            return outline;
        }

        public ResearchDocumentView document() {
            return document;
        }

        /** Die Play-Leiste des Reiters einer Phase ({@code research}, {@code outline}, {@code draft}). */
        public ResearchPlayBar play(String phaseId) {
            return plays.get(phaseId);
        }

        /** Liest den Host neu und zeichnet alles (läuft auch auf jede Zustandsänderung). */
        public void refresh() {
            refresh.run();
        }

        /** Die Play-Aktion einer Phase wie ein Klick auf ihren Knopf (auf dem UI-Thread). */
        public void perform(String phaseId) {
            perform.accept(phaseId);
        }

        /** Der Nutzer wählt eine Phase (Dropdown; auf dem UI-Thread). */
        public void selectPhase(String phaseId) {
            select.accept(phaseId);
        }

        /** Kennungen der wählbaren Phasen in Produktreihenfolge. */
        public List<String> phaseIds() {
            List<String> ids = new ArrayList<String>();
            for (ResearchWorkflow.PhaseChoice choice : workflow.phaseChoices()) {
                ids.add(choice.id());
            }
            return ids;
        }

        /** Titel der wählbaren Phasen in Produktreihenfolge. */
        public List<String> phaseTitles() {
            List<String> titles = new ArrayList<String>();
            for (ResearchWorkflow.PhaseChoice choice : workflow.phaseChoices()) {
                titles.add(choice.title());
            }
            return titles;
        }

        /** Hört auf die aktive Phase (auf dem UI-Thread, bei jeder Zustandsänderung, sofort mit dem Stand). */
        public void onPhase(Consumer<String> listener) {
            phaseListeners.add(listener);
            listener.accept(workflow.currentPhaseId());
        }
    }

    private ResearchWorkflowAssembly() {
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    public static Parts create(final ResearchAgentHost host, final ActionSender sender,
                               final ChatShellModel chat, final Executor uiExecutor, final SourceOpener opener) {
        final ResearchWorkflow workflow = new ResearchWorkflow(host);
        final ResearchSearchTagStrip strip = new ResearchSearchTagStrip();
        final ResearchSourcesView sources = new ResearchSourcesView();
        final ResearchOutlineView outline = new ResearchOutlineView();
        final ResearchDocumentView document = new ResearchDocumentView();
        final java.util.Map<String, ResearchPlayBar> plays = new java.util.LinkedHashMap<String, ResearchPlayBar>();
        for (String phase : playPhases()) {
            ResearchWorkflow.Play play = workflow.play(phase);
            plays.put(phase, new ResearchPlayBar("research-play-" + phase, play.tooltip()));
        }
        sources.setPlayBar(plays.get(ResearchWorkflow.PHASE_SOURCES));
        outline.setPlayBar(plays.get(ResearchWorkflow.PHASE_OUTLINE));
        document.setPlayBar(plays.get(ResearchWorkflow.PHASE_DOCUMENT));
        final List<Consumer<String>> phaseListeners = new java.util.concurrent.CopyOnWriteArrayList<Consumer<String>>();

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                final boolean busy = chat.isStreaming();
                final ResearchSearchTagStrip.Snapshot tags = tagSnapshot(workflow, busy);
                final String phase = workflow.currentPhaseId();
                final java.util.Map<String, ResearchWorkflow.Play> playStates =
                        new java.util.LinkedHashMap<String, ResearchWorkflow.Play>();
                for (String id : playPhases()) {
                    playStates.put(id, workflow.play(id));
                }
                final List<ResearchSourcesView.SourceItem> sourceItems = sourceItems(host);
                final String runLine = runLine(host);
                final String outlineText = host.outline();
                final boolean outlineEditable = workflow.isOutlineEditable();
                final ResearchDocument current = shownDocument(host);
                final String documentText = current == null ? "" : current.toMarkdown();
                final String draftLine = workflow.documentDraftLine();
                final String documentLine = draftLine.isEmpty() ? documentLine(workflow, current) : draftLine;
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        strip.render(tags);
                        for (java.util.Map.Entry<String, ResearchWorkflow.Play> entry : playStates.entrySet()) {
                            ResearchWorkflow.Play play = entry.getValue();
                            plays.get(entry.getKey()).render(play.isEnabled() && !busy, play.isEnabled() && busy
                                    ? "Der Agent arbeitet noch am vorherigen Auftrag." : play.tooltip());
                        }
                        sources.render(sourceItems, runLine);
                        outline.render(outlineText, outlineEditable);
                        document.render(documentText, documentLine);
                        for (Consumer<String> listener : phaseListeners) {
                            listener.accept(phase);
                        }
                    }
                });
            }
        };
        final Consumer<String> perform = new Consumer<String>() {
            @Override
            public void accept(String phaseId) {
                if (chat.isStreaming()) {
                    chat.addNotice("Der Agent arbeitet noch am vorherigen Auftrag.");
                    return;
                }
                // Die Play-Aktion arbeitet nur in ihrer Phase und lässt die Phase, wie sie ist.
                ResearchWorkflow.Result result = workflow.perform(phaseId);
                if (result.rejection() != null) {
                    chat.addNotice("Nicht möglich: " + result.rejection());
                } else if (result.prompt() != null) {
                    sender.send(result.visibleText(), result.prompt());
                }
                render.run();
            }
        };
        for (final String phase : playPhases()) {
            plays.get(phase).setAction(new Runnable() {
                @Override
                public void run() {
                    perform.accept(phase);
                }
            });
        }
        final Consumer<String> select = new Consumer<String>() {
            @Override
            public void accept(String phaseId) {
                if (chat.isStreaming()) {
                    chat.addNotice("Die Phase lässt sich wechseln, sobald der Agent mit dem Auftrag fertig ist.");
                    render.run(); // das Dropdown zeigt wieder die gültige Phase
                    return;
                }
                String rejection = workflow.selectPhase(phaseId);
                if (rejection != null) {
                    chat.addNotice("Nicht möglich: " + rejection);
                }
                render.run();
            }
        };
        // Ein gelber Vorschlag ist eine Direktrecherche genau dieser Anfrage, kein Phasenwechsel (askai arch:
        // ein Klick IST die Suche); der Agent meldet danach Haken oder Retry.
        final Consumer<String> search = new Consumer<String>() {
            @Override
            public void accept(String query) {
                if (chat.isStreaming()) {
                    chat.addNotice("Der Agent arbeitet noch am vorherigen Auftrag.");
                    return;
                }
                ResearchWorkflow.Result result = workflow.search(query);
                if (result.rejection() != null) {
                    chat.addNotice("Nicht möglich: " + result.rejection());
                } else if (result.prompt() != null) {
                    sender.send(result.visibleText(), result.prompt());
                }
                render.run();
            }
        };
        strip.setSearchHandler(search);
        sources.setOpenHandler(new Consumer<String>() {
            @Override
            public void accept(String resourceId) {
                for (ResearchSourceRef ref : host.sourceRefs()) {
                    if (ref.resourceId().equals(resourceId)) {
                        opener.open(resourceId, ref.title());
                        return;
                    }
                }
            }
        });
        outline.setSaveHandler(new ResearchOutlineView.SaveHandler() {
            @Override
            public String save(String markdown) {
                return host.saveOutline(markdown);
            }
        });
        document.setSourceHandler(new IntConsumer() {
            @Override
            public void accept(int number) {
                ResearchDocument current = shownDocument(host);
                ResearchDocument.Source source = current == null ? null : current.source(number);
                if (source != null) {
                    opener.open(source.resourceId(), source.title());
                }
            }
        });
        host.addStateListener(render);
        // Läuft ein Auftrag an oder endet er, ändern sich nur Play-Knöpfe und Vorschläge (gesperrt/frei).
        chat.addListener(new ChatShellModelListener() {
            private boolean streaming = chat.isStreaming();

            @Override
            public void entryAdded(TranscriptEntry entry) {
            }

            @Override
            public void entryUpdated(TranscriptEntry entry) {
            }

            @Override
            public void stateChanged() {
                if (streaming != chat.isStreaming()) {
                    streaming = chat.isStreaming();
                    render.run();
                }
            }
        });
        render.run();
        return new Parts(strip, sources, outline, document, plays, workflow, render, perform, search, select,
                phaseListeners);
    }

    /** Die Phasen mit Play-Aktion (Reiter Quellen, Gliederung, Dokument); das Konzept hat keine. */
    static List<String> playPhases() {
        return java.util.Arrays.asList(ResearchWorkflow.PHASE_SOURCES, ResearchWorkflow.PHASE_OUTLINE,
                ResearchWorkflow.PHASE_DOCUMENT);
    }

    static ResearchSearchTagStrip.Snapshot tagSnapshot(ResearchWorkflow workflow, boolean busy) {
        List<ResearchSearchTagStrip.SuggestionItem> suggestions =
                new ArrayList<ResearchSearchTagStrip.SuggestionItem>();
        for (ResearchAgentHost.SearchTag tag : workflow.searchTags()) {
            suggestions.add(new ResearchSearchTagStrip.SuggestionItem(tag.query(), tag.purpose(), tag.status()));
        }
        return new ResearchSearchTagStrip.Snapshot(suggestions, busy);
    }

    /** Das fertige Dokument, sonst der Zwischenstand eines abgebrochenen Schreiblaufs (Kapitel 1..n-1). */
    private static ResearchDocument shownDocument(ResearchAgentHost host) {
        ResearchDocument document = host.document();
        return document != null ? document : host.documentDraft();
    }

    private static List<ResearchSourcesView.SourceItem> sourceItems(ResearchAgentHost host) {
        List<ResearchSourcesView.SourceItem> items = new ArrayList<ResearchSourcesView.SourceItem>();
        int number = 1;
        for (ResearchSourceRef ref : host.sourceRefs()) {
            StringBuilder detail = new StringBuilder(ref.url().isEmpty() ? ref.resourceId() : ref.url());
            if (!ref.revision().isEmpty()) {
                detail.append(" - Revision ").append(ref.revision());
            }
            items.add(new ResearchSourcesView.SourceItem(number++, ref.resourceId(), ref.title(), detail.toString()));
        }
        return items;
    }

    private static String runLine(ResearchAgentHost host) {
        ResearchRunStatus status = host.runStatus();
        int parked = host.parkedCount();
        String line = status.hasRun() ? status.describe() : "";
        if (parked > 0) {
            line = line + (line.isEmpty() ? "" : " - ") + parked + " Treffer vorgemerkt";
        }
        return line;
    }

    private static String documentLine(ResearchWorkflow workflow, ResearchDocument document) {
        if (document == null) {
            return "";
        }
        String state = workflow.isDocumentFinal() ? "abgeschlossen" : "Entwurf";
        return document.sections().size() + " Abschnitte, " + document.sources().size() + " Quellen, "
                + document.claims().size() + " belegte Aussagen (" + state + ")";
    }
}

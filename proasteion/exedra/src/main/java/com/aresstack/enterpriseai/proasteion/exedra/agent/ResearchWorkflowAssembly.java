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
import com.aresstack.enterpriseai.proasteion.exedra.ui.research.ResearchWorkflowStrip;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Verdrahtet den geführten Recherche-Workflow des Agent-Modus: die Phasenleiste mit den Aktions-Tags über dem
 * Composer und die Drawer-Reiter „Quellen“, „Gliederung“ und „Dokument“. Alles wird aus dem {@link ResearchAgentHost}
 * abgeleitet ({@link ResearchWorkflow}: Phase und Zustand der Zustandsmaschine, keine zweite Zustandsverwaltung) und
 * bei jeder Zustandsänderung des Hosts neu gezeichnet. Eine Aktion löst die vorhandenen Übergänge aus und schickt den
 * Auftrag an den Agenten wie eine Chatnachricht; die Textbefehle bleiben ein zweiter Weg.
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

    /** Wie die Oberfläche einen Drawer-Reiter zeigt. */
    public interface TabShower {
        void show(String title);
    }

    /** Die gebauten Teile. */
    public static final class Parts {
        private final ResearchWorkflowStrip strip;
        private final ResearchSourcesView sources;
        private final ResearchOutlineView outline;
        private final ResearchDocumentView document;
        private final Runnable refresh;
        private final Consumer<String> perform;
        private final Consumer<String> search;

        Parts(ResearchWorkflowStrip strip, ResearchSourcesView sources, ResearchOutlineView outline,
              ResearchDocumentView document, Runnable refresh, Consumer<String> perform, Consumer<String> search) {
            this.strip = strip;
            this.sources = sources;
            this.outline = outline;
            this.document = document;
            this.refresh = refresh;
            this.perform = perform;
            this.search = search;
        }

        /** Führt einen gelben Suchvorschlag aus wie ein Klick auf ihn (auf dem UI-Thread). */
        public void search(String query) {
            search.accept(query);
        }

        public ResearchWorkflowStrip strip() {
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

        /** Liest den Host neu und zeichnet alles (läuft auch auf jede Zustandsänderung). */
        public void refresh() {
            refresh.run();
        }

        /** Führt eine Aktion aus wie ein Klick auf ihren Tag (auf dem UI-Thread). */
        public void perform(String actionId) {
            perform.accept(actionId);
        }
    }

    private ResearchWorkflowAssembly() {
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    public static Parts create(final ResearchAgentHost host, final ActionSender sender,
                               final ChatShellModel chat, final Executor uiExecutor, final SourceOpener opener,
                               final TabShower tabs) {
        final ResearchWorkflow workflow = new ResearchWorkflow(host);
        final ResearchWorkflowStrip strip = new ResearchWorkflowStrip();
        final ResearchSourcesView sources = new ResearchSourcesView();
        final ResearchOutlineView outline = new ResearchOutlineView();
        final ResearchDocumentView document = new ResearchDocumentView();

        final Runnable render = new Runnable() {
            @Override
            public void run() {
                final ResearchWorkflowStrip.Snapshot strip0 = stripSnapshot(workflow, host, chat.isStreaming());
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
                        strip.render(strip0);
                        sources.render(sourceItems, runLine);
                        outline.render(outlineText, outlineEditable);
                        document.render(documentText, documentLine);
                    }
                });
            }
        };
        final Consumer<String> perform = new Consumer<String>() {
            @Override
            public void accept(String actionId) {
                if (chat.isStreaming()) {
                    chat.addNotice("Der Agent arbeitet noch am vorherigen Auftrag.");
                    return;
                }
                ResearchWorkflow.Result result = workflow.perform(actionId);
                if (result.rejection() != null) {
                    chat.addNotice("Nicht möglich: " + result.rejection());
                } else if (ResearchWorkflow.CHANGE_OUTLINE.equals(result.view())) {
                    tabs.show(OUTLINE_TAB);
                    outline.startEditing();
                } else if (result.prompt() != null) {
                    if (ResearchWorkflow.APPROVE_OUTLINE.equals(actionId)) {
                        tabs.show(DOCUMENT_TAB);
                    } else if (ResearchWorkflow.APPROVE_EVIDENCE.equals(actionId)) {
                        tabs.show(OUTLINE_TAB);
                    } else if (ResearchWorkflow.START_RESEARCH.equals(actionId)
                            || ResearchWorkflow.CONTINUE_RESEARCH.equals(actionId)) {
                        tabs.show(SOURCES_TAB);
                    }
                    sender.send(result.visibleText(), result.prompt());
                }
                render.run();
            }
        };
        strip.setActionHandler(perform);
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
        // Läuft ein Auftrag an oder endet er, ändern sich nur die Tags (gesperrt/frei).
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
        return new Parts(strip, sources, outline, document, render, perform, search);
    }

    static ResearchWorkflowStrip.Snapshot stripSnapshot(ResearchWorkflow workflow, ResearchAgentHost host,
                                                        boolean busy) {
        List<ResearchWorkflowStrip.PhaseItem> phases = new ArrayList<ResearchWorkflowStrip.PhaseItem>();
        for (ResearchWorkflow.Phase phase : workflow.phases()) {
            phases.add(new ResearchWorkflowStrip.PhaseItem(phase.title(), phase.progress().name(),
                    phase.stateLabel()));
        }
        List<ResearchWorkflowStrip.ActionItem> actions = new ArrayList<ResearchWorkflowStrip.ActionItem>();
        for (ResearchWorkflow.Action action : workflow.actions()) {
            actions.add(new ResearchWorkflowStrip.ActionItem(action.id(), action.label(), action.tooltip(),
                    action.isEnabled()));
        }
        List<ResearchWorkflowStrip.SuggestionItem> suggestions = new ArrayList<ResearchWorkflowStrip.SuggestionItem>();
        for (ResearchAgentHost.SearchTag tag : workflow.searchTags()) {
            suggestions.add(new ResearchWorkflowStrip.SuggestionItem(tag.query(), tag.purpose(), tag.status()));
        }
        return new ResearchWorkflowStrip.Snapshot(phases,
                workflow.showsRunProgress() ? host.runStatus().describe() : "", suggestions, actions, busy);
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

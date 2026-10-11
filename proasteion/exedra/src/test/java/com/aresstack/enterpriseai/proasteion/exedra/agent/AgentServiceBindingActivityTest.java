package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpPlanEntry;
import com.aresstack.enterpriseai.acp.api.AcpPromptState;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AcpToolCall;
import com.aresstack.enterpriseai.acp.api.AcpUpdate;
import com.aresstack.enterpriseai.acp.api.AcpUpdateListener;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;
import com.aresstack.enterpriseai.acp.api.PromptDispatcher;
import com.aresstack.enterpriseai.acp.api.PromptHandle;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentLauncher;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentToolActivity;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.TranscriptEntry;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Denk-, Werkzeug- und Planwolken über stabile Kennungen (askai arch): gestreamte Überlegungen bleiben eine Wolke,
 * auch wenn dazwischen die Recherche-Karte oder der Plan aktualisiert wird; Plan-Updates ersetzen dieselbe Wolke;
 * ein Werkzeugergebnis steht in der Ergebniszeile.
 */
public class AgentServiceBindingActivityTest {

    private static final Executor DIRECT = Runnable::run;

    private ChatShellModel model;
    private AgentServiceBinding binding;
    private PromptDispatcher prompt;

    @Before
    public void setUp() {
        model = new ChatShellModel(() -> 0L);
        binding = new AgentServiceBinding(new AgentService(new ScriptedLauncher(), DIRECT), model, DIRECT);
    }

    @Test
    public void thoughtChunksAroundCardUpdatesStayOneCloud() {
        binding.sendRequested("Suche IAR", false);
        prompt.update(new AcpToolCall("research-1", "Recherche in den Wissensquellen", "research",
                AcpToolCall.Status.IN_PROGRESS, "Suche läuft …", null));
        prompt.update(AcpUpdate.Kind.THOUGHT, "Ich ");
        prompt.update(new AcpToolCall("research-1", null, null, AcpToolCall.Status.IN_PROGRESS,
                "3 Treffer [[bar:1/4]]", null));
        prompt.update(AcpUpdate.Kind.THOUGHT, "lese ");
        prompt.updateOther(AcpUpdate.TYPE_PLAN, "", Arrays.asList(
                new AcpPlanEntry("Quellen lesen", "high", AcpPlanEntry.IN_PROGRESS)));
        prompt.updateOther(AcpUpdate.TYPE_PLAN, "", Arrays.asList(
                new AcpPlanEntry("Quellen lesen", "high", AcpPlanEntry.IN_PROGRESS),
                new AcpPlanEntry("Antworten", "medium", AcpPlanEntry.PENDING)));
        prompt.update(AcpUpdate.Kind.THOUGHT, "weiter.");

        List<TranscriptEntry> thoughts = activities(true);
        assertEquals("eine Denkwolke für alle Stücke", 1, thoughts.size());
        assertEquals("Ich lese weiter.", thoughts.get(0).getText());
        assertEquals(TranscriptEntry.State.STREAMING, thoughts.get(0).getState());

        List<TranscriptEntry> tools = activities(false);
        assertEquals("Karte und Plan je eine Wolke", 2, tools.size());
        assertEquals("3 Treffer [[bar:1/4]]", tools.get(0).getText());
        assertEquals(AgentToolActivity.KIND_PLAN, tools.get(1).getActivityKind());
        assertEquals("[>] Quellen lesen\n[ ] Antworten", tools.get(1).getText());

        prompt.update(new AcpToolCall("research-1", null, null, AcpToolCall.Status.COMPLETED, null,
                "2 Quellen übernommen"));
        prompt.update(AcpUpdate.Kind.MESSAGE, "IAR bündelt die Auswertung.");
        prompt.terminal(AcpPromptState.COMPLETED, "");

        assertEquals(TranscriptEntry.State.COMPLETE, thoughts.get(0).getState());
        assertEquals("2 Quellen übernommen", tools.get(0).getActivitySummary());
        assertFalse(model.isStreaming());
    }

    @Test
    public void otherAgentEventsAreShownNotDropped() {
        binding.sendRequested("Los", false);
        prompt.updateOther("current_mode_update", "currentModeId: ask", null);
        prompt.updateOther(AcpUpdate.TYPE_PLAN, "", Arrays.asList(
                new AcpPlanEntry("Schritt", "high", AcpPlanEntry.COMPLETED)));
        prompt.terminal(AcpPromptState.COMPLETED, "");

        List<TranscriptEntry> tools = activities(false);
        assertEquals(2, tools.size());
        assertEquals(AgentToolActivity.EVENT_TITLE_PREFIX + "current_mode_update", tools.get(0).getActivityTitle());
        assertEquals("currentModeId: ask", tools.get(0).getText());
        assertEquals(TranscriptEntry.State.COMPLETE, tools.get(1).getState());
        assertEquals(AgentToolActivity.PLAN_DONE, tools.get(1).getActivitySummary());
        assertTrue(tools.get(1).getText().startsWith("[x] "));
    }

    @Test
    public void failedToolShowsItsErrorTextOrAFallback() {
        binding.sendRequested("Los", false);
        prompt.update(new AcpToolCall("call-1", "Suche", "search", AcpToolCall.Status.IN_PROGRESS, "IAR", null));
        prompt.update(new AcpToolCall("call-1", null, null, AcpToolCall.Status.FAILED, null,
                "Wissensquelle nicht erreichbar"));
        prompt.update(new AcpToolCall("call-2", "Lese Seite", "read", AcpToolCall.Status.IN_PROGRESS, "IAR", null));
        prompt.update(new AcpToolCall("call-2", null, null, AcpToolCall.Status.FAILED, null, null));
        prompt.terminal(AcpPromptState.COMPLETED, "");

        List<TranscriptEntry> tools = activities(false);
        assertEquals(TranscriptEntry.State.FAILED, tools.get(0).getState());
        assertEquals("Wissensquelle nicht erreichbar", tools.get(0).getActivitySummary());
        assertEquals(TranscriptEntry.State.FAILED, tools.get(1).getState());
        assertEquals(AgentServiceBinding.FAILED_WITHOUT_TEXT, tools.get(1).getActivitySummary());
    }

    @Test
    public void markedUtterancesBecomeSeparateBubbles() {
        binding.sendRequested("Los", false);
        prompt.updateMessage("Hallo.", true);
        prompt.updateMessage(" Noch was.", false);
        prompt.updateMessage("\n\nIch suche.", true);
        prompt.terminal(AcpPromptState.COMPLETED, "");

        List<String> answers = new ArrayList<String>();
        for (TranscriptEntry entry : model.getEntries()) {
            if (entry.getAuthor() == TranscriptEntry.Author.ASSISTANT) {
                answers.add(entry.getText());
                assertEquals(TranscriptEntry.State.COMPLETE, entry.getState());
            }
        }
        assertEquals(Arrays.asList("Hallo. Noch was.", "Ich suche."), answers);
    }

    private List<TranscriptEntry> activities(boolean thinking) {
        List<TranscriptEntry> result = new ArrayList<TranscriptEntry>();
        for (TranscriptEntry entry : model.getEntries()) {
            if (entry.getAuthor() == TranscriptEntry.Author.ACTIVITY && entry.isThinking() == thinking) {
                result.add(entry);
            }
        }
        return result;
    }

    /** Ein Agent ohne Prozess: der Test schreibt die Updates des laufenden Prompts selbst. */
    private final class ScriptedLauncher implements AgentLauncher {
        @Override
        public AcpConnection launch() {
            return new AcpConnection() {
                @Override
                public AcpConnectionState getState() {
                    return AcpConnectionState.READY;
                }

                @Override
                public AgentProcessHandle getProcess() {
                    return new AgentProcessHandle() {
                        @Override
                        public boolean isAlive() {
                            return true;
                        }

                        @Override
                        public void destroyForcibly() {
                        }
                    };
                }

                @Override
                public AcpSession newSession() {
                    return new AcpSession() {
                        @Override
                        public String getSessionId() {
                            return "scripted";
                        }

                        @Override
                        public AcpSessionState getState() {
                            return AcpSessionState.ACTIVE;
                        }

                        @Override
                        public PromptHandle prompt(String text, AcpUpdateListener listener) {
                            final PromptDispatcher dispatcher = new PromptDispatcher("scripted", "p1", listener);
                            prompt = dispatcher;
                            return new PromptHandle() {
                                @Override
                                public String getPromptId() {
                                    return "p1";
                                }

                                @Override
                                public AcpPromptState getState() {
                                    return dispatcher.getState();
                                }

                                @Override
                                public void cancel() {
                                    dispatcher.cancelling();
                                }
                            };
                        }

                        @Override
                        public void close() {
                        }
                    };
                }

                @Override
                public void close() {
                }
            };
        }
    }
}

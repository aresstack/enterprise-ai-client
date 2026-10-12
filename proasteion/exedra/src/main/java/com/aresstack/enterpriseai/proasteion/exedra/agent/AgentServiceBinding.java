package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.AgentModelChoice;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatPage;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatQuote;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatResource;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentFailure;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentTurn;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentToolActivity;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentTurnListener;

import com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentTextExtractor;
import com.aresstack.enterpriseai.astu.acropolis.chat.QuotedPassage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Verbindet eine Agent-Ansicht mit dem {@link AgentService}, nach demselben Muster wie {@code ChatServiceBinding}
 * für den Chat. Das {@link ChatShellModel} ist eine eigene Instanz nur für den Agenten; der Chat-Verlauf wird
 * nie berührt.
 *
 * <p>Senden hängt Auftrag und leere Agentenblase an; die Blase zeigt bis zur ersten Antwortzeile den
 * Platzhalter (auch während der Agent startet). Wie in askai-java8 {@code arch} zeigt der Verlauf, woran der Agent
 * arbeitet: jeder Werkzeugaufruf ist eine gelbe Gedankenwolke mit Titel und Eingabe, die mit ihrer Ergebniszeile
 * platzt; Denkschritte und gestreamte Überlegungen des Agenten stehen in einer grünen Wolke. Stop bricht den Auftrag
 * ab; die Blase endet, wenn der Agent den Abbruch bestätigt. Der RAG-Schalter spielt im Agent-Modus keine Rolle.
 */
public final class AgentServiceBinding implements ChatShellActions {

    private final AgentService agentService;
    private final ChatShellModel model;
    private final Executor uiExecutor;
    private AgentTurn runningTurn;
    private final AgentModelChoice choice;
    private AttachmentTextExtractor extractor;
    private Executor workExecutor;
    /** Stop, während die Anhänge noch gelesen werden (nur UI-Thread). */
    private boolean cancelBeforeStart;

    /** Ergebniszeile eines fehlgeschlagenen Werkzeugaufrufs ohne Fehlertext. */
    static final String FAILED_WITHOUT_TEXT = "fehlgeschlagen";

    /** Höchstens so viele Zeichen Text je Anhang gehen an den Agenten. */
    static final int MAX_ATTACHMENT_CHARS = 30000;

    public AgentServiceBinding(AgentService agentService, ChatShellModel model, Executor uiExecutor) {
        this(agentService, model, uiExecutor, null);
    }

    /** @param choice Modell und Denkaufwand des eingebauten Agenten; {@code null} bei einem externen Agenten */
    public AgentServiceBinding(AgentService agentService, ChatShellModel model, Executor uiExecutor,
                               AgentModelChoice choice) {
        if (agentService == null || model == null || uiExecutor == null) {
            throw new IllegalArgumentException("agentService, model and uiExecutor must not be null");
        }
        this.agentService = agentService;
        this.model = model;
        this.uiExecutor = uiExecutor;
        this.choice = choice;
    }

    /**
     * Anhänge im Agent-Modus: der Text jeder angehängten Datei (über denselben Extraktor wie im Chat) geht mit der
     * Nachricht an den Agenten; gelesen wird auf {@code workExecutor}, nie auf dem UI-Thread.
     */
    public void enableAttachments(AttachmentTextExtractor extractor, Executor workExecutor) {
        this.extractor = extractor;
        this.workExecutor = workExecutor;
    }

    @Override
    public boolean supportsAttachments() {
        return extractor != null && workExecutor != null;
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void sendRequested(final String text, boolean ragEnabled, final List<Path> attachments) {
        if (attachments == null || attachments.isEmpty() || !supportsAttachments()) {
            sendRequested(text, ragEnabled);
            return;
        }
        send(text, text, attachments, Collections.<ChatQuote>emptyList());
    }

    /**
     * Zitate im Agent-Modus (UI-Thread): Im Verlauf steht {@code visibleText} mit den Zitat-Chips; der Agent bekommt
     * vor {@code prompt} den Hinweis auf die zitierten Passagen ({@link QuotedPassage#context}), danach wie gewohnt
     * den Text der Anhänge.
     */
    public void sendQuoted(String visibleText, String prompt, List<Path> attachments, List<ChatQuote> quotes) {
        List<Path> files = attachments == null || !supportsAttachments()
                ? Collections.<Path>emptyList() : attachments;
        send(visibleText, prompt, files, quotes == null ? Collections.<ChatQuote>emptyList() : quotes);
    }

    private void send(String visibleText, String prompt, List<Path> attachments, List<ChatQuote> quotes) {
        if (!model.canSend(visibleText)) {
            return;
        }
        List<String> names = new ArrayList<String>(attachments.size());
        for (Path file : attachments) {
            names.add(file.getFileName().toString());
        }
        model.addUserMessage(visibleText, names, Collections.<ChatPage>emptyList(),
                Collections.<ChatResource>emptyList(), quotes);
        model.beginAssistantMessage();
        final TurnListener listener = new TurnListener();
        List<QuotedPassage> passages = new ArrayList<QuotedPassage>(quotes.size());
        for (ChatQuote quote : quotes) {
            passages.add(new QuotedPassage(quote.selectedText()));
        }
        String quoteContext = QuotedPassage.context(passages);
        final String request = quoteContext == null ? prompt : quoteContext + "\n\n" + prompt;
        if (attachments.isEmpty()) {
            start(request, listener);
            return;
        }
        final List<Path> files = new ArrayList<Path>(attachments);
        cancelBeforeStart = false;
        workExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final String withFiles = withAttachments(request, files, extractor);
                uiExecutor.execute(new Runnable() {
                    @Override
                    public void run() {
                        if (cancelBeforeStart) {
                            listener.onCancelled();
                            return;
                        }
                        start(withFiles, listener);
                    }
                });
            }
        });
    }

    /** Die Nachricht an den Agenten: der Text des Nutzers, danach der Inhalt jeder angehängten Datei. */
    static String withAttachments(String text, List<Path> files, AttachmentTextExtractor extractor) {
        StringBuilder prompt = new StringBuilder(text);
        prompt.append("\n\nAngehängte Dateien:");
        for (Path file : files) {
            String name = file.getFileName().toString();
            prompt.append("\n\n--- ").append(name).append(" ---\n");
            try {
                String content = extractor.extractText(name, Files.readAllBytes(file));
                if (content == null || content.trim().isEmpty()) {
                    prompt.append("(kein Text enthalten)");
                } else if (content.length() > MAX_ATTACHMENT_CHARS) {
                    prompt.append(content, 0, MAX_ATTACHMENT_CHARS).append("\n(gekürzt, ")
                            .append(content.length()).append(" Zeichen insgesamt)");
                } else {
                    prompt.append(content);
                }
            } catch (IOException | RuntimeException unreadable) {
                prompt.append("(nicht lesbar: ").append(unreadable.getMessage()).append(')');
            }
        }
        return prompt.toString();
    }

    /** Der Denkaufwand aus dem Composer gilt ab der nächsten Modellanfrage des eingebauten Agenten. */
    @Override
    public void reasoningChanged(String effort) {
        if (choice != null) {
            choice.reasoningChanged(effort);
        }
    }

    /** Das im Composer gewählte Modell; {@code null} bei einem externen Agenten. */
    public AgentModelChoice modelChoice() {
        return choice;
    }

    /** Muss auf dem UI-Thread gerufen werden (wie alle Model-Änderungen). */
    @Override
    public void sendRequested(String text, boolean ragEnabled) {
        if (!model.canSend(text)) {
            return;
        }
        model.addUserMessage(text);
        model.beginAssistantMessage();
        start(text, new TurnListener());
    }

    /**
     * Eine Workflow-Aktion (roter Tag): im Verlauf steht ihr Name, der Agent bekommt den Auftrag dahinter. Muss auf
     * dem UI-Thread gerufen werden.
     */
    public void sendAction(String visibleText, String prompt) {
        if (!model.canSend(visibleText)) {
            return;
        }
        model.addUserMessage(visibleText);
        model.beginAssistantMessage();
        start(prompt, new TurnListener());
    }

    /** Ob gerade ein Auftrag läuft (die Workflow-Tags warten dann). */
    public boolean isBusy() {
        return agentService.isBusy();
    }

    private void start(String prompt, TurnListener listener) {
        try {
            AgentTurn turn = agentService.send(prompt, listener);
            if (!turn.isDone()) {
                runningTurn = turn;
            }
        } catch (RuntimeException e) {
            // Agent beschäftigt oder Agent-Modus geschlossen: sichtbar machen, nicht verschlucken.
            listener.closed = true;
            model.failAssistantMessage(agentService.isBusy()
                    ? "Der Agent arbeitet noch am vorherigen Auftrag."
                    : "Der Agent-Modus ist beendet.");
        }
    }

    /** Muss auf dem UI-Thread gerufen werden. */
    @Override
    public void stopRequested() {
        cancelBeforeStart = true;
        AgentTurn turn = runningTurn;
        if (turn != null) {
            turn.cancel();
        }
    }

    /** Für Menschen lesbare Meldung; enthält bewusst keine Details aus Agentenprozess oder SDK. */
    static String describe(AgentFailure failure) {
        if (failure == null) {
            return "Der Agent hat den Auftrag nicht abgeschlossen.";
        }
        switch (failure) {
            case START_FAILED:
                return "Der Agent konnte nicht gestartet werden.";
            case SESSION_FAILED:
                return "Der Agent hat keine Sitzung eröffnet.";
            case AGENT_TERMINATED:
                return "Der Agent wurde unerwartet beendet. Der nächste Auftrag startet ihn neu.";
            case CLOSED:
                return "Der Agent-Modus ist beendet.";
            case PROMPT_FAILED:
            default:
                return "Der Agent hat den Auftrag nicht abgeschlossen.";
        }
    }

    /** Leitet Callbacks eines Auftrags auf den UI-Thread; nach dem Abschluss wird nichts mehr weitergereicht. */
    private final class TurnListener implements AgentTurnListener {

        private volatile boolean closed;

        @Override
        public void onMessage(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    closeThinking();
                    model.appendAssistantDelta(text);
                }
            });
        }

        @Override
        public void onUtteranceStarted() {
            onUi(new Runnable() {
                @Override
                public void run() {
                    closeThinking();
                    model.beginAnswerSection();
                }
            });
        }

        /** Kennung der offenen Denkwolke für gestreamte Überlegungen (nur UI-Thread), sonst {@code null}. */
        private String thinkingKey;
        private int thoughtSeries;

        @Override
        public void onThought(final String text) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    if (thinkingKey == null || !model.isActivityRunning(thinkingKey)) {
                        thinkingKey = "thought-" + (++thoughtSeries);
                        model.startActivity(thinkingKey, "Überlegungen", "think", text);
                    } else {
                        model.appendActivityText(thinkingKey, text);
                    }
                }
            });
        }

        @Override
        public void onToolActivity(final AgentToolActivity activity) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    if (!model.hasActivity(activity.id())
                            && !AgentToolActivity.KIND_PLAN.equals(activity.kind())
                            && !AgentToolActivity.KIND_EVENT.equals(activity.kind())) {
                        // Erst ein neuer Werkzeugschritt beendet die Denkwolke; Updates eines bekannten (Recherche-
                        // Karte), Plan und Ereignisse dürfen gestreamte Überlegungen nicht auf viele Wolken verteilen.
                        closeThinking();
                    }
                    if (activity.status() == AgentToolActivity.Status.RUNNING) {
                        model.startActivity(activity.id(), activity.title(), activity.kind(), activity.input());
                    } else {
                        // Auch das abschließende Update kann Titel oder Inhalt ändern (ein Plan trägt jedes Mal
                        // den ganzen Plan); leere Felder lassen den bekannten Stand stehen.
                        model.startActivity(activity.id(), activity.title(), activity.kind(), activity.input());
                        boolean succeeded = activity.status() == AgentToolActivity.Status.COMPLETED;
                        // Ein Fehler zeigt immer eine Ergebniszeile, auch wenn der Agent keinen Text mitschickt.
                        model.finishActivity(activity.id(), succeeded, succeeded || !activity.output().trim().isEmpty()
                                ? activity.output() : FAILED_WITHOUT_TEXT);
                    }
                }
            });
        }

        /** Mit der nächsten Antwortzeile oder dem nächsten Werkzeug endet die Denkwolke. */
        private void closeThinking() {
            if (thinkingKey != null) {
                model.finishActivity(thinkingKey, true, "");
                thinkingKey = null;
            }
        }

        @Override
        public void onCompleted() {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.completeAssistantMessage();
                }
            });
        }

        @Override
        public void onCancelled() {
            finish(new Runnable() {
                @Override
                public void run() {
                    model.cancelAssistantMessage();
                }
            });
        }

        @Override
        public void onFailed(AgentFailure failure) {
            final String message = describe(failure);
            finish(new Runnable() {
                @Override
                public void run() {
                    model.failAssistantMessage(message);
                }
            });
        }

        private void finish(final Runnable terminal) {
            onUi(new Runnable() {
                @Override
                public void run() {
                    closed = true;
                    runningTurn = null;
                    terminal.run();
                }
            });
        }

        private void onUi(final Runnable update) {
            uiExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    if (!closed) {
                        update.run();
                    }
                }
            });
        }
    }
}

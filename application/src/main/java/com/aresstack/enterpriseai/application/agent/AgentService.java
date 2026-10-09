package com.aresstack.enterpriseai.application.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.acp.api.AcpConnectionState;
import com.aresstack.enterpriseai.acp.api.AcpException;
import com.aresstack.enterpriseai.acp.api.AcpSession;
import com.aresstack.enterpriseai.acp.api.AcpSessionState;
import com.aresstack.enterpriseai.acp.api.AgentProcessHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Use Case "Agent-Modus": spricht über den ACP-Port mit einem externen Agentenprozess.
 *
 * <p>Lebenszyklus: Der erste Auftrag startet den Agenten über den {@link AgentLauncher} (Prozess, ACP-Verbindung,
 * Session); weitere Aufträge laufen in derselben ACP-Session, so dass der Agent seinen Kontext behält. Höchstens
 * ein Auftrag läuft gleichzeitig. Ist der Agentenprozess weggefallen, startet der nächste Auftrag einen neuen
 * (in einer neuen Session, ohne den alten Kontext); einen automatischen Neustart ohne Nutzeraktion gibt es
 * nicht. {@link #endSession()} („Neuer Chat“) beendet Session, Verbindung und Prozess, der nächste Auftrag
 * startet frisch; {@link #close()} beendet sie endgültig.
 *
 * <p>Das Transkript ({@link #transcript()}) gehört nur diesem Service. Es ist von den Chat-Konversationen des
 * {@code ChatService} getrennt; beide Modi teilen keinen Zustand.
 *
 * <p>Start und Session-Anlage blockieren und laufen deshalb auf dem injizierten {@code startExecutor}; der
 * Prompt selbst streamt über den Callback-Thread des Adapters. Threadsicher, kein globaler Zustand.
 */
public final class AgentService {

    private final AgentLauncher launcher;
    private final Executor startExecutor;
    private final Object lock = new Object();
    /** Serialisiert Starts: ein abgebrochener Start und der nächste Auftrag starten keinen zweiten Prozess. */
    private final Object launchLock = new Object();
    private final List<AgentExchange> transcript = new ArrayList<AgentExchange>();

    private AgentStatus status = AgentStatus.NOT_STARTED;
    private AcpConnection connection;
    private AcpSession session;
    private AgentTurn running;
    private int runningIndex = -1;
    private boolean closed;

    /**
     * @param launcher      startet den Agentenprozess (Composition Root)
     * @param startExecutor führt Start und Prompt-Versand aus; darf nicht der UI-Thread sein
     */
    public AgentService(AgentLauncher launcher, Executor startExecutor) {
        if (launcher == null || startExecutor == null) {
            throw new IllegalArgumentException("launcher and startExecutor must not be null");
        }
        this.launcher = launcher;
        this.startExecutor = startExecutor;
    }

    public AgentStatus status() {
        synchronized (lock) {
            refreshStatus();
            return status;
        }
    }

    /** Kennung der aktuellen ACP-Session oder {@code null}, solange keine bereit ist. */
    public String sessionId() {
        synchronized (lock) {
            return session == null ? null : session.getSessionId();
        }
    }

    /** Das Agent-Transkript in Auftragsreihenfolge (unveränderliche Kopie). */
    public List<AgentExchange> transcript() {
        synchronized (lock) {
            return Collections.unmodifiableList(new ArrayList<AgentExchange>(transcript));
        }
    }

    public boolean isBusy() {
        synchronized (lock) {
            return running != null;
        }
    }

    /**
     * Schickt {@code text} an den Agenten und streamt dessen Antwort. Kehrt sofort zurück; ein noch nicht
     * laufender Agent wird zuerst gestartet.
     *
     * @throws IllegalStateException    wenn schon ein Auftrag läuft oder der Agent-Modus geschlossen ist
     * @throws IllegalArgumentException bei leerem Text
     */
    public AgentTurn send(String text, AgentTurnListener listener) {
        if (text == null || text.trim().isEmpty()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        final AgentTurn turn = new AgentTurn(this, text, listener);
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("agent mode is closed");
            }
            if (running != null) {
                throw new IllegalStateException("agent is busy");
            }
            running = turn;
            runningIndex = transcript.size();
            transcript.add(AgentExchange.running(text));
        }
        try {
            startExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    start(turn);
                }
            });
        } catch (RejectedExecutionException e) {
            turn.fail(AgentFailure.START_FAILED);
        }
        return turn;
    }

    /** Bricht den laufenden Auftrag ab, falls es einen gibt. */
    public void cancel() {
        AgentTurn turn;
        synchronized (lock) {
            turn = running;
        }
        if (turn != null) {
            turn.cancel();
        }
    }

    /**
     * Bricht einen laufenden Auftrag ab und beendet Session, Verbindung und Agentenprozess (und damit auch die
     * Tool-Endpunkte, die der Launcher dafür angelegt hat). Idempotent; danach nimmt der Service keine Aufträge
     * mehr an. Das Transkript bleibt lesbar.
     */
    public void close() {
        AgentTurn turn;
        AcpSession openSession;
        AcpConnection openConnection;
        synchronized (lock) {
            if (closed) {
                return;
            }
            closed = true;
            status = AgentStatus.CLOSED;
            turn = running;
            openSession = session;
            openConnection = connection;
            session = null;
            connection = null;
        }
        if (turn != null) {
            turn.cancel();
        }
        // Erst die Verbindung: Sie beendet jeden laufenden oder wartenden Prompt mit einem Terminal. Eine zuerst
        // geschlossene Session könnte den Prompt-Dispatcher des Adapters vorher abmelden.
        closeQuietly(openConnection);
        closeQuietly(openSession);
    }

    /**
     * „Neuer Chat“ im Agent-Modus: beendet Session, Verbindung und Agentenprozess (samt Tool-Endpunkten), ohne
     * den Service zu schließen; der nächste Auftrag startet einen frischen Agenten in einer neuen Session ohne
     * den alten Kontext. Das Transkript bleibt lesbar. Ohne Wirkung, wenn kein Agent läuft oder nach
     * {@link #close()}.
     *
     * @throws IllegalStateException solange ein Auftrag läuft (vorher {@link #cancel()} und abwarten)
     */
    public void endSession() {
        AcpSession openSession;
        AcpConnection openConnection;
        synchronized (lock) {
            if (closed) {
                return;
            }
            if (running != null) {
                throw new IllegalStateException("cannot end the agent session while a turn is running");
            }
            openSession = session;
            openConnection = connection;
            session = null;
            connection = null;
            status = AgentStatus.NOT_STARTED;
        }
        closeQuietly(openConnection);
        closeQuietly(openSession);
    }

    private void start(AgentTurn turn) {
        if (turn.isDone()) {
            return; // vor dem Start abgebrochen
        }
        AcpSession ready;
        try {
            ready = readySession();
        } catch (AcpException e) {
            turn.fail(e.getPhase() == AcpException.Phase.SESSION ? AgentFailure.SESSION_FAILED
                    : AgentFailure.START_FAILED);
            return;
        } catch (RuntimeException e) {
            turn.fail(AgentFailure.START_FAILED);
            return;
        }
        if (ready == null) {
            turn.fail(AgentFailure.CLOSED);
            return;
        }
        turn.dispatch(ready);
    }

    /** Liefert die bereite Session; startet den Agenten (neu), wenn keiner läuft. {@code null} nach close(). */
    private AcpSession readySession() throws AcpException {
        synchronized (launchLock) {
            AcpConnection stale;
            synchronized (lock) {
                if (closed) {
                    return null;
                }
                refreshStatus();
                if (status == AgentStatus.READY && session.getState() == AcpSessionState.ACTIVE) {
                    return session;
                }
                stale = connection;
                connection = null;
                session = null;
                status = AgentStatus.STARTING;
            }
            closeQuietly(stale); // gibt auch den alten Prozess und seine Tool-Endpunkte frei

            AcpConnection fresh = null;
            AcpSession freshSession;
            try {
                fresh = launcher.launch();
                freshSession = fresh.newSession();
            } catch (AcpException e) {
                startFailed(fresh);
                throw e;
            } catch (RuntimeException e) {
                startFailed(fresh);
                throw e;
            }
            synchronized (lock) {
                if (!closed) {
                    connection = fresh;
                    session = freshSession;
                    status = AgentStatus.READY;
                    return freshSession;
                }
            }
            closeQuietly(freshSession);
            closeQuietly(fresh); // während des Starts geschlossen: nichts weiterlaufen lassen
            return null;
        }
    }

    private void startFailed(AcpConnection half) {
        closeQuietly(half);
        synchronized (lock) {
            if (!closed) {
                status = AgentStatus.FAILED;
            }
        }
    }

    /** Ein Prompt ist gescheitert: lag es am weggefallenen Prozess oder am Auftrag? */
    AgentFailure failureAfterPrompt() {
        synchronized (lock) {
            refreshStatus();
            return status == AgentStatus.FAILED ? AgentFailure.AGENT_TERMINATED : AgentFailure.PROMPT_FAILED;
        }
    }

    /** Rückmeldung eines Auftrags beim Abschluss. */
    void turnFinished(AgentTurn turn, AgentExchange exchange) {
        synchronized (lock) {
            if (running != turn) {
                return;
            }
            transcript.set(runningIndex, exchange);
            running = null;
            runningIndex = -1;
        }
    }

    /**
     * Ein Agent, der zwischen zwei Aufträgen endet, ändert den Verbindungszustand im Adapter erst beim nächsten
     * Request; deshalb zählt auch der Prozess selbst, damit der nächste Auftrag gleich einen neuen startet.
     */
    private void refreshStatus() {
        if (status != AgentStatus.READY || connection == null) {
            return;
        }
        AgentProcessHandle process = connection.getProcess();
        if (connection.getState() != AcpConnectionState.READY || (process != null && !process.isAlive())) {
            status = AgentStatus.FAILED;
        }
    }

    private static void closeQuietly(AcpSession toClose) {
        if (toClose == null) {
            return;
        }
        try {
            toClose.close();
        } catch (RuntimeException ignored) {
            // best effort: ein Fehler beim Schließen darf das Schließen der Verbindung nicht verhindern
        }
    }

    private static void closeQuietly(AcpConnection toClose) {
        if (toClose == null) {
            return;
        }
        try {
            toClose.close();
        } catch (RuntimeException ignored) {
            // best effort
        }
    }
}

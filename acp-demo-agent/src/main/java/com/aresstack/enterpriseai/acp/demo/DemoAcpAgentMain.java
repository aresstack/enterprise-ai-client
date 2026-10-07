package com.aresstack.enterpriseai.acp.demo;

import com.agentclientprotocol.sdk.agent.SyncPromptContext;
import com.agentclientprotocol.sdk.agent.support.AcpAgentSupport;
import com.agentclientprotocol.sdk.agent.transport.StdioAcpAgentTransport;
import com.agentclientprotocol.sdk.annotation.AcpAgent;
import com.agentclientprotocol.sdk.annotation.Cancel;
import com.agentclientprotocol.sdk.annotation.Initialize;
import com.agentclientprotocol.sdk.annotation.NewSession;
import com.agentclientprotocol.sdk.annotation.Prompt;
import com.agentclientprotocol.sdk.spec.AcpSchema;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * External Java-8 demo ACP agent (STDIO transport). STDOUT carries ONLY the ACP protocol; every log line
 * goes to STDERR. On a prompt it streams a thought + several message chunks, then ends the turn; a prompt
 * containing "slow" spins until cancelled (cancel support). Unknown custom notifications are tolerated by
 * the SDK dispatcher and simply not handled here — they must never kill the process.
 *
 * <p>Test hooks (only for the adapter round-trip test): a prompt containing "crash" halts the JVM mid-turn
 * (agent process death); when {@value #EXIT_MARKER_ENV} names a file, the agent writes it on JVM exit so the
 * host can prove the child process really terminated after shutdown.</p>
 *
 * <p>Origin: Miguel0888/askai-java8, {@code acp-demo-agent} ({@code DemoAcpAgentMain}), package and agent
 * name adapted, unique session ids, crash and exit-marker hooks added.</p>
 */
@AcpAgent(name = "enterprise-ai-demo-agent", version = "0.1")
public final class DemoAcpAgentMain {

    /** Environment variable naming a file the agent creates when its JVM exits. */
    public static final String EXIT_MARKER_ENV = "ACP_DEMO_EXIT_MARKER";

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicInteger sessions = new AtomicInteger();

    public static void main(String[] args) {
        System.err.println("[demo-agent] starting");
        installExitMarker(System.getenv(EXIT_MARKER_ENV));
        AcpAgentSupport.create(new DemoAcpAgentMain())
                .transport(new StdioAcpAgentTransport())
                .build().run();
        System.err.println("[demo-agent] terminated");
    }

    private static void installExitMarker(final String path) {
        if (path == null || path.isEmpty()) {
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            public void run() {
                try {
                    new File(path).createNewFile();
                } catch (IOException ex) {
                    System.err.println("[demo-agent] exit marker not written: " + ex.getMessage());
                }
            }
        }, "demo-agent-exit-marker"));
    }

    @Initialize
    public AcpSchema.InitializeResponse initialize() {
        System.err.println("[demo-agent] initialize");
        return AcpSchema.InitializeResponse.ok();
    }

    @NewSession
    public AcpSchema.NewSessionResponse newSession() {
        System.err.println("[demo-agent] new session");
        return new AcpSchema.NewSessionResponse("demo-session-" + sessions.incrementAndGet(), null, null);
    }

    @Cancel
    public void cancel() {
        System.err.println("[demo-agent] cancel received");
        cancelled.set(true);
    }

    @Prompt
    public AcpSchema.PromptResponse prompt(SyncPromptContext ctx, AcpSchema.PromptRequest request) {
        cancelled.set(false);
        String text = request.text() == null ? "" : request.text();
        System.err.println("[demo-agent] prompt: " + text);
        if (text.contains("crash")) {
            ctx.sendMessage("about to crash");
            System.err.println("[demo-agent] crashing on purpose");
            Runtime.getRuntime().halt(3); // no shutdown hooks, no response: a hard process death mid-turn
        }
        ctx.sendThought("thinking about: " + text);
        for (int i = 1; i <= 3; i++) {
            if (cancelled.get()) {
                return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
            }
            ctx.sendMessage("chunk " + i + " for '" + text + "'");
        }
        if (text.contains("slow")) {
            // Spin (bounded) until cancel arrives, so cancel-during-streaming is testable without sleeps.
            for (int i = 0; i < 1_000_000 && !cancelled.get(); i++) {
                ctx.sendMessage("slow " + i);
                if (i > 3 && cancelled.get()) {
                    break;
                }
            }
            if (cancelled.get()) {
                return new AcpSchema.PromptResponse(AcpSchema.StopReason.CANCELLED);
            }
        }
        return AcpSchema.PromptResponse.endTurn();
    }
}

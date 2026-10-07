package com.aresstack.enterpriseai.mcp.solon;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools;

import org.junit.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regressionsschutz gegen "Anwendung hängt beim Beenden": Solon startet Nicht-Daemon-Threads (den
 * {@code HTTP-Dispatcher} des JDK-HttpServers und den {@code jdkhttp-N}-Worker-Pool), die
 * {@link SolonMcpServerRuntime#shutdown()} bewusst überleben. {@link SolonMcpServerRuntime#stopSharedServer()}
 * muss sie freigeben; {@link McpToolClient#close()} muss den Scheduler des Clients freigeben.
 *
 * <p>Läuft wegen {@code forkEvery = 1} in einer eigenen JVM; danach startet hier kein Solon mehr.
 * Herkunft: askai-java8 {@code SolonThreadDaemonDiagnosticTest}.
 */
public class SolonSharedServerShutdownTest {

    @Test
    public void clientCloseAndFinalServerStopReleaseAllNonDaemonThreads() throws Exception {
        SolonMcpServerRuntime runtime = new SolonMcpServerRuntime();
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("lifecycle", "Lifecycle"));
        runtime.updateTools(handle, Collections.singletonList(McpTestTools.ping()));

        Set<String> poolsBefore = aliveNonDaemonPoolThreads();
        McpToolClient client = new SolonMcpToolClientFactory(Duration.ofSeconds(10), Duration.ofSeconds(10))
                .connect(runtime.endpointUrl(handle), null);
        assertEquals("pong", client.callTool("ping", new HashMap<String, Object>()));
        final Set<String> clientPools = aliveNonDaemonPoolThreads();
        clientPools.removeAll(poolsBefore);
        client.close();
        client.close(); // idempotent
        awaitGone(new Condition() {
            @Override
            public boolean holds() {
                return !intersects(aliveNonDaemonPoolThreads(), clientPools);
            }
        });
        assertFalse("McpToolClient.close() must release the client's scheduler threads",
                intersects(aliveNonDaemonPoolThreads(), clientPools));

        runtime.unregisterEndpoint(handle);
        runtime.shutdown();
        assertTrue("shutdown() must keep the shared server (restart in one JVM is unreliable)",
                hasHttpDispatcher());

        SolonMcpServerRuntime.stopSharedServer();
        SolonMcpServerRuntime.stopSharedServer(); // idempotent
        awaitGone(new Condition() {
            @Override
            public boolean holds() {
                return !hasHttpDispatcher() && !hasJdkHttpWorker();
            }
        });
        assertFalse("stopSharedServer() must release the HTTP-Dispatcher", hasHttpDispatcher());
        assertFalse("stopSharedServer() must stop the jdkhttp worker pool", hasJdkHttpWorker());
    }

    private interface Condition {
        boolean holds();
    }

    private static void awaitGone(Condition condition) throws InterruptedException {
        for (int i = 0; i < 100 && !condition.holds(); i++) {
            Thread.sleep(100);
        }
    }

    private static boolean intersects(Set<String> a, Set<String> b) {
        Set<String> copy = new HashSet<String>(a);
        copy.retainAll(b);
        return !copy.isEmpty();
    }

    /**
     * Der Dispatcher heißt erst ab JDK 9 "HTTP-Dispatcher"; unter JDK 8 ist er ein namenloser "Thread-N".
     * Stabil über JDK 8 bis 21 ist sein Run-Loop-Frame {@code sun.net.httpserver.ServerImpl$Dispatcher}.
     */
    private static boolean hasHttpDispatcher() {
        for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
            Thread thread = entry.getKey();
            if (thread == null || !thread.isAlive() || thread.isDaemon()) {
                continue;
            }
            if ("HTTP-Dispatcher".equals(thread.getName())) {
                return true;
            }
            for (StackTraceElement frame : entry.getValue()) {
                if ("sun.net.httpserver.ServerImpl$Dispatcher".equals(frame.getClassName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasJdkHttpWorker() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread != null && thread.isAlive() && !thread.isDaemon() && thread.getName().startsWith("jdkhttp-")) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> aliveNonDaemonPoolThreads() {
        Set<String> names = new HashSet<String>();
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if (thread != null && thread.isAlive() && !thread.isDaemon() && thread.getName().startsWith("pool-")) {
                names.add(thread.getName());
            }
        }
        return names;
    }
}

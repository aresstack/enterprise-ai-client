package com.aresstack.enterpriseai.mcp.solon;

import org.noear.solon.Solon;
import org.noear.solon.core.Plugin;
import org.noear.solon.core.PluginEntity;

import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;

/**
 * Gibt den prozessglobalen Solon-Server endgültig frei. Zwei Nicht-Daemon-Thread-Familien halten die JVM
 * sonst am Leben: der {@code HTTP-Dispatcher} des JDK-HttpServers (freigegeben durch {@code Solon.stopBlock})
 * und der {@code jdkhttp-N}-Worker-Pool, den {@code JdkHttpPlugin.stop()} nie herunterfährt. Der Pool ist nur
 * über private Felder erreichbar und wird deshalb vor dem Stopp reflektiv gegriffen.
 *
 * <p>Herkunft: askai-java8 {@code SolonMcpServerRuntime.stopSharedServer()} (dort als Ursache des
 * Hängenbleibens der GUI beim Beenden nachgewiesen).
 */
final class SolonServerThreads {

    private SolonServerThreads() {
    }

    /** Aufrufer hält die Solon-Sperre. Idempotent, best effort. */
    static void stop() {
        if (Solon.app() == null) {
            return;
        }
        ExecutorService workers = findJdkHttpWorkerPool();
        try {
            Solon.stopBlock(false, 0); // false: keine App-Stop-Hooks, kein Verzögern
        } catch (Throwable ignored) {
            // best effort: die Anwendung beendet sich ohnehin
        }
        if (workers != null) {
            try {
                workers.shutdownNow();
            } catch (Throwable ignored) {
                // best effort
            }
        }
    }

    private static ExecutorService findJdkHttpWorkerPool() {
        try {
            for (PluginEntity entry : Solon.cfg().plugins()) {
                Plugin plugin = entry.getPlugin();
                if (plugin == null || !plugin.getClass().getName().endsWith(".JdkHttpPlugin")) {
                    continue;
                }
                Field serverField = plugin.getClass().getDeclaredField("_server");
                serverField.setAccessible(true);
                Object server = serverField.get(plugin);
                if (server == null) {
                    return null;
                }
                Field executorField = server.getClass().getDeclaredField("executor");
                executorField.setAccessible(true);
                Object executor = executorField.get(server);
                return executor instanceof ExecutorService ? (ExecutorService) executor : null;
            }
        } catch (Throwable ignored) {
            // Struktur nach einem Bibliotheks-Update anders: dann gibt es nichts Zusätzliches zu stoppen
        }
        return null;
    }
}

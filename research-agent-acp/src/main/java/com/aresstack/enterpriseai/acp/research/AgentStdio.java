package com.aresstack.enterpriseai.acp.research;

import java.io.BufferedOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * Die Prozessgrenze des Agenten kodierungsfest machen, bevor ACP und Logging die Ströme greifen:
 * <ul>
 *   <li>STDOUT (ACP) wird reines ASCII ({@link AsciiJsonOutputStream}); der Client liest es dann richtig, egal mit
 *   welcher Kodierung.</li>
 *   <li>STDERR (Logzeilen) wird in der Kodierung geschrieben, mit der der Client es liest: seine
 *   Standardkodierung, die er als {@value #STDERR_CHARSET_PROPERTY} mitgibt.</li>
 * </ul>
 */
final class AgentStdio {

    /** Die Standardkodierung des Clients; mit ihr liest das ACP-SDK STDERR des Agenten. */
    static final String STDERR_CHARSET_PROPERTY = "enterpriseai.agent.stderrCharset";

    private AgentStdio() {
    }

    static void install() {
        System.setOut(new PrintStream(new AsciiJsonOutputStream(
                new BufferedOutputStream(new FileOutputStream(FileDescriptor.out), 8192)), false));
        String charset = System.getProperty(STDERR_CHARSET_PROPERTY, "");
        if (charset.isEmpty() || !Charset.isSupported(charset)) {
            return;
        }
        for (Handler handler : Logger.getLogger("").getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                try {
                    handler.setEncoding(charset);
                } catch (Exception unsupported) {
                    // bleibt bei der Kodierung des Prozesses
                }
            }
        }
    }
}

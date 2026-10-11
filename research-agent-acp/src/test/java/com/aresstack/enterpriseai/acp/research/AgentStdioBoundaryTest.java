package com.aresstack.enterpriseai.acp.research;

import com.aresstack.enterpriseai.research.runtime.json.MiniJson;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Umlaute durch die echte Prozessgrenze: der Agent läuft wie im Client mit {@code -Dfile.encoding=UTF-8}, der
 * Client liest STDOUT und STDERR mit seiner Plattformkodierung (hier windows-1252 wie ein deutsches Windows). Der
 * Text kommt unverändert an, ohne Mojibake.
 */
public class AgentStdioBoundaryTest {

    static final String TEXT = "Grüße aus Ärger, Öl und Übermaß – IAR‑Übersicht …";
    static final String INPUT = "Wer arbeitet an der Rückschnittstelle für Anträge? Grüße";
    private static final Charset CLIENT = Charset.forName("windows-1252");

    @Test
    public void userTextJsonAndLogLinesCrossTheProcessBoundaryInBothDirectionsWithoutMojibake() throws Exception {
        List<String> command = new ArrayList<String>();
        command.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-D" + AgentStdio.STDERR_CHARSET_PROPERTY + "=" + CLIENT.name());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(AgentStdioProbe.class.getName());
        Process process = new ProcessBuilder(command).start();
        // Hinweg wie StdioAcpClientTransport: der Nutzertext geht als UTF-8-Bytes in STDIN des Agenten.
        process.getOutputStream().write((INPUT + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        process.getOutputStream().close();
        byte[] out = readAll(process.getInputStream());
        byte[] err = readAll(process.getErrorStream());
        assertTrue("Agent endet", process.waitFor(60, TimeUnit.SECONDS));

        String stdout = new String(out, CLIENT).trim();
        for (int i = 0; i < out.length; i++) {
            assertTrue("STDOUT ist ASCII", out[i] >= 0);
        }
        Map<?, ?> json = (Map<?, ?>) MiniJson.parse(stdout);
        assertEquals(TEXT, json.get("text"));
        assertEquals("Umlaute des Nutzers kommen im Agenten an", INPUT, json.get("echo"));

        String stderr = new String(err, CLIENT);
        assertTrue(stderr, stderr.contains("Log: Grüße aus Ärger, Öl und Übermaß"));
        assertFalse(stderr, stderr.contains("Ã"));
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int read; (read = in.read(buffer)) > 0; ) {
            bytes.write(buffer, 0, read);
        }
        return bytes.toByteArray();
    }
}

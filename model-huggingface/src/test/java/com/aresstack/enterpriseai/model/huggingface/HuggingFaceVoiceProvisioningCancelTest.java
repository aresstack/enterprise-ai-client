package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallCancelledException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoutePort;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Cancels a real HTTP transfer of huggingface4j against a local hub: the stream ends long before the file is
 * complete, nothing appears as installed, the partial file stays and a later install resumes it with a range request.
 */
public class HuggingFaceVoiceProvisioningCancelTest {

    private static final String REPOSITORY = "test/voice";
    private static final String FILE = "voice.onnx";
    private static final int SIZE = 8 * 1024 * 1024;
    private static final int CHUNK = 64 * 1024;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private HttpServer server;
    private final AtomicLong servedBytes = new AtomicLong();
    private final List<String> ranges = new CopyOnWriteArrayList<String>();
    private final ConcurrentHashMap<String, Boolean> aborted = new ConcurrentHashMap<String, Boolean>();
    private volatile long chunkPauseMillis = 5;

    @Before
    public void startHub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/models/" + REPOSITORY + "/tree/main", exchange -> {
            byte[] body = ("[{\"type\":\"file\",\"path\":\"" + FILE + "\",\"size\":" + SIZE + "}]")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/" + REPOSITORY + "/resolve/main/" + FILE, this::serveFile);
        server.start();
    }

    @After
    public void stopHub() {
        server.stop(0);
    }

    private void serveFile(HttpExchange exchange) throws IOException {
        String range = exchange.getRequestHeaders().getFirst("Range");
        ranges.add(range == null ? "" : range);
        long start = 0;
        if (range != null) {
            Matcher m = Pattern.compile("bytes=(\\d+)-").matcher(range);
            if (m.find()) {
                start = Long.parseLong(m.group(1));
            }
        }
        long length = SIZE - start;
        if (start > 0) {
            exchange.getResponseHeaders().add("Content-Range", "bytes " + start + "-" + (SIZE - 1) + "/" + SIZE);
            exchange.sendResponseHeaders(206, length);
        } else {
            exchange.sendResponseHeaders(200, length);
        }
        byte[] chunk = new byte[CHUNK];
        OutputStream out = exchange.getResponseBody();
        try {
            long sent = 0;
            while (sent < length) {
                int n = (int) Math.min(CHUNK, length - sent);
                out.write(chunk, 0, n);
                out.flush();
                sent += n;
                servedBytes.addAndGet(n);
                if (chunkPauseMillis > 0) {
                    Thread.sleep(chunkPauseMillis);
                }
            }
            out.close();
        } catch (IOException e) {
            aborted.put("aborted", Boolean.TRUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private HuggingFaceVoiceProvisioning provisioning() {
        HuggingFaceVoice voice = new HuggingFaceVoice("test-voice", "Test", "de", REPOSITORY, "main",
                Collections.singletonList(FILE));
        return new HuggingFaceVoiceProvisioning(Collections.singletonList(voice),
                new ConcurrentHashMap<String, HuggingFaceVoice>(),
                "http://127.0.0.1:" + server.getAddress().getPort(), HttpRoutePort.direct());
    }

    private static LocalVoiceInstallListener cancelAfter(final long bytes, final InstallCancellation cancellation) {
        return (file, number, count, done, total) -> {
            if (done >= bytes) {
                cancellation.cancel();
            }
        };
    }

    private static boolean installed(List<LocalVoiceOffer> offers) {
        for (LocalVoiceOffer offer : offers) {
            if ("test-voice".equals(offer.id())) {
                return offer.isInstalled();
            }
        }
        return false;
    }

    private static List<Path> partFiles(Path root) throws IOException {
        List<Path> parts = new ArrayList<Path>();
        try (java.util.stream.Stream<Path> walk = Files.walk(root)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".part")).forEach(parts::add);
        }
        return parts;
    }

    @Test
    public void cancelStopsTheRunningTransferAndLeavesAResumablePartialFile() throws Exception {
        Path root = tmp.newFolder("models").toPath();
        InstallCancellation cancellation = new InstallCancellation();
        long started = System.nanoTime();
        try {
            provisioning().install("test-voice", root, cancelAfter(CHUNK, cancellation), cancellation);
            fail("cancelled install must not complete");
        } catch (LocalVoiceInstallCancelledException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("abgebrochen"));
        }
        long millis = (System.nanoTime() - started) / 1000000L;
        // The full file takes > 600 ms at the server's pace; a stop after the first block must end far earlier.
        assertTrue("transfer ended early (" + millis + " ms)", millis < 500);
        assertTrue("served " + servedBytes.get() + " of " + SIZE, servedBytes.get() < SIZE);
        assertFalse("never visible as installed", Files.exists(root.resolve("test-voice")));
        assertFalse(installed(provisioning().offers(root)));
        List<Path> parts = partFiles(root);
        assertEquals("partial file kept for resume: " + parts, 1, parts.size());
        long partial = Files.size(parts.get(0));
        assertTrue("partial size " + partial, partial > 0 && partial < SIZE);

        chunkPauseMillis = 0;
        provisioning().install("test-voice", root, (file, number, count, done, total) -> { },
                new InstallCancellation());
        assertTrue(installed(provisioning().offers(root)));
        assertEquals(SIZE, Files.size(root.resolve("test-voice").resolve(FILE)));
        assertEquals("second request resumes", "bytes=" + partial + "-", ranges.get(ranges.size() - 1));
    }

    @Test
    public void cancelBeforeTheFirstRequestDownloadsNothing() throws Exception {
        Path root = tmp.newFolder("models").toPath();
        InstallCancellation cancellation = new InstallCancellation();
        cancellation.cancel();
        try {
            provisioning().install("test-voice", root, (file, number, count, done, total) -> { }, cancellation);
            fail("cancelled install must not complete");
        } catch (LocalVoiceInstallCancelledException expected) {
            // expected
        }
        assertTrue("no file request", ranges.isEmpty());
        assertFalse(installed(provisioning().offers(root)));
    }

    @Test
    public void lateCancelAfterCompletedInstallIsANoOp() throws Exception {
        Path root = tmp.newFolder("models").toPath();
        chunkPauseMillis = 0;
        provisioning().install("test-voice", root, (file, number, count, done, total) -> { },
                new InstallCancellation());
        InstallCancellation late = new InstallCancellation();
        late.cancel();
        provisioning().install("test-voice", root, (file, number, count, done, total) -> { }, late);
        assertTrue(installed(provisioning().offers(root)));
        assertEquals(SIZE, Files.size(root.resolve("test-voice").resolve(FILE)));
    }
}

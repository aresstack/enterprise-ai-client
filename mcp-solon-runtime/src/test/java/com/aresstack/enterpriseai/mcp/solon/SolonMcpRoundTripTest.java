package com.aresstack.enterpriseai.mcp.solon;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpToolCallException;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolClient;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolHandler;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;
import com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools;

import io.modelcontextprotocol.spec.McpSchema;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.noear.solon.ai.mcp.client.McpClientProvider;

import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * AP19: echter Solon-Client ↔ Solon-Server-Roundtrip über Streamable HTTP auf 127.0.0.1 (initialize,
 * tools/list, tools/call, Tools hinzufügen/entfernen mit {@code notifications/tools/list_changed}, falscher
 * Token, Shutdown) sowie die Sicherheitsregeln (nur Loopback, Token nie in der Konsole, keine Solon-Typen in
 * der öffentlichen API).
 */
public class SolonMcpRoundTripTest {

    private SolonMcpServerRuntime runtime;
    private final List<McpClientProvider> clients = new ArrayList<McpClientProvider>();

    @Before
    public void startRuntime() {
        runtime = new SolonMcpServerRuntime();
    }

    @After
    public void stopRuntime() {
        for (McpClientProvider client : clients) {
            client.close();
        }
        runtime.shutdown();
    }

    private McpClientProvider client(String url, final LinkedBlockingQueue<List<String>> toolListChanges) {
        McpClientProvider.Builder builder = McpClientProvider.builder()
                .url(url)
                .channel("streamable")
                .cacheSeconds(0)
                .initializationTimeout(Duration.ofSeconds(10))
                .requestTimeout(Duration.ofSeconds(10));
        if (toolListChanges != null) {
            builder.toolsChangeConsumer(new Function<List<McpSchema.Tool>, Mono<Void>>() {
                @Override
                public Mono<Void> apply(List<McpSchema.Tool> tools) {
                    List<String> names = new ArrayList<String>();
                    for (McpSchema.Tool tool : tools) {
                        names.add(tool.name());
                    }
                    toolListChanges.add(names);
                    return Mono.empty();
                }
            });
        }
        McpClientProvider client = builder.build();
        clients.add(client);
        return client;
    }

    private static List<String> names(McpClientProvider client) {
        List<String> names = new ArrayList<String>();
        for (org.noear.solon.ai.chat.tool.FunctionTool tool : client.getTools()) {
            names.add(tool.name());
        }
        return names;
    }

    private static String text(McpSchema.CallToolResult result) {
        StringBuilder text = new StringBuilder();
        for (McpSchema.Content content : result.content()) {
            text.append(((McpSchema.TextContent) content).text());
        }
        return text.toString();
    }

    /** Wartet auf eine tools/list_changed-Meldung, deren Tool-Liste genau {@code expected} ist. */
    private static void awaitToolList(LinkedBlockingQueue<List<String>> changes, List<String> expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10000;
        List<List<String>> seen = new ArrayList<List<String>>();
        while (System.currentTimeMillis() < deadline) {
            List<String> change = changes.poll(200, TimeUnit.MILLISECONDS);
            if (change == null) {
                continue;
            }
            seen.add(change);
            if (change.containsAll(expected) && expected.containsAll(change)) {
                return;
            }
        }
        fail("no tools/list_changed with " + expected + ", saw " + seen);
    }

    @Test
    public void realClientServerRoundTripWithDynamicToolsAndListChanged() throws Exception {
        // '#' und ':' sind URL-feindlich; die Route muss trotzdem funktionieren.
        McpEndpointHandle handle = runtime.registerEndpoint(
                new McpEndpointDefinition("knowledge#session:1", "Knowledge Tools"));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        String url = runtime.endpointUrl(handle);
        assertTrue(url, url.startsWith("http://127.0.0.1:" + runtime.getPort() + "/mcp/knowledge-session-1/"));

        LinkedBlockingQueue<List<String>> changes = new LinkedBlockingQueue<List<String>>();
        McpClientProvider client = client(url, changes);

        // initialize + tools/list
        assertEquals(Arrays.asList("ping", "echo"), names(client));
        assertTrue("initialize must have completed", client.getClient().isInitialized());
        assertEquals("Knowledge Tools", client.getClient().getServerInfo().name());

        // tools/call
        McpSchema.CallToolResult pong = client.callToolRequest("ping", new HashMap<String, Object>());
        assertFalse(Boolean.TRUE.equals(pong.isError()));
        assertEquals("pong", text(pong));
        McpSchema.CallToolResult echoed = client.callToolRequest("echo",
                Collections.<String, Object>singletonMap("text", "Grüße über MCP ✓"));
        assertEquals("Grüße über MCP ✓", text(echoed));

        // Tool hinzufügen → tools/list_changed
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo(), McpTestTools.add()));
        awaitToolList(changes, Arrays.asList("ping", "echo", "add"));
        Map<String, Object> summands = new HashMap<String, Object>();
        summands.put("a", 19);
        summands.put("b", 23);
        assertEquals("42", text(client.callToolRequest("add", summands)));

        // Tool entfernen → tools/list_changed, Aufruf des entfernten Tools scheitert kontrolliert
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.add()));
        awaitToolList(changes, Arrays.asList("ping", "add"));
        assertEquals(Arrays.asList("ping", "add"), names(client));
        try {
            McpSchema.CallToolResult gone = client.callToolRequest("echo",
                    Collections.<String, Object>singletonMap("text", "x"));
            assertTrue("removed tool must not be callable", Boolean.TRUE.equals(gone.isError()));
        } catch (RuntimeException expected) {
            // JSON-RPC-Fehler des Servers ist ebenso zulässig
        }

        // Shutdown: Endpoint weg, Client scheitert
        runtime.shutdown();
        runtime.shutdown();
        assertEquals(null, runtime.endpointUrl(handle));
        McpToolClient neutral = new SolonMcpToolClientFactory(Duration.ofSeconds(5), Duration.ofSeconds(5))
                .connect(url, null);
        try {
            neutral.callTool("ping", new HashMap<String, Object>());
            fail("endpoint must be gone after shutdown");
        } catch (McpToolCallException expected) {
            assertTrue(expected.isEndpointUnavailable());
        } finally {
            neutral.close();
        }
    }

    @Test
    public void clientSeesEnumValuesAndRequiredFieldsInTheToolSchema() {
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("schema", "Schema"));
        runtime.updateTools(handle, Collections.singletonList(McpToolContribution.of("search", "Suche",
                new McpToolHandler() {
                    @Override
                    public McpToolResult invoke(McpToolCall call) {
                        return McpToolResult.ok(call.getString("mode"));
                    }
                },
                McpToolParameter.enumeration("mode", true, "Modus", Arrays.asList("keyword", "semantic")))));
        McpClientProvider client = client(runtime.endpointUrl(handle), null);

        McpSchema.JsonSchema schema = client.getClient().listTools().block().tools().get(0).inputSchema();
        assertEquals(Collections.singletonList("mode"), schema.required());
        @SuppressWarnings("unchecked")
        Map<String, Object> mode = (Map<String, Object>) schema.properties().get("mode");
        assertEquals(Arrays.asList("keyword", "semantic"), mode.get("enum"));
        assertEquals("semantic", text(client.callToolRequest("search",
                Collections.<String, Object>singletonMap("mode", "semantic"))));
    }

    @Test
    public void unchangedToolsStayCallableWhileTheToolSetIsUpdated() throws Exception {
        final McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("busy", "Busy"));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        McpToolClient client = new SolonMcpToolClientFactory(Duration.ofSeconds(10), Duration.ofSeconds(10))
                .connect(runtime.endpointUrl(handle), null);
        final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean(true);
        Thread updater = new Thread(new Runnable() {
            @Override
            public void run() {
                boolean withEcho = false;
                while (running.get()) {
                    runtime.updateTools(handle, withEcho
                            ? Arrays.asList(McpTestTools.ping(), McpTestTools.echo())
                            : Collections.singletonList(McpTestTools.ping()));
                    withEcho = !withEcho;
                }
            }
        });
        updater.start();
        try {
            for (int i = 0; i < 200; i++) {
                assertEquals("pong", client.callTool("ping", new HashMap<String, Object>()));
            }
        } finally {
            running.set(false);
            updater.join();
            client.close();
        }
    }

    @Test
    public void unchangedToolsAreNotReinstalledOnUpdate() throws Exception {
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("stable", "Stable"));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        LinkedBlockingQueue<List<String>> changes = new LinkedBlockingQueue<List<String>>();
        McpClientProvider client = client(runtime.endpointUrl(handle), changes);
        assertEquals(Arrays.asList("ping", "echo"), names(client));

        // Neue Instanzen mit gleicher Beschreibung und gleichem Schema: das SDK ersetzt durch Entfernen und
        // Neuanlegen, das wäre eine Lücke. Erwartet: kein Eingriff in den Katalog, also kein tools/list_changed.
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo()));
        runtime.updateTools(handle, Collections.singletonList(McpTestTools.ping()));

        assertEquals(Collections.singletonList("ping"), changes.poll(10, TimeUnit.SECONDS));
        assertEquals(null, changes.poll(1, TimeUnit.SECONDS));
    }

    @Test
    public void reorderedToolSetKeepsTheRequestedOrder() {
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("order", "Order"));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.echo(), McpTestTools.add()));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.add(), McpTestTools.ping()));
        assertEquals(Arrays.asList("add", "ping"), runtime.toolNames(handle));
        runtime.updateTools(handle, Arrays.asList(McpTestTools.add(), McpTestTools.echo(), McpTestTools.ping()));
        assertEquals(Arrays.asList("add", "ping", "echo"), runtime.toolNames(handle));
    }

    @Test
    public void wrongTokenFindsNoRouteOnTheWire() throws Exception {
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("wire", "Wire"));
        runtime.updateTools(handle, Collections.singletonList(McpTestTools.ping()));
        String url = runtime.endpointUrl(handle);
        String wrong = url.substring(0, url.lastIndexOf('/') + 1) + "00112233445566778899aabbccddeeff0011223344556677";

        assertEquals(200, postInitialize(url));
        int status = postInitialize(wrong);
        assertTrue("wrong token must not be served, got HTTP " + status, status == 404 || status == 403);

        runtime.unregisterEndpoint(handle);
        int afterUnregister = postInitialize(url);
        assertTrue("unregistered token must not be served, got HTTP " + afterUnregister,
                afterUnregister == 404 || afterUnregister == 403);
    }

    @Test
    public void serverListensOnlyOnLoopback() throws Exception {
        McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("loopback", "Loopback"));
        int port = runtime.getPort();
        assertTrue("ephemeral port expected, got " + port, port > 1024);
        assertEquals(SolonMcpServerRuntime.LOOPBACK_HOST, new URL(runtime.endpointUrl(handle)).getHost());

        List<InetAddress> external = new ArrayList<InetAddress>();
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces != null && interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (!networkInterface.isUp() || networkInterface.isLoopback()) {
                continue;
            }
            for (InetAddress address : Collections.list(networkInterface.getInetAddresses())) {
                if (address instanceof Inet4Address && !address.isLoopbackAddress()) {
                    external.add(address);
                }
            }
        }
        Assume.assumeFalse("no non-loopback IPv4 interface to probe", external.isEmpty());
        for (InetAddress address : external) {
            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(address, port), 2000);
                fail("MCP server must not accept connections on " + address.getHostAddress());
            } catch (IOException expected) {
                // erwartet: nur 127.0.0.1 hört zu
            } finally {
                socket.close();
            }
        }
    }

    @Test
    public void tokensNeverReachTheConsole() throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream tee = new PrintStream(new TeeStream(captured, originalErr), true, "UTF-8");
        List<String> tokens = new ArrayList<String>();
        System.setOut(tee);
        System.setErr(tee);
        try {
            McpEndpointHandle handle = runtime.registerEndpoint(new McpEndpointDefinition("quiet", "Quiet"));
            tokens.add(handle.getToken());
            runtime.updateTools(handle, Arrays.asList(McpTestTools.ping(), McpTestTools.fail()));
            String url = runtime.endpointUrl(handle);
            McpToolClient client = new SolonMcpToolClientFactory(Duration.ofSeconds(5), Duration.ofSeconds(5))
                    .connect(url, null);
            try {
                client.callTool("ping", new HashMap<String, Object>());
                try {
                    client.callTool("fail", new HashMap<String, Object>());
                } catch (McpToolCallException expected) {
                    assertFalse(expected.getMessage().contains(handle.getToken()));
                }
                runtime.unregisterEndpoint(handle);
                try {
                    client.callTool("ping", new HashMap<String, Object>());
                } catch (McpToolCallException expected) {
                    assertFalse(expected.getMessage().contains(handle.getToken()));
                    assertFalse(expected.getMessage().contains(url));
                }
            } finally {
                client.close();
            }
            McpEndpointHandle second = runtime.registerEndpoint(new McpEndpointDefinition("quiet", "Quiet"));
            tokens.add(second.getToken());
            runtime.shutdown();
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
        String console = new String(captured.toByteArray(), StandardCharsets.UTF_8);
        for (String token : tokens) {
            assertFalse("token leaked to console:\n" + console, console.contains(token));
        }
    }

    @Test
    public void publicApiExposesNoSolonOrMcpSdkTypes() {
        for (Class<?> type : Arrays.<Class<?>>asList(SolonMcpServerRuntime.class, SolonMcpToolClientFactory.class)) {
            for (Method method : type.getMethods()) {
                assertNeutral(type, method.getReturnType());
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertNeutral(type, parameter);
                }
            }
            for (Constructor<?> constructor : type.getConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertNeutral(type, parameter);
                }
            }
            for (java.lang.reflect.Field field : type.getFields()) {
                assertTrue(field + " must be a constant", Modifier.isFinal(field.getModifiers()));
                assertNeutral(type, field.getType());
            }
        }
    }

    private static void assertNeutral(Class<?> owner, Class<?> type) {
        String name = type.getName();
        assertFalse(owner.getSimpleName() + " exposes " + name,
                name.startsWith("org.noear.") || name.startsWith("io.modelcontextprotocol.")
                        || name.startsWith("reactor."));
    }

    private static int postInitialize(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        byte[] body = ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
                + "\"protocolVersion\":\"2025-03-26\",\"capabilities\":{},"
                + "\"clientInfo\":{\"name\":\"raw-test\",\"version\":\"1\"}}}").getBytes(StandardCharsets.UTF_8);
        OutputStream out = connection.getOutputStream();
        try {
            out.write(body);
        } finally {
            out.close();
        }
        int status = connection.getResponseCode();
        InputStream in = status < 400 ? connection.getInputStream() : connection.getErrorStream();
        if (in != null) {
            in.close();
        }
        connection.disconnect();
        return status;
    }

    /** Schreibt in den Puffer und weiter auf die ursprüngliche Konsole. */
    private static final class TeeStream extends OutputStream {
        private final OutputStream first;
        private final OutputStream second;

        private TeeStream(OutputStream first, OutputStream second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public synchronized void write(int b) throws IOException {
            first.write(b);
            second.write(b);
        }

        @Override
        public synchronized void write(byte[] b, int off, int len) throws IOException {
            first.write(b, off, len);
            second.write(b, off, len);
        }
    }
}

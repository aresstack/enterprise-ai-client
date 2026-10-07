package com.aresstack.enterpriseai.mcp.solon;

import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.McpEndpointHandle;
import com.aresstack.enterpriseai.mcp.api.McpServerRegistry;
import com.aresstack.enterpriseai.mcp.api.McpToolCall;
import com.aresstack.enterpriseai.mcp.api.McpToolContribution;
import com.aresstack.enterpriseai.mcp.api.McpToolParameter;
import com.aresstack.enterpriseai.mcp.api.McpToolResult;

import io.modelcontextprotocol.spec.McpSchema;

import org.noear.solon.Solon;
import org.noear.solon.ai.chat.tool.FunctionTool;
import org.noear.solon.ai.chat.tool.FunctionToolDesc;
import org.noear.solon.ai.chat.tool.ToolHandler;
import org.noear.solon.ai.mcp.server.McpServerEndpointProvider;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Echter MCP-Streamable-HTTP-Server über Solon hinter dem Port {@link McpServerRegistry}.
 *
 * <p>Sicherheitsregeln: Der Server bindet ausschließlich an {@code 127.0.0.1} auf einem freien Port. Jeder
 * logische Endpoint bekommt einen eigenen Pfad {@code /mcp/<id>/<token>} mit einem nicht erratbaren Token
 * (192 bit aus {@link SecureRandom}); ein falscher Token trifft keine Route. Abmelden stoppt die Route und
 * invalidiert damit den Token. Token und Endpoint-URL werden nie geloggt. {@link #shutdown()} ist idempotent.
 *
 * <p>Solon selbst ist prozessglobal ({@code Solon.start}/{@code Solon.stopBlock}); ein Stopp mit
 * anschließendem Neustart in derselben JVM ist unzuverlässig. Deshalb bootet die erste Registrierung den
 * Solon-Server einmal je JVM, alle Instanzen teilen ihn, und {@link #shutdown()} entfernt nur die eigenen
 * Endpoints. Den Server selbst gibt {@link #stopSharedServer()} beim endgültigen Beenden der Anwendung frei.
 * Diese Klasse hält dafür keinen eigenen statischen Zustand; der Boot-Zustand wird bei Solon abgefragt.
 *
 * <p>Kein Solon-Typ verlässt diese Klasse; Aufrufer sehen nur {@link McpEndpointHandle} und die URL.
 *
 * <p>Herkunft: askai-java8 {@code SolonMcpServerRuntime}; geändert: kein statischer Port-Zustand,
 * Loopback-Prüfung eines bereits laufenden Solon, Neu-Registrierung stoppt die alte Route,
 * Tool-Fehler als MCP-{@code isError}-Ergebnis statt Exception, Token-Vergleich in konstanter Zeit.
 */
public final class SolonMcpServerRuntime implements McpServerRegistry {

    /** Der einzige Host, an den der Server bindet. */
    public static final String LOOPBACK_HOST = "127.0.0.1";

    private static final String CHANNEL_STREAMABLE = "streamable";
    private static final String SERVER_VERSION = "1.0";

    /** Serialisiert den JVM-weiten Solon-Boot und -Stopp (unveränderliches Sperrobjekt, kein Zustand). */
    private static final Object SOLON_LOCK = new Object();

    private final SecureRandom random;
    private final Map<String, Registration> registrations = new ConcurrentHashMap<String, Registration>();
    private volatile boolean shutdown;

    public SolonMcpServerRuntime() {
        this(new SecureRandom());
    }

    public SolonMcpServerRuntime(SecureRandom random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.random = random;
    }

    /**
     * Startet den gemeinsamen Solon-Server auf {@code 127.0.0.1:<freier Port>}, falls er noch nicht läuft.
     * Idempotent; wird von {@link #registerEndpoint(McpEndpointDefinition)} implizit aufgerufen.
     *
     * @throws IllegalStateException wenn bereits ein Solon-Server läuft, der nicht nur an Loopback bindet
     *                               oder einen Kontextpfad verwendet
     */
    public void start() {
        synchronized (SOLON_LOCK) {
            if (Solon.app() == null) {
                int port = freeLoopbackPort();
                Solon.start(SolonMcpServerRuntime.class, new String[] {
                        "--server.host=" + LOOPBACK_HOST,
                        "--server.port=" + port
                });
            }
            String host = Solon.cfg().serverHost();
            if (!LOOPBACK_HOST.equals(host)) {
                throw new IllegalStateException("Solon server is not bound to " + LOOPBACK_HOST
                        + "; refusing to expose MCP endpoints");
            }
            String contextPath = Solon.cfg().serverContextPath();
            if (contextPath != null && !contextPath.isEmpty() && !"/".equals(contextPath)) {
                // Endpoint-URLs enthalten keinen Kontextpfad; mit einem würden alle Clients 404 bekommen.
                throw new IllegalStateException("Solon server uses a context path; MCP endpoints need the root path");
            }
        }
    }

    /** @return der Port des laufenden Servers oder {@code -1}, wenn er nicht läuft. */
    public int getPort() {
        synchronized (SOLON_LOCK) {
            return Solon.app() == null ? -1 : Solon.cfg().serverPort();
        }
    }

    @Override
    public McpEndpointHandle registerEndpoint(McpEndpointDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        synchronized (this) {
            if (shutdown) {
                throw new IllegalStateException("runtime is shut down");
            }
            start();
            String token = newToken();
            // Endpoint-IDs dürfen URL-feindliche Zeichen enthalten (z. B. '#', das die Client-URL am Fragment
            // abschneidet); die Route verwendet deshalb eine bereinigte Form, die ID bleibt Registry-Schlüssel.
            String path = "/mcp/" + urlSafe(definition.getEndpointId()) + "/" + token;
            McpServerEndpointProvider provider = McpServerEndpointProvider.builder()
                    .name(definition.getDisplayName())
                    .version(SERVER_VERSION)
                    .channel(CHANNEL_STREAMABLE)
                    .mcpEndpoint(path)
                    .build();
            provider.postStart(); // hängt die Streamable-Route an den laufenden Solon-Server
            Registration previous = registrations.put(definition.getEndpointId(),
                    new Registration(provider, token, path));
            if (previous != null) {
                stopQuietly(previous);
            }
            return new McpEndpointHandle(definition.getEndpointId(), token);
        }
    }

    @Override
    public void updateTools(McpEndpointHandle handle, Collection<McpToolContribution> tools) {
        Registration registration = authorized(handle);
        if (registration == null) {
            return;
        }
        synchronized (registration) {
            // Alte Menge entfernen, neue hinzufügen; der Provider meldet jede Änderung als tools/list_changed.
            List<String> existing = new ArrayList<String>();
            for (FunctionTool tool : registration.provider.getTools()) {
                existing.add(tool.name());
            }
            for (String name : existing) {
                registration.provider.removeTool(name);
            }
            if (tools != null) {
                Map<String, McpToolContribution> unique = new LinkedHashMap<String, McpToolContribution>();
                for (McpToolContribution tool : tools) {
                    unique.put(tool.getName(), tool);
                }
                for (McpToolContribution tool : unique.values()) {
                    registration.provider.addTool(toFunctionTool(tool));
                }
            }
        }
    }

    @Override
    public void unregisterEndpoint(McpEndpointHandle handle) {
        Registration registration = authorized(handle);
        if (registration != null && registrations.remove(handle.getEndpointId(), registration)) {
            stopQuietly(registration);
        }
    }

    @Override
    public String endpointUrl(McpEndpointHandle handle) {
        Registration registration = authorized(handle);
        int port = getPort();
        return registration == null || port < 0 ? null : "http://" + LOOPBACK_HOST + ":" + port + registration.path;
    }

    @Override
    public List<String> toolNames(McpEndpointHandle handle) {
        return new ArrayList<String>(toolCatalog(handle).keySet());
    }

    @Override
    public Map<String, String> toolCatalog(McpEndpointHandle handle) {
        Map<String, String> catalog = new LinkedHashMap<String, String>();
        Registration registration = authorized(handle);
        if (registration != null) {
            synchronized (registration) {
                for (FunctionTool tool : registration.provider.getTools()) {
                    catalog.put(tool.name(), tool.description());
                }
            }
        }
        return catalog;
    }

    /**
     * Idempotent: stoppt alle Endpoints dieser Instanz (alle Tokens ungültig) und lehnt weitere
     * Registrierungen ab. Der gemeinsame Solon-Server läuft ohne Routen weiter; siehe {@link #stopSharedServer()}.
     */
    @Override
    public synchronized void shutdown() {
        if (shutdown) {
            return;
        }
        shutdown = true;
        for (Registration registration : registrations.values()) {
            stopQuietly(registration);
        }
        registrations.clear();
    }

    /**
     * Nur für das endgültige Beenden der JVM (Composition Root): stoppt den prozessglobalen Solon-Server samt
     * Worker-Pool, damit kein Nicht-Daemon-Thread die JVM am Leben hält. Danach in derselben JVM keinen neuen
     * Server mehr starten. Idempotent und best effort.
     */
    public static void stopSharedServer() {
        synchronized (SOLON_LOCK) {
            SolonServerThreads.stop();
        }
    }

    // ------------------------------------------------------------------ Hilfsfunktionen

    private Registration authorized(McpEndpointHandle handle) {
        if (shutdown || handle == null || handle.getEndpointId() == null || handle.getToken() == null) {
            return null;
        }
        Registration registration = registrations.get(handle.getEndpointId());
        return registration != null && sameToken(registration.token, handle.getToken()) ? registration : null;
    }

    private static boolean sameToken(String expected, String actual) {
        return MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /** Stoppt den Endpoint und entfernt seine Route, damit der alte Token-Pfad gar nicht mehr existiert. */
    private static void stopQuietly(Registration registration) {
        try {
            registration.provider.stop();
        } catch (RuntimeException ignored) {
            // best effort: die Registrierung ist bereits entfernt
        }
        synchronized (SOLON_LOCK) {
            try {
                if (Solon.app() != null) {
                    Solon.app().router().remove(registration.path);
                }
            } catch (RuntimeException ignored) {
                // best effort: der gestoppte Provider beantwortet die Route ohnehin nicht mehr
            }
        }
    }

    static FunctionTool toFunctionTool(final McpToolContribution tool) {
        FunctionToolDesc description = new FunctionToolDesc(tool.getName()).description(tool.getDescription());
        for (McpToolParameter parameter : tool.getParameters()) {
            description.paramAdd(parameter.getName(), javaType(parameter), parameter.isRequired(),
                    parameterDescription(parameter));
        }
        description.doHandle(new ToolHandler() {
            @Override
            public Object handle(Map<String, Object> arguments) {
                McpToolResult result;
                try {
                    result = tool.getHandler().invoke(new McpToolCall(tool.getName(), arguments));
                } catch (RuntimeException ex) {
                    // Generisch: die Meldung kann Anfragedaten oder Secrets enthalten.
                    result = McpToolResult.error("Tool failed.");
                }
                if (result == null) {
                    result = McpToolResult.error("Tool returned no result.");
                }
                // Solon reicht ein CallToolResult unverändert durch; so kommt isError beim Client an.
                return McpSchema.CallToolResult.builder()
                        .addTextContent(result.getText())
                        .isError(result.isError())
                        .build();
            }
        });
        return description;
    }

    private static Class<?> javaType(McpToolParameter parameter) {
        switch (parameter.getType()) {
            case INTEGER:
                return Long.class;
            case BOOLEAN:
                return Boolean.class;
            case STRING:
            case ENUM:
            default:
                return String.class;
        }
    }

    private static String parameterDescription(McpToolParameter parameter) {
        if (parameter.getEnumValues().isEmpty()) {
            return parameter.getDescription();
        }
        StringBuilder text = new StringBuilder(parameter.getDescription());
        text.append(text.length() == 0 ? "" : " ").append("One of: ");
        for (int i = 0; i < parameter.getEnumValues().size(); i++) {
            text.append(i == 0 ? "" : ", ").append(parameter.getEnumValues().get(i));
        }
        return text.toString();
    }

    /** Ersetzt jedes nicht pfadsichere Zeichen, damit IDs die Endpoint-URL nie verfälschen. */
    static String urlSafe(String endpointId) {
        StringBuilder safe = new StringBuilder(endpointId.length());
        for (int i = 0; i < endpointId.length(); i++) {
            char c = endpointId.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '-' || c == '_';
            safe.append(allowed ? c : '-');
        }
        return safe.toString();
    }

    private String newToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static int freeLoopbackPort() {
        ServerSocket socket = null;
        try {
            socket = new ServerSocket();
            socket.bind(new InetSocketAddress(LOOPBACK_HOST, 0));
            return socket.getLocalPort();
        } catch (IOException ex) {
            throw new IllegalStateException("could not allocate a loopback port", ex);
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // nichts zu tun
                }
            }
        }
    }

    private static final class Registration {
        private final McpServerEndpointProvider provider;
        private final String token;
        private final String path;

        private Registration(McpServerEndpointProvider provider, String token, String path) {
            this.provider = provider;
            this.token = token;
            this.path = path;
        }
    }
}

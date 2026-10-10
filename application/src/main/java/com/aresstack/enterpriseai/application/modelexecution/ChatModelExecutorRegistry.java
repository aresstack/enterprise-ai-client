package com.aresstack.enterpriseai.application.modelexecution;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.chat.api.ChatErrorKind;
import com.aresstack.enterpriseai.chat.api.ChatStreamListener;
import com.aresstack.enterpriseai.chat.api.ChatTask;
import com.aresstack.enterpriseai.chat.api.ResponsesPort;
import com.aresstack.enterpriseai.chat.api.ResponsesRequest;
import com.aresstack.enterpriseai.chat.api.ResponsesResult;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.domain.chat.ChatRequest;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;
import com.aresstack.enterpriseai.domain.modelcatalog.ModelReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Chat-Ausführung je Modellkatalog. Das Modell einer Anfrage ({@code ChatOptions.model}, {@code [<katalog>:]<modell>})
 * oder, ohne Angabe, die gespeicherte CHAT-Auswahl bestimmt den Katalog; die Anfrage geht mit der Modellkennung ohne
 * Präfix an dessen {@link ChatCompletionPort}. {@link #chat()} und {@link #responses()} sind die gerouteten Ports, die
 * der Rest der Anwendung benutzt. Werkzeug-Chat ({@code /responses}) gibt es nur für Kataloge, die ihn anbieten.
 */
public final class ChatModelExecutorRegistry {

    private final Map<String, ChatCompletionPort> chats;
    private final Map<String, ResponsesPort> responses;
    private final List<String> knownCatalogs;
    private final String defaultCatalogId;
    private final ModelReference selected;

    /**
     * @param chats            Chat-Port je Katalog
     * @param responses        Werkzeug-Chat je Katalog (Teilmenge)
     * @param knownCatalogs    alle Kataloge, deren Präfix eine Modellangabe tragen darf
     * @param defaultCatalogId Katalog einer Modellangabe ohne Präfix
     * @param selected         die gespeicherte CHAT-Auswahl oder {@code null}
     */
    public ChatModelExecutorRegistry(Map<String, ChatCompletionPort> chats, Map<String, ResponsesPort> responses,
                                     List<String> knownCatalogs, String defaultCatalogId, ModelReference selected) {
        if (chats == null || chats.isEmpty() || defaultCatalogId == null) {
            throw new IllegalArgumentException("chats and defaultCatalogId are required");
        }
        this.chats = Collections.unmodifiableMap(new LinkedHashMap<String, ChatCompletionPort>(chats));
        this.responses = responses == null ? Collections.<String, ResponsesPort>emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<String, ResponsesPort>(responses));
        List<String> known = new ArrayList<String>(this.chats.keySet());
        if (knownCatalogs != null) {
            for (String id : knownCatalogs) {
                if (!known.contains(id)) {
                    known.add(id);
                }
            }
        }
        this.knownCatalogs = Collections.unmodifiableList(known);
        this.defaultCatalogId = defaultCatalogId;
        this.selected = selected;
    }

    /** Der Chat-Port des Katalogs; {@link ChatCompletionException}, wenn der Katalog nicht chatten kann. */
    public ChatCompletionPort require(String catalogId) {
        ChatCompletionPort port = chats.get(catalogId);
        if (port == null) {
            throw unsupported(catalogId, "Chat");
        }
        return port;
    }

    /** Der geroutete Chat-Port. */
    public ChatCompletionPort chat() {
        return new ChatCompletionPort() {
            @Override
            public ChatResponse complete(ChatRequest request) {
                Target target = resolve(request.options());
                return require(target.catalogId).complete(rewrite(request, target));
            }

            @Override
            public ChatTask stream(ChatRequest request, ChatStreamListener listener) {
                Target target;
                try {
                    target = resolve(request.options());
                    require(target.catalogId);
                } catch (ChatCompletionException e) {
                    listener.onError(e);
                    return new FinishedTask();
                }
                return chats.get(target.catalogId).stream(rewrite(request, target), listener);
            }

            @Override
            public String toString() {
                return "RoutedChat" + chats.keySet();
            }
        };
    }

    /** Der geroutete Werkzeug-Chat ({@code /responses}). */
    public ResponsesPort responses() {
        return new ResponsesPort() {
            @Override
            public ResponsesResult create(ResponsesRequest request) {
                Target target = resolve(request.options());
                ResponsesPort port = responses.get(target.catalogId);
                if (port == null) {
                    throw unsupported(target.catalogId, "Werkzeug-Chat (Anhänge lesen)");
                }
                ChatOptions options = withModel(request.options(), target);
                if (options == request.options()) {
                    return port.create(request);
                }
                return port.create(request.isContinuation()
                        ? ResponsesRequest.continueWith(request.previousResponseId(), request.instructions(),
                        request.toolOutputs(), request.tools(), options)
                        : ResponsesRequest.start(request.instructions(), request.messages(), request.tools(), options));
            }

            @Override
            public String toString() {
                return "RoutedResponses" + responses.keySet();
            }
        };
    }

    /**
     * Ziel einer Anfrage: die Modellangabe, sonst die gespeicherte Auswahl, sonst der Standardkatalog mit dem
     * Standardmodell seines Adapters ({@code null}).
     */
    Target resolve(ChatOptions options) {
        ModelReference requested = options == null ? null
                : ModelReference.parse(options.model(), knownCatalogs, defaultCatalogId);
        if (requested != null) {
            return new Target(requested.catalogId(), requested.modelId());
        }
        if (selected != null) {
            return new Target(selected.catalogId(), selected.modelId());
        }
        return new Target(defaultCatalogId, null);
    }

    private static ChatRequest rewrite(ChatRequest request, Target target) {
        ChatOptions options = withModel(request.options(), target);
        return options == request.options() ? request : new ChatRequest(request.messages(), options);
    }

    private static ChatOptions withModel(ChatOptions options, Target target) {
        if (target.modelId == null || target.modelId.equals(options.model())) {
            return options;
        }
        return options.toBuilder().model(target.modelId).build();
    }

    /** Katalog und Modell (ohne Präfix; {@code null} = Standardmodell des Adapters). */
    static final class Target {
        final String catalogId;
        final String modelId;

        Target(String catalogId, String modelId) {
            this.catalogId = catalogId;
            this.modelId = modelId;
        }
    }

    /** Für einen sofort gemeldeten Fehler: schon abgeschlossen, Abbruch wirkungslos. */
    private static final class FinishedTask implements ChatTask {

        @Override
        public void cancel() {
        }

        @Override
        public boolean isDone() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private ChatCompletionException unsupported(String catalogId, String what) {
        return new ChatCompletionException(ChatErrorKind.INVALID_REQUEST, what + " ist für Modelle aus „" + catalogId
                + "“ nicht verfügbar (lokale Modelle brauchen Java 21 und das Sidecar-Jar; Werkzeug-Chat gibt es nur "
                + "über die Enterprise-API)");
    }

    @Override
    public String toString() {
        return "ChatModelExecutorRegistry[chat=" + chats.keySet() + ", responses=" + responses.keySet()
                + ", selected=" + selected + "]";
    }
}

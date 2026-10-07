package com.aresstack.enterpriseai.application.chat;

import com.aresstack.enterpriseai.chat.api.ChatCompletionException;
import com.aresstack.enterpriseai.domain.chat.ChatResponse;

/**
 * Beobachtet einen Chat-Turn. Nach beliebig vielen {@link #onDelta(String)} folgt genau ein Abschluss:
 * {@link #onCompleted}, {@link #onFailed} oder {@link #onCancelled}; danach kommt nichts mehr.
 *
 * <p>Aufrufe kommen vom Thread des Adapters bzw. von dem Thread, der abbricht, nie gleichzeitig. Der
 * Zustand des {@link ChatService} ist beim Aufruf bereits aktualisiert (z. B. steht die Antwort bei
 * {@code onCompleted} schon in der Historie). Implementierungen kehren schnell zurück und werfen nicht;
 * UI-Code wechselt selbst auf seinen Event-Thread.
 */
public interface ChatTurnListener {

    void onDelta(String text);

    void onCompleted(ChatResponse response);

    void onFailed(ChatCompletionException error);

    void onCancelled();
}

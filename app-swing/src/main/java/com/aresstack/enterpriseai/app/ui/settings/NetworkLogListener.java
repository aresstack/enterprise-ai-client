package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Empfänger von „Proxy auflösen“ und „HTTPS-Verbindung testen“: Protokollzeilen wie in AskAI
 * ({@code Resolving <url> ...}, Schritte, {@code Result: ...}) und zum Schluss genau einmal {@link #finished}.
 * Beide Methoden werden auf dem EDT gerufen.
 */
public interface NetworkLogListener {

    void line(String text);

    void finished(boolean success);
}

package com.aresstack.enterpriseai.acp.api;

/** One logical ACP session on a READY connection. Usable for multiple sequential prompts. */
public interface AcpSession {

    String getSessionId();

    AcpSessionState getState();

    PromptHandle prompt(String text, AcpUpdateListener listener);

    void close();
}

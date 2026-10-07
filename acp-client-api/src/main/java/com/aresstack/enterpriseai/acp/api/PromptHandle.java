package com.aresstack.enterpriseai.acp.api;

/** One prompt run. cancel() is idempotent and never terminates the process or the session by itself. */
public interface PromptHandle {

    String getPromptId();

    AcpPromptState getState();

    void cancel();
}

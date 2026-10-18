package com.aresstack.enterpriseai.proasteion.application.browser;

import java.io.IOException;

/** Remembers a user's "always" answer by adding the exact host to the configured allow or block list. */
public interface BrowserPolicyStore {

    void allow(String host) throws IOException;

    void block(String host) throws IOException;
}

package com.aresstack.enterpriseai.security.keepassrpc.archfixture.logging;

import java.util.logging.Logger;

/** Gegenprobe: java.util.logging in einem Adapter ist erlaubt. */
public final class AdapterWithJulLogger {

    private static final Logger LOG = Logger.getLogger(AdapterWithJulLogger.class.getName());

    public void run() {
        LOG.fine("paired");
    }
}

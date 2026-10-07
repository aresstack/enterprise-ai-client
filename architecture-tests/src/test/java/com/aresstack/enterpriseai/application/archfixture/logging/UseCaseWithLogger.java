package com.aresstack.enterpriseai.application.archfixture.logging;

import java.util.logging.Logger;

/** Absichtlicher Verstoß: Logging im Kern. */
public final class UseCaseWithLogger {

    private static final Logger LOG = Logger.getLogger(UseCaseWithLogger.class.getName());

    public void run(String secret) {
        LOG.info(secret);
    }
}

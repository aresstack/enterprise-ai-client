package com.aresstack.enterpriseai.proasteion.application.config;

import java.nio.file.Path;

/**
 * Global metadata catalog (#106): directory of the H2 database ({@code facts.directory}) and how long a writer waits
 * for a row locked by another writer ({@code facts.lockTimeoutMillis}). Both take effect at the next start.
 */
public final class FactStoreConfig {

    /** {@code facts.lockTimeoutMillis}: long enough for any single short transaction of the catalog. */
    public static final int DEFAULT_LOCK_TIMEOUT_MILLIS = 10000;

    private final Path directory;
    private final int lockTimeoutMillis;

    FactStoreConfig(Path directory, int lockTimeoutMillis) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory;
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    /** The defaults: {@code <app directory>/facts}, {@link #DEFAULT_LOCK_TIMEOUT_MILLIS}. */
    public static FactStoreConfig defaults() {
        return new FactStoreConfig(AppPaths.defaultFactDirectory(), DEFAULT_LOCK_TIMEOUT_MILLIS);
    }

    public Path directory() {
        return directory;
    }

    public int lockTimeoutMillis() {
        return lockTimeoutMillis;
    }

    @Override
    public String toString() {
        return "FactStoreConfig[directory=" + directory + ", lockTimeoutMillis=" + lockTimeoutMillis + "]";
    }
}

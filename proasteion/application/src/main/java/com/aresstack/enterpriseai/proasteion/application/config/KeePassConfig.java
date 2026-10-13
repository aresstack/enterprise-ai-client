package com.aresstack.enterpriseai.proasteion.application.config;

import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassRpcConfig;

import java.nio.file.Path;

/**
 * KeePassRPC als Security-Backend. Ist es deaktiviert, startet die Anwendung trotzdem; jede Secret-Auflösung
 * scheitert dann mit einer verständlichen Meldung. Der Pairing-Schlüssel liegt dauerhaft in
 * {@link #pairingKeyFile()} oder, wenn keine Datei konfiguriert ist, nur im Speicher des Prozesses.
 */
public final class KeePassConfig {

    /** {@code security.keepass.timeoutMillis}: Frist je KeePassRPC-Zugriff (Standard des Adapters). */
    public static final int DEFAULT_TIMEOUT_MILLIS = KeePassRpcConfig.DEFAULT_TIMEOUT_MILLIS;
    /** {@code security.keepass.retryCount}: Wiederholungen, wenn KeePassRPC vorübergehend nicht antwortet. */
    public static final int DEFAULT_RETRY_COUNT = 2;
    /** {@code security.keepass.retryDelayMillis}: Pause vor der ersten Wiederholung (300 ms, dann 750 ms). */
    public static final int DEFAULT_RETRY_DELAY_MILLIS = 300;
    /**
     * {@code security.keepass.setupRecheckCount}: wie oft der Einrichtungsassistent einen vorübergehend gescheiterten
     * Verbindungstest (KeePass oder KI-Dienst kurz nicht erreichbar) im Hintergrund von selbst wiederholt.
     */
    public static final int DEFAULT_SETUP_RECHECK_COUNT = 4;
    /** {@code security.keepass.setupRecheckDelayMillis}: Pause vor der ersten Wiederprüfung (2 s, 5 s, 12,5 s, 30 s). */
    public static final int DEFAULT_SETUP_RECHECK_DELAY_MILLIS = 2000;

    private final boolean enabled;
    private final KeePassRpcConfig rpc;
    private final Path pairingKeyFile;
    private final RetryPolicy retry;
    private final RetryPolicy setupRecheck;

    KeePassConfig(boolean enabled, KeePassRpcConfig rpc, Path pairingKeyFile, RetryPolicy retry,
                  RetryPolicy setupRecheck) {
        this.enabled = enabled;
        this.rpc = rpc;
        this.pairingKeyFile = pairingKeyFile;
        this.retry = retry == null ? RetryPolicy.none() : retry;
        this.setupRecheck = setupRecheck == null ? RetryPolicy.none() : setupRecheck;
    }

    public boolean enabled() {
        return enabled;
    }

    public KeePassRpcConfig rpc() {
        return rpc;
    }

    /** Adresse des KeePassRPC-Endpunkts als {@code host:port} (Pairing-Dialog, Meldungen). */
    public String address() {
        return rpc.host() + ":" + rpc.port();
    }

    /** Datei für den Pairing-Schlüssel oder {@code null} (nur im Speicher, nach jedem Start neues Pairing). */
    public Path pairingKeyFile() {
        return pairingKeyFile;
    }

    /**
     * {@code security.keepass.retryCount}/{@code security.keepass.retryDelayMillis}: Wiederholungen eines lesenden
     * Zugriffs, wenn KeePassRPC nicht erreichbar ist. Bewusst nicht in {@link #toString()}: eine geänderte Zahl
     * tauscht die KeePass-Anbindung nicht aus (kein neues Pairing), sie gilt ab dem nächsten Zugriff.
     */
    public RetryPolicy retry() {
        return retry;
    }

    /**
     * {@code security.keepass.setupRecheckCount}/{@code security.keepass.setupRecheckDelayMillis}: Wiederprüfungen des
     * Einrichtungsassistenten nach einem vorübergehenden Ausfall, mit dem Backoff von {@link RetryPolicy}. Fehlende
     * Einträge, abgelehnte Keys und abgebrochenes Pairing prüft der Assistent nie von selbst neu.
     */
    public RetryPolicy setupRecheck() {
        return setupRecheck;
    }

    @Override
    public String toString() {
        return "KeePassConfig[enabled=" + enabled + ", rpc=" + rpc + ", pairingKeyFile="
                + (pairingKeyFile == null ? "nur im Speicher" : pairingKeyFile) + "]";
    }
}

package com.aresstack.enterpriseai.app.config;

import com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcConfig;

import java.nio.file.Path;

/**
 * KeePassRPC als Security-Backend. Ist es deaktiviert, startet die Anwendung trotzdem; jede Secret-Auflösung
 * scheitert dann mit einer verständlichen Meldung. Der Pairing-Schlüssel liegt dauerhaft in
 * {@link #pairingKeyFile()} oder, wenn keine Datei konfiguriert ist, nur im Speicher des Prozesses.
 */
public final class KeePassConfig {

    private final boolean enabled;
    private final KeePassRpcConfig rpc;
    private final Path pairingKeyFile;

    KeePassConfig(boolean enabled, KeePassRpcConfig rpc, Path pairingKeyFile) {
        this.enabled = enabled;
        this.rpc = rpc;
        this.pairingKeyFile = pairingKeyFile;
    }

    public boolean enabled() {
        return enabled;
    }

    public KeePassRpcConfig rpc() {
        return rpc;
    }

    /** Datei für den Pairing-Schlüssel oder {@code null} (nur im Speicher, nach jedem Start neues Pairing). */
    public Path pairingKeyFile() {
        return pairingKeyFile;
    }

    @Override
    public String toString() {
        return "KeePassConfig[enabled=" + enabled + ", rpc=" + rpc + ", pairingKeyFile="
                + (pairingKeyFile == null ? "nur im Speicher" : pairingKeyFile) + "]";
    }
}

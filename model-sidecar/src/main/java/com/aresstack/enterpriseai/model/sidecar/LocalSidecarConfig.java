package com.aresstack.enterpriseai.model.sidecar;

import java.nio.file.Path;

/** Pfade des lokalen Sidecars: Java-21-Programm, Sidecar-Jar und Modellverzeichnis. */
public final class LocalSidecarConfig {

    private final Path javaExecutable;
    private final Path sidecarJar;
    private final Path modelRoot;
    private final int readyTimeoutMillis;

    /**
     * @param javaExecutable     {@code java} bzw. {@code java.exe} einer Java-21-Laufzeit
     * @param sidecarJar         {@code local-model-runtime-sidecar.jar}
     * @param modelRoot          Verzeichnis der lokal installierten Modelle
     * @param readyTimeoutMillis Wartezeit auf die Bereitschaftszeile
     */
    public LocalSidecarConfig(Path javaExecutable, Path sidecarJar, Path modelRoot, int readyTimeoutMillis) {
        if (javaExecutable == null || sidecarJar == null || modelRoot == null) {
            throw new IllegalArgumentException("javaExecutable, sidecarJar and modelRoot must not be null");
        }
        this.javaExecutable = javaExecutable;
        this.sidecarJar = sidecarJar;
        this.modelRoot = modelRoot;
        this.readyTimeoutMillis = readyTimeoutMillis > 0 ? readyTimeoutMillis : 60000;
    }

    public Path javaExecutable() {
        return javaExecutable;
    }

    public Path sidecarJar() {
        return sidecarJar;
    }

    public Path modelRoot() {
        return modelRoot;
    }

    public int readyTimeoutMillis() {
        return readyTimeoutMillis;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof LocalSidecarConfig)) {
            return false;
        }
        LocalSidecarConfig that = (LocalSidecarConfig) other;
        return javaExecutable.equals(that.javaExecutable) && sidecarJar.equals(that.sidecarJar)
                && modelRoot.equals(that.modelRoot) && readyTimeoutMillis == that.readyTimeoutMillis;
    }

    @Override
    public int hashCode() {
        return (javaExecutable.hashCode() * 31 + sidecarJar.hashCode()) * 31 + modelRoot.hashCode();
    }

    @Override
    public String toString() {
        return "LocalSidecarConfig[java=" + javaExecutable + ", jar=" + sidecarJar + ", models=" + modelRoot + "]";
    }
}

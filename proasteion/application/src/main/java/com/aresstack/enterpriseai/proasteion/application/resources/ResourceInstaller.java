package com.aresstack.enterpriseai.proasteion.application.resources;

import com.aresstack.enterpriseai.proasteion.application.net.HttpConnections;
import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoute;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Holt eine beschriebene Ressource geprüft: zuerst aus dem lokalen Bundle ({@link LocalResourceSource}), sonst per
 * Download über die Netzwerkeinstellungen (Weiterleitungen je Ziel neu geroutet). Geschrieben wird erst nach der
 * Prüfung von Größe und SHA-256, atomar. Merkt sich außerdem, welches Bundle (über seinen SHA-256) zuletzt
 * installiert wurde, damit ein geändertes Bundle erkennbar ist. Weiß nichts davon, wofür die Ressourcen dienen.
 */
public final class ResourceInstaller {

    /** Datei im Zielverzeichnis mit dem SHA-256 des zuletzt installierten Bundles. */
    static final String BUNDLE_MARKER = "resource-bundle.properties";
    private static final int MAX_REDIRECTS = 5;

    private static final Logger LOG = Logger.getLogger(ResourceInstaller.class.getName());

    /** Woher eine Ressource kam bzw. käme. */
    public enum Source {
        /** Schon installiert. */
        INSTALLED,
        /** Aus dem lokalen Ressourcen-Bundle. */
        ARCHIVE,
        /** Per Download. */
        DOWNLOAD
    }

    /** Wie das danebenliegende Bundle zum zuletzt installierten steht. */
    public enum BundleState {
        /** Kein Bundle da. */
        NONE,
        /** Ein Bundle liegt da, aus dem noch nie installiert wurde. */
        NEW,
        /** Genau dieses Bundle wurde schon installiert. */
        INSTALLED,
        /** Ein anderes Bundle als das zuletzt installierte. */
        CHANGED
    }

    /** Geprüfter Inhalt mit Herkunft. */
    public static final class Fetched {
        private final byte[] bytes;
        private final Source source;

        Fetched(byte[] bytes, Source source) {
            this.bytes = bytes;
            this.source = source;
        }

        public byte[] bytes() {
            return bytes;
        }

        public Source source() {
            return source;
        }
    }

    private final LocalResourceSource local;
    private final DownloadRetry retry;

    public ResourceInstaller(LocalResourceSource local) {
        this(local, DownloadRetry.once());
    }

    /** @param retry Wiederholung nach vorübergehenden Netzwerkfehlern */
    public ResourceInstaller(LocalResourceSource local, DownloadRetry retry) {
        this.local = local == null ? LocalResourceSource.none() : local;
        this.retry = retry == null ? DownloadRetry.once() : retry;
    }

    /** Ob die Ressource aus dem Bundle käme. */
    public boolean offersLocally(ResourceDescriptor resource) {
        return local.offers(resource);
    }

    /**
     * Der geprüfte Inhalt: aus dem Bundle, wenn es die Ressource genau so führt, sonst per Download. Blockiert.
     *
     * @param network die Netzwerkdienste für den Download oder {@code null} (dann nur das Bundle)
     */
    public Fetched fetch(ResourceDescriptor resource, NetworkServices network) throws IOException {
        byte[] bytes = null;
        try {
            bytes = local.read(resource);
        } catch (IOException | RuntimeException unreadable) {
            LOG.log(Level.WARNING, "Ressourcen-Bundle nicht lesbar, lade " + resource.file(), unreadable);
        }
        if (bytes != null) {
            return new Fetched(bytes, Source.ARCHIVE);
        }
        if (network == null || resource.url() == null) {
            throw new IOException(resource.file() + ": nicht im Ressourcen-Archiv und kein Download möglich");
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return new Fetched(downloadVerified(resource, network), Source.DOWNLOAD);
            } catch (IOException failed) {
                if (attempt >= retry.attempts() || !DownloadRetry.isTransient(failed)) {
                    throw failed;
                }
                LOG.log(Level.WARNING, resource.file() + ": Versuch " + attempt + " von " + retry.attempts()
                        + " fehlgeschlagen, neuer Versuch: " + failed);
                try {
                    Thread.sleep(retry.pauseBefore(attempt + 1));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException(resource.file() + ": Download unterbrochen", interrupted);
                }
            }
        }
    }

    private static byte[] downloadVerified(ResourceDescriptor resource, NetworkServices network) throws IOException {
        byte[] bytes = download(resource.url(), network);
        if (resource.size() >= 0 && bytes.length != resource.size()) {
            throw new IOException(resource.file() + ": " + bytes.length + " Bytes statt " + resource.size());
        }
        String actual = sha256(bytes);
        if (!actual.equals(resource.sha256())) {
            throw new IOException(resource.file() + ": SHA-256 stimmt nicht (" + actual + ")");
        }
        return bytes;
    }

    /** Merkt unter {@code root} den SHA-256 des Bundles, aus dem gerade installiert wurde. */
    public void recordBundle(Path root) throws IOException {
        String hash = local.bundleSha256();
        Path bundle = local.locate();
        if (hash == null || bundle == null) {
            return;
        }
        Properties marker = new Properties();
        marker.setProperty("sha256", hash);
        marker.setProperty("file", bundle.getFileName().toString());
        Files.createDirectories(root);
        writeProperties(root.resolve(BUNDLE_MARKER), marker);
    }

    /** Wie das gefundene Bundle zum unter {@code root} zuletzt installierten steht. */
    public BundleState bundleState(Path root) {
        String hash;
        try {
            hash = local.bundleSha256();
        } catch (IOException | RuntimeException unreadable) {
            return BundleState.NONE;
        }
        if (hash == null) {
            return BundleState.NONE;
        }
        Path marker = root.resolve(BUNDLE_MARKER);
        if (!Files.isRegularFile(marker)) {
            return BundleState.NEW;
        }
        Properties recorded = new Properties();
        try (InputStream in = Files.newInputStream(marker)) {
            recorded.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException unreadable) {
            return BundleState.NEW;
        }
        return hash.equals(recorded.getProperty("sha256", "").trim()) ? BundleState.INSTALLED : BundleState.CHANGED;
    }

    /** Schreibt Properties UTF-8 und atomar. */
    public static void writeProperties(Path target, Properties properties) throws IOException {
        ByteArrayOutputStream text = new ByteArrayOutputStream();
        try (Writer writer = new java.io.OutputStreamWriter(text, StandardCharsets.UTF_8)) {
            properties.store(writer, null);
        }
        atomicWrite(target, text.toByteArray());
    }

    /** Schreibt über eine Nachbardatei und benennt sie dann um. */
    public static void atomicWrite(Path target, byte[] content) throws IOException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try (OutputStream out = Files.newOutputStream(tmp)) {
            out.write(content);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException atomicUnsupported) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static String sha256(byte[] bytes) throws IOException {
        MessageDigest digest = sha256Digest();
        return hex(digest.digest(bytes));
    }

    static String sha256(Path file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest sha256Digest() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 nicht verfügbar", e);
        }
    }

    private static String hex(byte[] hash) {
        StringBuilder hex = new StringBuilder(hash.length * 2);
        for (byte b : hash) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }

    private static byte[] download(URI url, NetworkServices network) throws IOException {
        URI target = url;
        for (int hop = 0; hop <= MAX_REDIRECTS; hop++) {
            HttpRoute route = network.routes().routeFor(target);
            HttpURLConnection connection = HttpConnections.open(target, route, network.tls(), network.userAgent());
            try {
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(60000);
                connection.setInstanceFollowRedirects(false);
                int code = connection.getResponseCode();
                if (code >= 300 && code < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("HTTP " + code + " ohne Ziel: " + target.getHost());
                    }
                    target = target.resolve(location);
                    continue;
                }
                if (code != HttpURLConnection.HTTP_OK) {
                    String message = "HTTP " + code + " von " + target.getHost();
                    throw DownloadRetry.TransientHttpException.applies(code)
                            ? new DownloadRetry.TransientHttpException(message) : new IOException(message);
                }
                try (InputStream in = connection.getInputStream()) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, read);
                    }
                    return out.toByteArray();
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("zu viele Weiterleitungen: " + url.getHost());
    }
}

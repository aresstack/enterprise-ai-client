package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.api.LocalVoiceProvisioning;
import com.aresstack.huggingface.hub.HuggingFaceHub;
import com.aresstack.huggingface.hub.HuggingFaceHubException;
import com.aresstack.huggingface.hub.download.DownloadProgress;
import com.aresstack.huggingface.hub.download.DownloadResult;
import com.aresstack.huggingface.hub.download.ProgressListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * {@link LocalVoiceProvisioning} über den Hugging Face Hub mit huggingface4j: lädt die Dateien einer kuratierten
 * Stimme anonym (öffentliche Repositories wie {@code rhasspy/piper-voices}) nach
 * {@code <Modellverzeichnis>/.<id>.download}, setzt abgebrochene Downloads fort ({@code .part}), prüft große
 * Dateien gegen den SHA-256 aus {@code X-Linked-ETag}, wenn der Hub ihn liefert, und verschiebt den fertigen Ordner
 * nach {@code <Modellverzeichnis>/<id>}. Erst dann sieht ihn der Sidecar.
 */
public final class HuggingFaceVoiceProvisioning implements LocalVoiceProvisioning {

    private static final String STAGING_SUFFIX = ".download";

    private final List<HuggingFaceVoice> voices;
    private final HttpRoutePort routes;

    /**
     * @param voices die kuratierten Stimmen aus der Konfiguration
     * @param routes Proxy-Route je Ziel (die Route der Anwendung über win-proxy-java)
     */
    public HuggingFaceVoiceProvisioning(List<HuggingFaceVoice> voices, HttpRoutePort routes) {
        if (voices == null || routes == null) {
            throw new IllegalArgumentException("voices and routes must not be null");
        }
        this.voices = Collections.unmodifiableList(new ArrayList<HuggingFaceVoice>(voices));
        this.routes = routes;
    }

    @Override
    public List<LocalVoiceOffer> offers(Path modelRoot) {
        List<LocalVoiceOffer> offers = new ArrayList<LocalVoiceOffer>();
        for (HuggingFaceVoice voice : voices) {
            offers.add(new LocalVoiceOffer(voice.id(), voice.displayName(), voice.language(),
                    complete(modelRoot.resolve(voice.id()), voice)));
        }
        return offers;
    }

    @Override
    public void install(String voiceId, Path modelRoot, final LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        HuggingFaceVoice voice = find(voiceId);
        Path target = modelRoot.resolve(voice.id());
        if (complete(target, voice)) {
            return;
        }
        download(voice, modelRoot, target, listener);
    }

    /** Lädt alle Dateien neu; die bisherige Stimme bleibt nutzbar, bis die neuen Dateien geprüft da sind. */
    @Override
    public void update(String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        HuggingFaceVoice voice = find(voiceId);
        download(voice, modelRoot, modelRoot.resolve(voice.id()), listener);
    }

    private void download(HuggingFaceVoice voice, Path modelRoot, Path target,
                          final LocalVoiceInstallListener listener) throws LocalVoiceInstallException {
        Path staging = modelRoot.resolve("." + voice.id() + STAGING_SUFFIX);
        try {
            Files.createDirectories(staging);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Modellverzeichnis nicht beschreibbar: " + modelRoot + " ("
                    + e.getMessage() + ")", e);
        }
        HuggingFaceHub hub = HuggingFaceHub.standard().anonymous().proxySelector(new RouteProxySelector(routes))
                .build();
        for (String file : voice.files()) {
            final String name = HuggingFaceVoice.fileName(file);
            Path destination = staging.resolve(name);
            DownloadResult result;
            try {
                result = hub.models().model(voice.repository()).file(file).revision(voice.revision())
                        .downloadTo(destination).resume(true).overwrite(false)
                        .onProgress(new ProgressListener() {
                            @Override
                            public void onProgress(DownloadProgress progress) {
                                listener.progress(name, progress.getBytesDownloaded(), progress.getTotalBytes());
                            }
                        }).execute();
            } catch (HuggingFaceHubException e) {
                throw new LocalVoiceInstallException("Download von " + name + " fehlgeschlagen: " + e.getMessage(), e);
            } catch (RuntimeException e) {
                throw new LocalVoiceInstallException("Download von " + name + " fehlgeschlagen: " + e, e);
            }
            verify(destination, result);
        }
        moveIntoPlace(staging, target);
        if (!complete(target, voice)) {
            throw new LocalVoiceInstallException("Stimme " + voice.id() + " ist nach dem Download unvollständig.");
        }
    }

    /** Nur Ordner der angebotenen Stimmen; Dateien darin werden gelöscht, Unterordner gibt es nicht. */
    @Override
    public void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        HuggingFaceVoice voice = find(voiceId);
        Path target = modelRoot.resolve(voice.id());
        if (!Files.isDirectory(target)) {
            return;
        }
        try {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(target)) {
                for (Path file : files) {
                    Files.delete(file);
                }
            }
            Files.delete(target);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Stimme " + voice.id() + " ließ sich nicht entfernen: "
                    + e.getMessage(), e);
        }
    }

    private HuggingFaceVoice find(String voiceId) throws LocalVoiceInstallException {
        for (HuggingFaceVoice voice : voices) {
            if (voice.id().equals(voiceId)) {
                return voice;
            }
        }
        throw new LocalVoiceInstallException("Unbekannte Stimme: " + voiceId);
    }

    /** Alle Dateien der Stimme liegen nicht leer im Ordner. */
    static boolean complete(Path directory, HuggingFaceVoice voice) {
        if (!Files.isDirectory(directory)) {
            return false;
        }
        try {
            for (String file : voice.files()) {
                Path present = directory.resolve(HuggingFaceVoice.fileName(file));
                if (!Files.isRegularFile(present) || Files.size(present) == 0) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** SHA-256 gegen {@code X-Linked-ETag} (Git-LFS-Dateien); kleine Git-Dateien haben nur eine SHA-1 als ETag. */
    private static void verify(Path file, DownloadResult result) throws LocalVoiceInstallException {
        String etag = result.getEtag();
        if (etag == null || result.isSkipped()) {
            return;
        }
        String expected = etag.replace("W/", "").replace("\"", "").trim().toLowerCase(Locale.ROOT);
        if (!expected.matches("[0-9a-f]{64}")) {
            return;
        }
        String actual = sha256(file);
        if (!expected.equals(actual)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // die Meldung unten reicht
            }
            throw new LocalVoiceInstallException("Prüfsumme von " + file.getFileName() + " stimmt nicht (erwartet "
                    + expected + ", erhalten " + actual + "); Datei verworfen.");
        }
    }

    private static String sha256(Path file) throws LocalVoiceInstallException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new LocalVoiceInstallException("Prüfsumme von " + file.getFileName() + " nicht lesbar: "
                    + e.getMessage(), e);
        }
    }

    private static void moveIntoPlace(Path staging, Path target) throws LocalVoiceInstallException {
        try {
            if (!Files.exists(target)) {
                Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
                return;
            }
            // Ein unvollständiger Ordner von früher: Dateien einzeln ersetzen.
            try (DirectoryStream<Path> files = Files.newDirectoryStream(staging)) {
                for (Path file : files) {
                    Files.move(file, target.resolve(file.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Files.delete(staging);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Stimme konnte nicht nach " + target + " verschoben werden: "
                    + e.getMessage(), e);
        }
    }
}

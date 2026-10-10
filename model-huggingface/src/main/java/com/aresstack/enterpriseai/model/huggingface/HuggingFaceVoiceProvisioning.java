package com.aresstack.enterpriseai.model.huggingface;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.api.LocalVoiceProvisioning;
import com.aresstack.huggingface.hub.HuggingFaceHub;
import com.aresstack.huggingface.hub.HuggingFaceHubException;
import com.aresstack.huggingface.hub.download.DownloadProgress;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link LocalVoiceProvisioning} über den Hugging Face Hub mit huggingface4j: bietet die kuratierten Stimmen, die
 * schon installierten und die Treffer der Hub-Suche ({@link HubVoiceSearch}, nur Formate, die der Client ohne
 * Umwandlung lädt) an. Installieren lädt die Dateien anonym nach {@code <Modellverzeichnis>/.<id>.download},
 * setzt abgebrochene Downloads fort ({@code .part}), prüft zuerst die kleine Konfigurationsdatei
 * ({@link VoiceConfigCheck}) und jede Datei gegen Größe und SHA-256 aus den Hub-Metadaten ({@link HubFileIndex}),
 * legt die Herkunft ab ({@link VoiceSource}) und verschiebt den fertigen Ordner nach
 * {@code <Modellverzeichnis>/<id>}. Erst dann sieht ihn die Sprachausgabe.
 */
public final class HuggingFaceVoiceProvisioning implements LocalVoiceProvisioning {

    private static final String STAGING_SUFFIX = ".download";

    private final List<HuggingFaceVoice> voices;
    private final Map<String, HuggingFaceVoice> discovered;
    private final String endpoint;
    private final HttpRoutePort routes;

    /**
     * @param voices     die kuratierten Stimmen aus der Konfiguration
     * @param discovered Treffer früherer Suchen (vom Aufrufer gehalten, wird hier ergänzt; thread-sicher)
     * @param endpoint   Basis-URL des Hubs aus der Konfiguration (leer: nur Anzeigen und Entfernen gehen)
     * @param routes     Proxy-Route je Ziel (die Route der Anwendung über win-proxy-java)
     */
    public HuggingFaceVoiceProvisioning(List<HuggingFaceVoice> voices, Map<String, HuggingFaceVoice> discovered,
                                        String endpoint, HttpRoutePort routes) {
        if (voices == null || discovered == null || endpoint == null || routes == null) {
            throw new IllegalArgumentException("voices, discovered, endpoint and routes must not be null");
        }
        this.voices = Collections.unmodifiableList(new ArrayList<HuggingFaceVoice>(voices));
        this.discovered = discovered;
        this.endpoint = endpoint.trim();
        this.routes = routes;
    }

    /** Kuratierte Stimmen, danach alle anderen installierten (mit Herkunftsdatei). */
    @Override
    public List<LocalVoiceOffer> offers(Path modelRoot) {
        List<LocalVoiceOffer> offers = new ArrayList<LocalVoiceOffer>();
        Set<String> listed = new HashSet<String>();
        for (HuggingFaceVoice voice : voices) {
            offers.add(offer(voice, modelRoot));
            listed.add(voice.id());
        }
        for (HuggingFaceVoice voice : installed(modelRoot)) {
            if (listed.add(voice.id())) {
                offers.add(offer(voice, modelRoot));
            }
        }
        return offers;
    }

    @Override
    public List<LocalVoiceOffer> search(String query, Path modelRoot) throws LocalVoiceInstallException {
        requireEndpoint();
        List<HuggingFaceVoice> found;
        try {
            found = new HubVoiceSearch(hub(new RouteProxySelector(routes))).search(query);
        } catch (HuggingFaceHubException e) {
            throw new LocalVoiceInstallException("Suche auf dem Hugging Face Hub fehlgeschlagen: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new LocalVoiceInstallException("Suche auf dem Hugging Face Hub fehlgeschlagen: " + e, e);
        }
        List<LocalVoiceOffer> offers = new ArrayList<LocalVoiceOffer>();
        for (HuggingFaceVoice voice : found) {
            discovered.put(voice.id(), voice);
            offers.add(offer(voice, modelRoot));
        }
        return offers;
    }

    @Override
    public void install(String voiceId, Path modelRoot, final LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        HuggingFaceVoice voice = find(voiceId, modelRoot);
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
        HuggingFaceVoice voice = find(voiceId, modelRoot);
        download(voice, modelRoot, modelRoot.resolve(voice.id()), listener);
    }

    private LocalVoiceOffer offer(HuggingFaceVoice voice, Path modelRoot) {
        return new LocalVoiceOffer(voice.id(), voice.displayName(), voice.language(),
                complete(modelRoot.resolve(voice.id()), voice));
    }

    /** Installierte Stimmen mit Herkunftsdatei, nach Ordnername. */
    private static List<HuggingFaceVoice> installed(Path modelRoot) {
        List<HuggingFaceVoice> installed = new ArrayList<HuggingFaceVoice>();
        if (!Files.isDirectory(modelRoot)) {
            return installed;
        }
        List<Path> directories = new ArrayList<Path>();
        try (DirectoryStream<Path> children = Files.newDirectoryStream(modelRoot)) {
            for (Path child : children) {
                if (Files.isDirectory(child) && !child.getFileName().toString().startsWith(".")) {
                    directories.add(child);
                }
            }
        } catch (IOException e) {
            return installed;
        }
        Collections.sort(directories);
        for (Path directory : directories) {
            HuggingFaceVoice voice = VoiceSource.read(directory);
            if (voice != null) {
                installed.add(voice);
            }
        }
        return installed;
    }

    private HuggingFaceHub hub(RouteProxySelector proxies) {
        return HuggingFaceHub.standard().endpoint(endpoint).anonymous().proxySelector(proxies).build();
    }

    private void requireEndpoint() throws LocalVoiceInstallException {
        if (endpoint.isEmpty()) {
            throw new LocalVoiceInstallException("Keine Hugging-Face-Adresse konfiguriert (hub.endpoint).");
        }
    }

    private void download(HuggingFaceVoice voice, Path modelRoot, Path target,
                          final LocalVoiceInstallListener listener) throws LocalVoiceInstallException {
        requireEndpoint();
        Path staging = modelRoot.resolve("." + voice.id() + STAGING_SUFFIX);
        try {
            Files.createDirectories(staging);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Modellverzeichnis nicht beschreibbar: " + modelRoot + " ("
                    + e.getMessage() + ")", e);
        }
        RouteProxySelector proxies = new RouteProxySelector(routes);
        HuggingFaceHub hub = hub(proxies);
        HubFileIndex index = new HubFileIndex(endpoint, proxies);
        for (String file : configFirst(voice.files())) {
            final String name = HuggingFaceVoice.fileName(file);
            Path destination = staging.resolve(name);
            HubFileIndex.Entry expected = expected(index, voice, file);
            try {
                hub.models().model(voice.repository()).file(file).revision(voice.revision())
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
            verify(destination, expected);
            VoiceConfigCheck.check(destination);
        }
        try {
            VoiceSource.write(voice, staging);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Herkunft der Stimme nicht speicherbar: " + e.getMessage(), e);
        }
        moveIntoPlace(staging, target);
        if (!complete(target, voice)) {
            throw new LocalVoiceInstallException("Stimme " + voice.id() + " ist nach dem Download unvollständig.");
        }
    }

    /** Nur Ordner der angebotenen Stimmen; Dateien darin werden gelöscht, Unterordner gibt es nicht. */
    @Override
    public void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        HuggingFaceVoice voice = find(voiceId, modelRoot);
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

    /** Kuratiert, dann Suchtreffer, dann die Herkunftsdatei im installierten Ordner. */
    private HuggingFaceVoice find(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        for (HuggingFaceVoice voice : voices) {
            if (voice.id().equals(voiceId)) {
                return voice;
            }
        }
        HuggingFaceVoice found = discovered.get(voiceId);
        if (found == null && voiceId != null && voiceId.matches("[A-Za-z0-9._-]+") && !voiceId.startsWith(".")) {
            found = VoiceSource.read(modelRoot.resolve(voiceId));
        }
        if (found == null) {
            throw new LocalVoiceInstallException("Unbekannte Stimme: " + voiceId);
        }
        return found;
    }

    /** Konfigurationsdateien (JSON) zuerst: passt die Stimme nicht, wird das große Modell gar nicht geladen. */
    private static List<String> configFirst(List<String> files) {
        List<String> ordered = new ArrayList<String>();
        for (String file : files) {
            if (file.endsWith(".json")) {
                ordered.add(file);
            }
        }
        for (String file : files) {
            if (!file.endsWith(".json")) {
                ordered.add(file);
            }
        }
        return ordered;
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

    /** Größe und SHA-256 der Datei laut Hub; ohne Eintrag stimmt der Pfad der Konfiguration nicht. */
    private static HubFileIndex.Entry expected(HubFileIndex index, HuggingFaceVoice voice, String file)
            throws LocalVoiceInstallException {
        HubFileIndex.Entry entry;
        try {
            entry = index.entry(voice.repository(), voice.revision(), file);
        } catch (IOException | RuntimeException e) {
            throw new LocalVoiceInstallException("Metadaten von " + HuggingFaceVoice.fileName(file)
                    + " nicht abrufbar: " + e.getMessage(), e);
        }
        if (entry == null) {
            throw new LocalVoiceInstallException("Datei " + file + " fehlt im Repository " + voice.repository()
                    + " (Revision " + voice.revision() + ").");
        }
        return entry;
    }

    /** Größe immer, SHA-256 bei LFS-Dateien; bei Abweichung tatsächliche Werte melden und Datei verwerfen. */
    private static void verify(Path file, HubFileIndex.Entry expected) throws LocalVoiceInstallException {
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new LocalVoiceInstallException("Größe von " + file.getFileName() + " nicht lesbar: "
                    + e.getMessage(), e);
        }
        String actual = expected.sha256 == null ? null : sha256(file);
        if (size == expected.size && (expected.sha256 == null || expected.sha256.equals(actual))) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // die Meldung unten reicht
        }
        StringBuilder message = new StringBuilder("Prüfsumme von ").append(file.getFileName())
                .append(" stimmt nicht (erwartet ");
        if (expected.sha256 != null) {
            message.append("SHA-256 ").append(expected.sha256).append(", ");
        }
        message.append(expected.size).append(" Bytes; erhalten ");
        if (actual != null) {
            message.append("SHA-256 ").append(actual).append(", ");
        }
        message.append(size).append(" Bytes); Datei verworfen.");
        throw new LocalVoiceInstallException(message.toString());
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

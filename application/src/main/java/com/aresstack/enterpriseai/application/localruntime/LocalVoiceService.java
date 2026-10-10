package com.aresstack.enterpriseai.application.localruntime;

import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.api.LocalVoiceProvisioning;

import java.nio.file.Path;
import java.util.List;

/**
 * Lokale Stimmen für die Sprachausgabe anzeigen, installieren, aktualisieren und entfernen. Ausgewählt wird eine installierte Stimme wie
 * jedes andere Modell über die Katalog-Kategorie TTS; der Sidecar meldet sie unter ihrer Kennung. Blockiert.
 */
public final class LocalVoiceService {

    private final LocalVoiceProvisioning provisioning;

    public LocalVoiceService(LocalVoiceProvisioning provisioning) {
        if (provisioning == null) {
            throw new IllegalArgumentException("provisioning must not be null");
        }
        this.provisioning = provisioning;
    }

    public List<LocalVoiceOffer> offers(Path modelRoot) {
        requireRoot(modelRoot);
        return provisioning.offers(modelRoot);
    }

    public void install(String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        requireRoot(modelRoot);
        if (voiceId == null || voiceId.trim().isEmpty()) {
            throw new IllegalArgumentException("voiceId must not be blank");
        }
        provisioning.install(voiceId.trim(), modelRoot, listener == null ? new LocalVoiceInstallListener() {
            @Override
            public void progress(String file, long done, long total) {
                // niemand hört zu
            }
        } : listener);
    }

    public void update(String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        requireRoot(modelRoot);
        if (voiceId == null || voiceId.trim().isEmpty()) {
            throw new IllegalArgumentException("voiceId must not be blank");
        }
        provisioning.update(voiceId.trim(), modelRoot, listener == null ? new LocalVoiceInstallListener() {
            @Override
            public void progress(String file, long done, long total) {
                // niemand hört zu
            }
        } : listener);
    }

    public void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        requireRoot(modelRoot);
        if (voiceId == null || voiceId.trim().isEmpty()) {
            throw new IllegalArgumentException("voiceId must not be blank");
        }
        provisioning.remove(voiceId.trim(), modelRoot);
    }

    private static void requireRoot(Path modelRoot) {
        if (modelRoot == null) {
            throw new IllegalArgumentException("modelRoot must not be null");
        }
    }
}

package com.aresstack.enterpriseai.app.settings;

import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;

import java.nio.file.Path;
import java.util.List;

/**
 * Lokale Stimmen für den Abschnitt „Lokale Stimmen“ der Sprachausgabe; produktiv verdrahtet
 * {@code app.composition.LocalVoices} den Anwendungsfall mit der Netzroute des Entwurfs. Beide Methoden blockieren.
 */
public interface LocalVoiceInstaller {

    List<LocalVoiceOffer> offers(Path modelRoot);

    /** @param keePass KeePass-Abschnitt des Entwurfs für die Proxy-Anmeldung (BASIC) oder {@code null} */
    void install(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException;
}

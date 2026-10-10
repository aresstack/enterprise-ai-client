package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.LocalVoicesConfig;
import com.aresstack.enterpriseai.app.net.HttpRoutes;
import com.aresstack.enterpriseai.app.settings.LocalVoiceInstaller;
import com.aresstack.enterpriseai.application.localruntime.LocalVoiceService;
import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoiceProvisioning;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;

/**
 * Verdrahtet die Stimmenversorgung des Sidecars: kuratierte Stimmen aus {@link LocalVoicesConfig},
 * {@link HuggingFaceVoiceProvisioning} (huggingface4j) mit der Proxy-Route aus den Netzwerkeinstellungen des
 * Entwurfs, davor der Anwendungsfall {@link LocalVoiceService}. TLS: huggingface4j nimmt keine eigene
 * {@code SSLSocketFactory} an und vertraut deshalb den JVM-Zertifikaten, nicht der TrustPolicy der Anwendung.
 */
public final class LocalVoices implements LocalVoiceInstaller {

    /** Für die reine Anzeige wird nichts geladen; eine Route wird dabei nie gebraucht. */
    private static final HttpRoutePort NO_NETWORK = new HttpRoutePort() {
        @Override
        public HttpRoute routeFor(URI target) {
            return HttpRoute.unavailable("listing-only", "listing installed voices needs no network");
        }
    };

    private final List<HuggingFaceVoice> curated;

    public LocalVoices() {
        this(LocalVoicesConfig.curated());
    }

    LocalVoices(List<HuggingFaceVoice> curated) {
        this.curated = curated;
    }

    @Override
    public List<LocalVoiceOffer> offers(Path modelRoot) {
        return new LocalVoiceService(new HuggingFaceVoiceProvisioning(curated, NO_NETWORK)).offers(modelRoot);
    }

    @Override
    public void install(AppConfig config, String voiceId, Path modelRoot, LocalVoiceInstallListener listener)
            throws LocalVoiceInstallException {
        HttpRoutes routes = HttpRoutes.from(config.network());
        new LocalVoiceService(new HuggingFaceVoiceProvisioning(curated, routes)).install(voiceId, modelRoot, listener);
    }
}

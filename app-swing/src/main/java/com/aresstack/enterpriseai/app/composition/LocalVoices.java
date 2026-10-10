package com.aresstack.enterpriseai.app.composition;

import com.aresstack.enterpriseai.app.config.KeePassConfig;
import com.aresstack.enterpriseai.app.config.LocalVoicesConfig;
import com.aresstack.enterpriseai.app.config.ProxyAuthMode;
import com.aresstack.enterpriseai.app.config.NetworkConfig;
import com.aresstack.enterpriseai.app.net.HttpRoutes;
import com.aresstack.enterpriseai.app.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.app.security.ProxyAuthenticator;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.app.settings.LocalVoiceInstaller;
import com.aresstack.enterpriseai.application.localruntime.LocalVoiceService;
import com.aresstack.enterpriseai.domain.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.http.api.HttpRoute;
import com.aresstack.enterpriseai.http.api.HttpRoutePort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoiceProvisioning;
import com.aresstack.enterpriseai.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    /** Ob im Prozess schon eine Proxy-Anmeldung läuft (vom Start oder von einem früheren Download). */
    private final AtomicBoolean proxyAuthenticated = new AtomicBoolean();

    public LocalVoices() {
        this(LocalVoicesConfig.curated());
    }

    LocalVoices(List<HuggingFaceVoice> curated) {
        this.curated = curated;
    }

    /** Der Start hat den {@link ProxyAuthenticator} aus der gespeicherten Konfiguration gesetzt. */
    public void proxyAuthenticationInstalled() {
        proxyAuthenticated.set(true);
    }

    /**
     * Proxy-Anmeldung BASIC mit den Einstellungen des Entwurfs, nur wenn der Prozess noch keine hat: Beim Erststart
     * hat {@code EnterpriseAiClientMain} den {@link ProxyAuthenticator} noch nicht gesetzt; ohne ihn scheitert der
     * Download am Proxy mit 407. Eine laufende Anmeldung bleibt unangetastet (kein Entwurf ersetzt sie, ein
     * Speicher-Pairing bleibt gültig); geänderte Zugangsdaten greifen wie bisher nach dem Neustart.
     */
    private void installProxyAuthentication(NetworkConfig network, KeePassConfig keePass) {
        if (network.proxyAuthMode() != ProxyAuthMode.BASIC || network.proxyCredentialRef() == null
                || keePass == null || !keePass.enabled() || !proxyAuthenticated.compareAndSet(false, true)) {
            return;
        }
        String address = keePass.rpc().host() + ":" + keePass.rpc().port();
        KeePassPairingKeyStore keyStore = keePass.pairingKeyFile() == null
                ? new InMemoryPairingKeyStore()
                : new FilePairingKeyStore(keePass.pairingKeyFile());
        ProxyAuthenticator.install(AdapterAssembly.secrets(keePass, new SwingPairingCallback(address), keyStore),
                network.proxyCredentialRef());
    }

    @Override
    public List<LocalVoiceOffer> offers(Path modelRoot) {
        return new LocalVoiceService(new HuggingFaceVoiceProvisioning(curated, NO_NETWORK)).offers(modelRoot);
    }

    @Override
    public void install(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot,
                        LocalVoiceInstallListener listener) throws LocalVoiceInstallException {
        installProxyAuthentication(network, keePass);
        HttpRoutes routes = HttpRoutes.from(network);
        new LocalVoiceService(new HuggingFaceVoiceProvisioning(curated, routes)).install(voiceId, modelRoot, listener);
    }

    @Override
    public void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        new LocalVoiceService(new HuggingFaceVoiceProvisioning(curated, NO_NETWORK)).remove(voiceId, modelRoot);
    }
}

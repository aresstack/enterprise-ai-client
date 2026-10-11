package com.aresstack.enterpriseai.proasteion.application.composition;

import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.proasteion.application.config.KeePassConfig;
import com.aresstack.enterpriseai.proasteion.application.config.LocalVoicesConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ProxyAuthMode;
import com.aresstack.enterpriseai.proasteion.application.config.NetworkConfig;
import com.aresstack.enterpriseai.proasteion.application.net.HttpRoutes;
import com.aresstack.enterpriseai.proasteion.application.security.FilePairingKeyStore;
import com.aresstack.enterpriseai.proasteion.application.security.ProxyAuthenticator;
import com.aresstack.enterpriseai.proasteion.application.settings.LocalVoiceInstaller;
import com.aresstack.enterpriseai.astu.acropolis.localruntime.LocalVoiceService;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoute;
import com.aresstack.enterpriseai.proasteion.platform.network.HttpRoutePort;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallException;
import com.aresstack.enterpriseai.model.api.LocalVoiceInstallListener;
import com.aresstack.enterpriseai.model.huggingface.HubRepositorySuggestion;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoice;
import com.aresstack.enterpriseai.model.huggingface.HuggingFaceVoiceProvisioning;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.InMemoryPairingKeyStore;
import com.aresstack.enterpriseai.proasteion.platform.security.keepassrpc.KeePassPairingKeyStore;

import java.net.URI;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verdrahtet die Versorgung mit lokalen Stimmen: kuratierte Stimmen aus {@link LocalVoicesConfig},
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
    private final List<HubRepositorySuggestion> suggested;
    private final String hubEndpoint;
    /** Treffer der Hub-Suchen dieses Prozesses, damit „Installieren“ sie über die Kennung findet. */
    private final Map<String, HuggingFaceVoice> discovered = new ConcurrentHashMap<String, HuggingFaceVoice>();
    /** Ob im Prozess schon eine Proxy-Anmeldung läuft (vom Start oder von einem früheren Download). */
    private final AtomicBoolean proxyAuthenticated = new AtomicBoolean();
    /** Pairing- und Zugangsdaten-Eingabe für die Proxy-Anmeldung beim Download (von der Oberfläche). */
    private final UserPrompts prompts;

    public LocalVoices(UserPrompts prompts) {
        this(LocalVoicesConfig.curated(), LocalVoicesConfig.suggestions(), LocalVoicesConfig.hubEndpoint(), prompts);
    }

    LocalVoices(List<HuggingFaceVoice> curated, String hubEndpoint) {
        this(curated, Collections.<HubRepositorySuggestion>emptyList(), hubEndpoint, new RefusingPrompts());
    }

    LocalVoices(List<HuggingFaceVoice> curated, List<HubRepositorySuggestion> suggested, String hubEndpoint) {
        this(curated, suggested, hubEndpoint, new RefusingPrompts());
    }

    LocalVoices(List<HuggingFaceVoice> curated, List<HubRepositorySuggestion> suggested, String hubEndpoint,
                UserPrompts prompts) {
        if (prompts == null) {
            throw new IllegalArgumentException("prompts must not be null");
        }
        this.curated = curated;
        this.suggested = suggested;
        this.hubEndpoint = hubEndpoint;
        this.prompts = prompts;
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
        ProxyAuthenticator.install(AdapterAssembly.secrets(keePass, prompts.pairing(address), keyStore,
                        prompts.credentials()),
                network.proxyCredentialRef());
    }

    @Override
    public List<LocalVoiceOffer> offers(Path modelRoot) {
        return new LocalVoiceService(provisioning(NO_NETWORK)).offers(modelRoot);
    }

    @Override
    public List<LocalVoiceOffer> search(NetworkConfig network, KeePassConfig keePass, String query,
                                        ModelCategory category, Path modelRoot) throws LocalVoiceInstallException {
        installProxyAuthentication(network, keePass);
        return new LocalVoiceService(provisioning(HttpRoutes.from(network))).search(query, category, modelRoot);
    }

    @Override
    public void install(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot,
                        LocalVoiceInstallListener listener) throws LocalVoiceInstallException {
        installProxyAuthentication(network, keePass);
        HttpRoutes routes = HttpRoutes.from(network);
        new LocalVoiceService(provisioning(routes)).install(voiceId, modelRoot, listener);
    }

    @Override
    public void update(NetworkConfig network, KeePassConfig keePass, String voiceId, Path modelRoot,
                       LocalVoiceInstallListener listener) throws LocalVoiceInstallException {
        installProxyAuthentication(network, keePass);
        HttpRoutes routes = HttpRoutes.from(network);
        new LocalVoiceService(provisioning(routes)).update(voiceId, modelRoot, listener);
    }

    @Override
    public void remove(String voiceId, Path modelRoot) throws LocalVoiceInstallException {
        new LocalVoiceService(provisioning(NO_NETWORK)).remove(voiceId, modelRoot);
    }

    private HuggingFaceVoiceProvisioning provisioning(HttpRoutePort routes) {
        return new HuggingFaceVoiceProvisioning(curated, suggested, discovered, hubEndpoint, routes);
    }
}

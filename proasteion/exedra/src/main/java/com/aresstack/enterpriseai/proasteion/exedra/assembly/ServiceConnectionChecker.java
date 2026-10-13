package com.aresstack.enterpriseai.proasteion.exedra.assembly;

import com.aresstack.enterpriseai.proasteion.application.composition.AdapterAssembly;

import com.aresstack.enterpriseai.astu.acropolis.speech.TextAnalysisService;
import com.aresstack.enterpriseai.proasteion.application.composition.ModelCatalogs;
import com.aresstack.enterpriseai.proasteion.application.config.AppConfig;
import com.aresstack.enterpriseai.proasteion.application.config.ModelsConfig;
import com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt;
import com.aresstack.enterpriseai.proasteion.application.security.KeePassAccess;
import com.aresstack.enterpriseai.proasteion.exedra.security.DialogCredentialPrompt;
import com.aresstack.enterpriseai.proasteion.exedra.security.SwingPairingCallback;
import com.aresstack.enterpriseai.proasteion.exedra.settings.ConnectionChecker;
import com.aresstack.enterpriseai.proasteion.exedra.settings.ConnectionProbe;
import com.aresstack.enterpriseai.proasteion.exedra.speech.ReadAloudBinding;
import com.aresstack.enterpriseai.proasteion.exedra.speech.SpeechOutput;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.setup.SetupVoices;
import com.aresstack.enterpriseai.astu.security.SecretRef;

import com.aresstack.enterpriseai.proasteion.application.net.NetworkServices;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Der Verbindungstest des Einstellungen-Dialogs: holt den API-Key wie die Anwendung über den Tresorzugang des
 * Kompositionspunkts ({@link AdapterAssembly#keePassAccess}: Pairing-Dialog, Pairing-Schlüssel in der
 * konfigurierten Datei) und lässt dann {@link ConnectionProbe} den Weg zum Dienst Schritt für Schritt abgehen.
 * Kann KeePass den Key nicht liefern, nennt die Meldung nur den Grund; der Test läuft ohne Key weiter.
 */
public final class ServiceConnectionChecker implements ConnectionChecker {

    private final KeePassSecretChecker.PairingCallbackFactory pairing;
    private final ModelCatalogs catalogs;

    /** Produktiv: der Swing-Pairing-Dialog. */
    public ServiceConnectionChecker() {
        this((ModelCatalogs) null);
    }

    public ServiceConnectionChecker(KeePassSecretChecker.PairingCallbackFactory pairing) {
        this(pairing, null);
    }

    /** Produktiv mit Sprachausgabe: die Übergabe am Ende der ersten Einrichtung spricht über diese Modellquellen. */
    public ServiceConnectionChecker(ModelCatalogs catalogs) {
        this(new KeePassSecretChecker.PairingCallbackFactory() {
            @Override
            public PairingPrompt forAddress(String keePassAddress) {
                return new SwingPairingCallback(keePassAddress);
            }
        }, catalogs);
    }

    /** @param catalogs Modellquellen für die Stimmen der Übergabe oder {@code null} (dann nur Sprechblasen) */
    public ServiceConnectionChecker(KeePassSecretChecker.PairingCallbackFactory pairing, ModelCatalogs catalogs) {
        if (pairing == null) {
            throw new IllegalArgumentException("pairing must not be null");
        }
        this.pairing = pairing;
        this.catalogs = catalogs;
    }

    @Override
    public boolean check(final AppConfig config, Consumer<ConnectionCheckStep> onStep) {
        if (config == null || onStep == null) {
            throw new IllegalArgumentException("config and onStep must not be null");
        }
        ConnectionProbe.TokenLookup tokens = new ConnectionProbe.TokenLookup() {
            @Override
            public String token() throws IOException {
                return chatToken(config, pairing);
            }
        };
        return new ConnectionProbe(config, tokens).run(onStep);
    }

    /**
     * Der Embedding-Test: der API-Key des Embedding-Dienstes kommt wie bei der Anwendung aus KeePass (ohne eigenen
     * Eintrag der des Chats), dann ein Aufruf über den Adapter des Kompositionspunkts.
     */
    @Override
    public int embeddingDimension(AppConfig config) throws Exception {
        if (config == null || config.embedding() == null) {
            throw new IllegalArgumentException("config with embedding section required");
        }
        SecretRef ref = config.embedding().apiKeyRef() == null ? config.chat().apiKeyRef() : config.embedding().apiKeyRef();
        String token = null;
        if (config.keePass().enabled() && ref != null) {
            try {
                token = AdapterAssembly.keePassAccess(config.keePass(), pairing.forAddress(config.keePass().address()),
                        new DialogCredentialPrompt()).token(ref);
            } catch (KeePassAccess.Failure e) {
                throw new IOException(e.getMessage());
            }
        }
        return AdapterAssembly.embeddingDimension(config.embedding(), token, NetworkServices.from(config.network()));
    }

    /** Merlins freie Frage: API-Key des Chats wie beim Verbindungstest, dann ein Aufruf über den Chat-Adapter. */
    @Override
    public String ask(AppConfig config, String system, String question) throws Exception {
        if (config == null || config.chat() == null) {
            throw new IllegalArgumentException("config with chat section required");
        }
        return AdapterAssembly.askOnce(config.chat(), chatToken(config, pairing), NetworkServices.from(config.network()),
                system, question);
    }

    /**
     * Merlins Stimme ({@code setup.merlin.ttsModel}, ohne die Stimme des Chats) und die des Chat-Assistenten
     * ({@code model.tts}) über die Modellquellen der Anwendung. Der API-Key wird erst beim ersten Sprechen geholt,
     * einmal für beide.
     */
    @Override
    public SetupVoices voices(final AppConfig config) {
        if (config == null || config.chat() == null) {
            throw new IllegalArgumentException("config with chat section required");
        }
        String name = assistantName(config);
        if (catalogs == null) {
            return SetupVoices.silent(name);
        }
        NetworkServices network = NetworkServices.from(config.network());
        Supplier<String> token = onceToken(config);
        ModelsConfig models = config.models();
        TextAnalysisService analysis = SpeechOutput.textAnalysis();
        final ReadAloudBinding merlin = SpeechOutput.merlinVoice(models,
                catalogs.speech(config, models, network, token));
        final ReadAloudBinding assistant = SpeechOutput.readAloud(models,
                catalogs.speech(config, models, network, token), analysis);
        return new SetupVoices(merlin, assistant, name, () -> {
            merlin.close();
            assistant.close();
        });
    }

    @Override
    public SetupVoices merlinVoice(final AppConfig config) {
        if (config == null || config.chat() == null) {
            throw new IllegalArgumentException("config with chat section required");
        }
        if (catalogs == null) {
            return SetupVoices.silent(null);
        }
        ModelsConfig models = config.models();
        final ReadAloudBinding merlin = SpeechOutput.merlinVoice(models,
                catalogs.speech(config, models, NetworkServices.from(config.network()), onceToken(config)));
        return new SetupVoices(merlin, null, null, merlin::close);
    }

    /** Der API-Key des Chats, erst beim ersten Sprechen geholt und dann gemerkt. */
    private Supplier<String> onceToken(final AppConfig config) {
        return new Supplier<String>() {
            private boolean loaded;
            private String key;

            @Override
            public synchronized String get() {
                if (!loaded) {
                    loaded = true;
                    try {
                        key = chatToken(config, pairing);
                    } catch (IOException e) {
                        key = null; // ohne Key antwortet der Dienst mit dem Grund, das Vorlesen meldet ihn
                    }
                }
                return key;
            }
        };
    }

    private static String assistantName(AppConfig config) {
        return config.chat().assistantDisplayName();
    }

    /**
     * Der API-Key des Chats über KeePass, wie die Anwendung ihn holt (Pairing-Dialog bei Bedarf); {@code null} ohne
     * KeePass oder Referenz. Auch für die Modellabfrage des Einstellungen-Dialogs ({@link DialogModelCatalogs}).
     */
    static String chatToken(AppConfig config, KeePassSecretChecker.PairingCallbackFactory pairing)
            throws IOException {
        SecretRef ref = config.chat().apiKeyRef();
        if (!config.keePass().enabled() || ref == null) {
            return null;
        }
        try {
            return AdapterAssembly.keePassAccess(config.keePass(), pairing.forAddress(config.keePass().address()),
                    new DialogCredentialPrompt()).token(ref);
        } catch (KeePassAccess.Failure e) {
            // Nur der Grund wandert in die Anzeige; die Ausnahme selbst bleibt hier.
            throw e.temporary() ? new ConnectionProbe.TokenTemporarilyUnavailable(e.getMessage())
                    : new IOException(e.getMessage());
        }
    }
}

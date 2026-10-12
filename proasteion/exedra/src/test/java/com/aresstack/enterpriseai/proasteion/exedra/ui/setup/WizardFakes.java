package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.CatalogStatus;
import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelDescriptor;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.astu.localruntime.InstallCancellation;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckStep;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceOffersCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordEntry;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

import java.awt.Component;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Ersatz für Einstellungen- und Einrichtungsaktionen: alles synchron auf dem aufrufenden Thread, wie vorgegeben. */
final class WizardFakes {

    private WizardFakes() {
    }

    /** KeePass mit den zwei Einträgen der Einrichtung, Verbindungstest grün, lokale Modelle nach Skript. */
    static final class Settings implements SettingsDialogActions {
        final List<SettingsForm> saved = new ArrayList<SettingsForm>();
        List<LocalVoiceOffer> offers = new ArrayList<LocalVoiceOffer>();
        /** Ohne Eintrag schließt die Installation sofort erfolgreich ab und meldet {@link #installedAs}. */
        final List<LocalVoiceInstallProgress> pendingInstalls = new ArrayList<LocalVoiceInstallProgress>();
        final List<String> installRequests = new ArrayList<String>();
        /** The cancellation handle of every requested install, in order. */
        final List<InstallCancellation> installCancellations = new ArrayList<InstallCancellation>();
        /** Models offered by the catalog query of step 3: chat ids and embedding ids. */
        final List<String> chatModels = new ArrayList<String>();
        final List<String> embeddingModels = new ArrayList<String>();
        final List<String> speechModels = new ArrayList<String>();
        final List<String> visionModels = new ArrayList<String>();
        final Map<ModelCategory, List<String>> preferredMatch =
                new java.util.EnumMap<ModelCategory, List<String>>(ModelCategory.class);
        final Map<ModelCategory, String> preferred = new java.util.EnumMap<ModelCategory, String>(ModelCategory.class);
        boolean holdInstalls;
        String installFailure;
        String installedAs;
        /** Gesetzt: die Angebote lokaler Modelle lassen sich nicht ermitteln. */
        String offersFailure;
        /** Wie oft die Eintragsliste aus KeePass gelesen wurde. */
        int listCalls;
        /** Gesetzt: KeePass liefert keine Eintragsliste (nicht gekoppelt). */
        String listFailure;
        /** Je Verbindungstest: Titel|URL|Benutzer|Passwort, wie der Test sie bekam. */
        final List<String> tested = new ArrayList<String>();
        /** Wie oft ein Eintrag gespeichert wurde. */
        int saves;
        final Map<String, PasswordEntry> vault = new LinkedHashMap<String, PasswordEntry>();

        Settings() {
            vault.put("Enterprise AI API", new PasswordEntry("Enterprise AI API", "", "https://ai.example.invalid/v1",
                    Collections.<String, String>emptyMap(), "test-key".toCharArray()));
            vault.put("Confluence", new PasswordEntry("Confluence", "jdoe", "https://wiki.example.invalid",
                    Collections.<String, String>emptyMap(), "test-password".toCharArray()));
        }

        @Override
        public List<String> validate(SettingsForm form) {
            return Collections.emptyList();
        }

        @Override
        public void save(SettingsForm form) {
            saved.add(form);
        }

        @Override
        public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
            onResult.accept(SecretCheckResult.ok("gefunden"));
        }

        @Override
        public PasswordStore passwordStore(SettingsForm form) {
            return new PasswordStore() {
                @Override
                public void list(PasswordCallback<List<PasswordEntry>> done) {
                    listCalls++;
                    if (listFailure != null) {
                        done.done(null, listFailure);
                        return;
                    }
                    List<PasswordEntry> entries = new ArrayList<PasswordEntry>();
                    for (PasswordEntry entry : vault.values()) {
                        entries.add(new PasswordEntry(entry.title(), entry.userName(), entry.url(), entry.fields(),
                                null));
                    }
                    done.done(entries, null);
                }

                @Override
                public void read(String title, PasswordCallback<PasswordEntry> done) {
                    PasswordEntry entry = vault.get(title);
                    done.done(entry == null ? null : new PasswordEntry(entry.title(), entry.userName(), entry.url(),
                            entry.fields(), entry.copyPassword()), entry == null ? "fehlt" : null);
                }

                @Override
                public void save(PasswordEntry entry, PasswordCallback<Void> done) {
                    saves++;
                    vault.put(entry.title(), new PasswordEntry(entry.title(), entry.userName(), entry.url(),
                            entry.fields(), entry.copyPassword()));
                    done.done(null, null);
                }

                @Override
                public void remove(String title, PasswordCallback<Void> done) {
                    vault.remove(title);
                    done.done(null, null);
                }

                @Override
                public void testConnection(PasswordEntry entry, PasswordCallback<String> done) {
                    char[] password = entry.copyPassword();
                    tested.add(entry.title() + "|" + entry.url() + "|" + entry.userName() + "|"
                            + (password == null ? "" : new String(password)));
                    done.done("Verbindung erfolgreich: 3 Spaces sichtbar.", null);
                }
            };
        }

        @Override
        public void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
            listener.onStep(ConnectionCheckStep.ok("GET /models", "HTTP 200"));
            listener.onFinished(true);
        }

        @Override
        public void refreshModels(SettingsForm form, Consumer<ModelCatalogSnapshot> onResult) {
            List<ModelDescriptor> models = new ArrayList<ModelDescriptor>();
            for (String id : chatModels) {
                models.add(descriptor(id, ModelCategory.CHAT));
            }
            for (String id : embeddingModels) {
                models.add(descriptor(id, ModelCategory.EMBEDDING));
            }
            for (String id : speechModels) {
                models.add(ModelDescriptor.builder("default", id).inputModalities(Collections.singletonList("text"))
                        .outputModalities(Collections.singletonList("audio")).build());
            }
            for (String id : visionModels) {
                models.add(ModelDescriptor.builder("default", id).capabilities(Collections.singletonList("vision"))
                        .build());
            }
            onResult.accept(models.isEmpty() ? ModelCatalogSnapshot.empty()
                    : new ModelCatalogSnapshot(models, Collections.<CatalogStatus>emptyList()));
        }

        @Override
        public String storedModel(ModelReference reference) {
            return reference.modelId();
        }

        private static ModelDescriptor descriptor(String id, ModelCategory category) {
            return ModelDescriptor.builder("default", id).capabilities(Collections.singletonList(
                    category == ModelCategory.CHAT ? "chat" : "embeddings")).build();
        }

        @Override
        public List<String> preferredModelMatch(ModelCategory category) {
            List<String> match = preferredMatch.get(category);
            return match == null ? Collections.<String>emptyList() : match;
        }

        @Override
        public String preferredModel(ModelCategory category) {
            String model = preferred.get(category);
            return model == null ? "" : model;
        }

        @Override
        public void localVoices(SettingsForm form, LocalVoiceOffersCallback callback) {
            if (offersFailure != null) {
                callback.failed(offersFailure);
                return;
            }
            callback.offers(new ArrayList<LocalVoiceOffer>(offers));
        }

        @Override
        public void installLocalVoice(SettingsForm form, String voiceId, InstallCancellation cancellation,
                                      LocalVoiceInstallProgress progress) {
            installRequests.add(voiceId);
            installCancellations.add(cancellation);
            progress.file("audio_encoder_q4.onnx", 1, 3, 512L * 1024, 1024L * 1024);
            if (holdInstalls) {
                pendingInstalls.add(progress);
                return;
            }
            complete(progress);
        }

        /** Beendet eine Installation nach Skript: Fehler, oder Erfolg mit neuem Ordner {@link #installedAs}. */
        void complete(LocalVoiceInstallProgress progress) {
            if (installFailure != null) {
                progress.finished(false, installFailure);
                return;
            }
            offers.add(new LocalVoiceOffer(installedAs, "Gemma 4 E2B (Spracheingabe)", "", true,
                    ModelCategory.STT));
            progress.finished(true, "installiert");
        }

        @Override
        public String localVoiceSelection(String voiceId) {
            return "local:" + voiceId;
        }
    }

    /** Abschlussprüfung grün, Embedding-Test liefert 768. */
    static final class Setup implements SetupActions {
        final List<SettingsForm> finished = new ArrayList<SettingsForm>();
        /** Die Confluence-Angaben je Abschlussprüfung ({@code null}: übersprungen). */
        final List<ConfluenceSetup> confluences = new ArrayList<ConfluenceSetup>();
        final List<String> questions = new ArrayList<String>();
        final List<String> systems = new ArrayList<String>();
        /** Merlins Antwort aufs freie Fragen; {@code null} = Fehler {@link #askFailure}. */
        String answer = "Ganz einfach: erst KeePass, dann der Rest.";
        String askFailure = "HTTP 503";

        @Override
        public void ask(SettingsForm form, String system, String question, BiConsumer<String, String> done) {
            systems.add(system);
            questions.add(question);
            done.accept(answer, answer == null ? askFailure : null);
        }

        /** Merlins Stimme nach dem KI-Dienst-Test; {@code null} = nur Sprechblasen. */
        SetupVoices merlinVoice;
        int merlinVoiceRequested;

        @Override
        public void merlinVoice(SettingsForm form, Consumer<SetupVoices> done) {
            merlinVoiceRequested++;
            done.accept(merlinVoice == null ? SetupVoices.silent(null) : merlinVoice);
        }

        /** Stimmen der Übergabe; {@code null} = nur Sprechblasen. */
        SetupVoices voices;
        int voicesRequested;

        @Override
        public void voices(SettingsForm form, Consumer<SetupVoices> done) {
            voicesRequested++;
            done.accept(voices == null ? SetupVoices.silent("Testtochter") : voices);
        }

        @Override
        public void embeddingDimension(SettingsForm form, BiConsumer<Integer, String> done) {
            done.accept(768, null);
        }

        @Override
        public void finish(SettingsForm form, ConfluenceSetup confluence, Consumer<List<SetupCheck>> done) {
            finished.add(form);
            confluences.add(confluence);
            if (form.chatModel().trim().isEmpty()) {
                done.accept(Arrays.asList(SetupCheck.ok("KeePass", ""), SetupCheck.failed("Chat-Modell",
                        "kein Chat-Modell gewählt")));
                return;
            }
            done.accept(Arrays.asList(SetupCheck.ok("KeePass", ""), SetupCheck.ok("KI-Dienst", ""),
                    SetupCheck.ok("Konfiguration lädt", "")));
        }

        @Override
        public SettingsForm editNetwork(Component parent, SettingsForm form) {
            return null;
        }

        @Override
        public String chooseCertificate(Component parent, String current) {
            return null;
        }
    }
}

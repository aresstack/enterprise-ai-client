package com.aresstack.enterpriseai.proasteion.exedra.ui.setup;

import com.aresstack.enterpriseai.astu.acropolis.modelcatalog.ModelCatalogSnapshot;
import com.aresstack.enterpriseai.astu.localruntime.LocalVoiceOffer;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelCategory;
import com.aresstack.enterpriseai.astu.modelcatalog.ModelReference;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.ConnectionCheckListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceInstallProgress;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.LocalVoiceSearchCallback;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.NetworkLogListener;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.NlpModelOffer;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.PasswordStore;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SecretCheckResult;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsDialogActions;
import com.aresstack.enterpriseai.proasteion.exedra.ui.settings.SettingsForm;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Die Einstellungen-Aktionen für „Netzwerkeinstellungen…“ aus dem Einrichtungsassistenten: alles wie beim
 * vorhandenen Dialog (Proxy auflösen, HTTPS testen, KeePass), nur „Speichern“ schreibt nicht in die Datei, sondern
 * übernimmt den Entwurf in den Assistenten. Geprüft wird erst die vollständige Konfiguration am Ende des
 * Assistenten, deshalb meldet {@link #validate} hier keine fehlenden Modelle.
 */
public final class DraftSettingsActions implements SettingsDialogActions {

    private final SettingsDialogActions delegate;
    private SettingsForm saved;

    public DraftSettingsActions(SettingsDialogActions delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate must not be null");
        }
        this.delegate = delegate;
    }

    /** Der übernommene Entwurf oder {@code null}, wenn nicht gespeichert wurde. */
    public SettingsForm saved() {
        return saved;
    }

    @Override
    public List<String> validate(SettingsForm form) {
        return Collections.emptyList();
    }

    @Override
    public void save(SettingsForm form) {
        saved = form;
    }

    @Override
    public String technicalDetails() {
        return delegate.technicalDetails();
    }

    @Override
    public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
        delegate.checkSecret(form, secretRef, onResult);
    }

    @Override
    public void keePassEntries(SettingsForm form, Consumer<List<String>> onResult) {
        delegate.keePassEntries(form, onResult);
    }

    @Override
    public PasswordStore passwordStore(SettingsForm form) {
        return delegate.passwordStore(form);
    }

    @Override
    public void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
        delegate.checkConnection(form, listener);
    }

    @Override
    public void resolveProxy(SettingsForm form, NetworkLogListener listener) {
        delegate.resolveProxy(form, listener);
    }

    @Override
    public void checkHttps(SettingsForm form, NetworkLogListener listener) {
        delegate.checkHttps(form, listener);
    }

    @Override
    public ModelCatalogSnapshot cachedModels() {
        return delegate.cachedModels();
    }

    @Override
    public void refreshModels(SettingsForm form, Consumer<ModelCatalogSnapshot> onResult) {
        delegate.refreshModels(form, onResult);
    }

    @Override
    public ModelReference parseModel(String text) {
        return delegate.parseModel(text);
    }

    @Override
    public String storedModel(ModelReference reference) {
        return delegate.storedModel(reference);
    }

    @Override
    public boolean isLocal(ModelReference reference) {
        return delegate.isLocal(reference);
    }

    @Override
    public void localVoices(SettingsForm form, Consumer<List<LocalVoiceOffer>> onResult) {
        delegate.localVoices(form, onResult);
    }

    @Override
    public void installLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        delegate.installLocalVoice(form, voiceId, progress);
    }

    @Override
    public void searchLocalVoices(SettingsForm form, String query, ModelCategory category,
                                  LocalVoiceSearchCallback callback) {
        delegate.searchLocalVoices(form, query, category, callback);
    }

    @Override
    public List<String> hubSearchHistory() {
        return delegate.hubSearchHistory();
    }

    @Override
    public List<String> rememberHubSearch(String query) {
        return delegate.rememberHubSearch(query);
    }

    @Override
    public void updateLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        delegate.updateLocalVoice(form, voiceId, progress);
    }

    @Override
    public void removeLocalVoice(SettingsForm form, String voiceId, LocalVoiceInstallProgress progress) {
        delegate.removeLocalVoice(form, voiceId, progress);
    }

    @Override
    public String localVoiceSelection(String voiceId) {
        return delegate.localVoiceSelection(voiceId);
    }

    @Override
    public void nlpModels(Consumer<List<NlpModelOffer>> onResult) {
        delegate.nlpModels(onResult);
    }

    @Override
    public void installNlpModel(SettingsForm form, String id, BiConsumer<Boolean, String> onFinished) {
        delegate.installNlpModel(form, id, onFinished);
    }

    @Override
    public String defaultDiscoveryScript(String proxyMode) {
        return delegate.defaultDiscoveryScript(proxyMode);
    }
}

package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.ArrayList;
import java.util.List;

/** Fake-Security-Port: liefert je Aufruf frisches Material und merkt sich, was er herausgegeben hat. */
final class RecordingSecretProvider implements SecretProvider {

    private final String principal;
    private final String secret;
    private final SecretUnavailableException failure;
    final List<SecretRef> requests = new ArrayList<SecretRef>();
    final List<SecretMaterial> issued = new ArrayList<SecretMaterial>();

    RecordingSecretProvider(String principal, String secret) {
        this.principal = principal;
        this.secret = secret;
        this.failure = null;
    }

    RecordingSecretProvider(SecretUnavailableException failure) {
        this.principal = null;
        this.secret = null;
        this.failure = failure;
    }

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        requests.add(ref);
        if (failure != null) {
            throw failure;
        }
        SecretMaterial material = new SecretMaterial(ref, principal, secret.toCharArray());
        issued.add(material);
        return material;
    }

    boolean allIssuedMaterialClosed() {
        for (SecretMaterial material : issued) {
            if (!material.isClosed()) {
                return false;
            }
        }
        return true;
    }
}

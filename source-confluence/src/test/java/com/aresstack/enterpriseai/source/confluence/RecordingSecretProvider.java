package com.aresstack.enterpriseai.source.confluence;

import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;

import java.util.ArrayList;
import java.util.List;

/** Secret-Provider für Tests: liefert festes Material, zählt Auflösungen und merkt sich ausgegebenes Material. */
final class RecordingSecretProvider implements SecretProvider {

    static final SecretRef REF = SecretRef.of("keepass:confluence");

    final List<SecretMaterial> issued = new ArrayList<SecretMaterial>();
    String principal = "alice";
    String secret = "s3cr3t-Pässwort";
    SecretUnavailableException.Reason failWith;

    @Override
    public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
        if (failWith != null) {
            throw new SecretUnavailableException(failWith, ref, "Testfehler");
        }
        if (!REF.equals(ref)) {
            throw new SecretUnavailableException(SecretUnavailableException.Reason.NOT_FOUND, ref, "unbekannt");
        }
        SecretMaterial material = new SecretMaterial(ref, principal, secret.toCharArray());
        issued.add(material);
        return material;
    }
}

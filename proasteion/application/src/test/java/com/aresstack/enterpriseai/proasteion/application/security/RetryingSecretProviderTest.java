package com.aresstack.enterpriseai.proasteion.application.security;

import com.aresstack.enterpriseai.adyton.SecretMaterial;
import com.aresstack.enterpriseai.adyton.SecretProvider;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException;
import com.aresstack.enterpriseai.adyton.SecretUnavailableException.Reason;
import com.aresstack.enterpriseai.astu.security.SecretRef;
import com.aresstack.enterpriseai.proasteion.application.retry.RetryPolicy;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class RetryingSecretProviderTest {

    private static final SecretRef REF = SecretRef.of("keepass:KI-Dienst");

    private final List<Long> pauses = new ArrayList<Long>();
    private final FlakyVault vault = new FlakyVault();
    private final RetryingSecretProvider secrets = new RetryingSecretProvider(vault,
            () -> RetryPolicy.of(2, 300), pauses::add);

    @Test
    public void notAvailableTwiceThenTheKeyIsRead() throws Exception {
        vault.fail(Reason.NOT_AVAILABLE, Reason.NOT_AVAILABLE);

        assertEquals("geheim", new SecretBackedTokenSource(secrets, REF).token());
        assertEquals(3, vault.calls);
        assertEquals(Arrays.asList(300L, 750L), pauses);
    }

    @Test
    public void notAvailableBeyondTheRetriesStaysTransientForTheChat() {
        vault.fail(Reason.NOT_AVAILABLE, Reason.NOT_AVAILABLE, Reason.NOT_AVAILABLE);
        SecretBackedTokenSource source = new SecretBackedTokenSource(secrets, REF);
        try {
            source.token();
            fail();
        } catch (SecretAccessException e) {
            assertEquals(Reason.NOT_AVAILABLE, e.reason());
            assertEquals("Ablage nur kurz weg: kein Anmeldefehler", true, source.isTransient(e));
        }
        assertEquals(3, vault.calls);
    }

    @Test
    public void notFoundIsNotRetried() {
        assertNotRetried(Reason.NOT_FOUND);
    }

    @Test
    public void accessDeniedIsNotRetried() {
        assertNotRetried(Reason.ACCESS_DENIED);
    }

    @Test
    public void cancelledPairingIsNotRetried() {
        assertNotRetried(Reason.CANCELLED);
    }

    @Test
    public void retriesFollowTheCurrentSettingWithoutNewProvider() throws Exception {
        final RetryPolicy[] setting = {RetryPolicy.none()};
        RetryingSecretProvider live = new RetryingSecretProvider(vault, () -> setting[0], pauses::add);
        vault.fail(Reason.NOT_AVAILABLE);
        try {
            live.resolve(REF);
            fail();
        } catch (SecretUnavailableException expected) {
            assertEquals(1, vault.calls);
        }
        setting[0] = RetryPolicy.of(1, 0);
        vault.fail(Reason.NOT_AVAILABLE);
        live.resolve(REF).close();
        assertEquals(3, vault.calls);
    }

    private void assertNotRetried(Reason reason) {
        vault.fail(reason, reason);
        SecretBackedTokenSource source = new SecretBackedTokenSource(secrets, REF);
        try {
            source.token();
            fail();
        } catch (SecretAccessException e) {
            assertEquals(reason, e.reason());
            assertEquals(false, source.isTransient(e));
        }
        assertEquals(1, vault.calls);
        assertEquals(0, pauses.size());
    }

    /** Scheitert mit den vorgegebenen Gründen, danach liefert er das Material. */
    private static final class FlakyVault implements SecretProvider {
        final LinkedList<Reason> failures = new LinkedList<Reason>();
        int calls;

        void fail(Reason... reasons) {
            failures.addAll(Arrays.asList(reasons));
        }

        @Override
        public SecretMaterial resolve(SecretRef ref) throws SecretUnavailableException {
            calls++;
            Reason reason = failures.poll();
            if (reason != null) {
                throw new SecretUnavailableException(reason, ref, "Testfehler");
            }
            return new SecretMaterial(ref, "user", "geheim".toCharArray());
        }
    }
}

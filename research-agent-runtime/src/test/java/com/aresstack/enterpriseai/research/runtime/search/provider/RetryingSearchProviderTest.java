package com.aresstack.enterpriseai.research.runtime.search.provider;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class RetryingSearchProviderTest {

    private static final SearchProviderRequest REQUEST =
            new SearchProviderRequest("Schulung", SearchEngine.PROVIDER_DEFAULT, 5, null, null);
    private static final SearchProviderResult RESULT = new SearchProviderResult(SearchProviderId.KNOWLEDGE_SEARCH,
            SearchEngine.PROVIDER_DEFAULT, Collections.<SearchHit>emptyList());

    private final List<Long> pauses = new ArrayList<Long>();
    private final Scripted provider = new Scripted();

    @Test
    public void temporaryAndRateLimitFailuresAreRetried() {
        provider.failures.add(new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH, "weg"));
        provider.failures.add(new SearchProviderRateLimitException(SearchProviderId.KNOWLEDGE_SEARCH, "429"));

        assertSame(RESULT, retrying(2, 300).search(REQUEST));
        assertEquals(3, provider.calls);
        assertEquals(Arrays.asList(300L, 750L), pauses);
    }

    @Test
    public void authenticationFailureIsNotRetried() {
        provider.failures.add(new SearchProviderAuthenticationException(SearchProviderId.KNOWLEDGE_SEARCH, "401"));
        try {
            retrying(2, 300).search(REQUEST);
            fail();
        } catch (SearchProviderAuthenticationException expected) {
            assertEquals(1, provider.calls);
        }
    }

    @Test
    public void lastFailureGoesToTheStrategy() {
        for (int i = 0; i < 3; i++) {
            provider.failures.add(new SearchProviderTemporaryException(SearchProviderId.KNOWLEDGE_SEARCH, "weg " + i));
        }
        try {
            retrying(1, 0).search(REQUEST);
            fail();
        } catch (SearchProviderTemporaryException e) {
            assertEquals("weg 1", e.getMessage());
        }
        assertEquals(2, provider.calls);
    }

    @Test
    public void environmentCarriesTheHostSetting() {
        Map<String, String> env = new HashMap<String, String>();
        assertEquals(RetryingSearchProvider.DEFAULT_RETRIES,
                RetryingSearchProvider.fromEnvironment(provider, env).getRetries());
        env.put(RetryingSearchProvider.ENV_RETRY_COUNT, "0");
        env.put(RetryingSearchProvider.ENV_RETRY_DELAY_MILLIS, "120");
        RetryingSearchProvider configured = RetryingSearchProvider.fromEnvironment(provider, env);
        assertEquals(0, configured.getRetries());
        assertEquals(120L, configured.getInitialDelayMillis());
        env.put(RetryingSearchProvider.ENV_RETRY_COUNT, "unsinn");
        assertEquals(RetryingSearchProvider.DEFAULT_RETRIES,
                RetryingSearchProvider.fromEnvironment(provider, env).getRetries());
    }

    private RetryingSearchProvider retrying(int retries, long delay) {
        return new RetryingSearchProvider(provider, retries, delay, pauses::add);
    }

    private static final class Scripted implements SearchProvider {
        final LinkedList<SearchProviderException> failures = new LinkedList<SearchProviderException>();
        int calls;

        @Override
        public SearchProviderId getProviderId() {
            return SearchProviderId.KNOWLEDGE_SEARCH;
        }

        @Override
        public SearchProviderResult search(SearchProviderRequest request) {
            calls++;
            SearchProviderException failure = failures.poll();
            if (failure != null) {
                throw failure;
            }
            return RESULT;
        }

        @Override
        public SearchProviderAvailability getAvailability() {
            return null;
        }
    }
}

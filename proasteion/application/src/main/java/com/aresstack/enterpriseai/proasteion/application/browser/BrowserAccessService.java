package com.aresstack.enterpriseai.proasteion.application.browser;

import com.aresstack.enterpriseai.astu.approval.ApprovalDecision;
import com.aresstack.enterpriseai.astu.approval.ApprovalPromptPort;
import com.aresstack.enterpriseai.astu.approval.ApprovalRequest;
import com.aresstack.enterpriseai.astu.approval.ApprovalTarget;
import com.aresstack.enterpriseai.astu.approval.ToolApprovalGate;
import com.aresstack.enterpriseai.astu.chat.ChatConversationId;
import com.aresstack.enterpriseai.browser.api.BrowserAccessGuard;
import com.aresstack.enterpriseai.browser.api.BrowserAccessPolicy;
import com.aresstack.enterpriseai.browser.api.BrowserException;
import com.aresstack.enterpriseai.proasteion.application.config.BrowserAccessConfig;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * Enforces the global browser access policy (issue #141): the block list always wins, the allow list passes, and an
 * unknown host is either allowed (mode allow) or the user is asked with four answers (once, always allow, always
 * forbid, cancel). "Always" answers are persisted for the exact host through the {@link BrowserPolicyStore}.
 * {@link #admit} may block the calling (tool) thread while the user decides; it never runs on the UI thread.
 */
public final class BrowserAccessService implements BrowserAccessGuard {

    /** Tool name shown in the approval question. */
    public static final String TOOL_NAME = "browser_navigate";

    private final Supplier<BrowserAccessPolicy> policy;
    private final Supplier<ApprovalPromptPort> prompt;
    private final BrowserPolicyStore store;
    private final long timeoutMillis;

    public BrowserAccessService(Supplier<BrowserAccessPolicy> policy, Supplier<ApprovalPromptPort> prompt,
                                BrowserPolicyStore store) {
        this(policy, prompt, store, ToolApprovalGate.DEFAULT_TIMEOUT_MILLIS);
    }

    public BrowserAccessService(Supplier<BrowserAccessPolicy> policy, Supplier<ApprovalPromptPort> prompt,
                                BrowserPolicyStore store, long timeoutMillis) {
        if (policy == null || prompt == null || store == null) {
            throw new IllegalArgumentException("policy, prompt and store must not be null");
        }
        this.policy = policy;
        this.prompt = prompt;
        this.store = store;
        this.timeoutMillis = timeoutMillis;
    }

    /** The browser policy of a configuration snapshot. */
    public static BrowserAccessPolicy policyOf(BrowserAccessConfig config) {
        if (config == null) {
            return BrowserAccessPolicy.defaults();
        }
        return new BrowserAccessPolicy(config.allowByDefault() ? BrowserAccessPolicy.Mode.ALLOW_BY_DEFAULT
                : BrowserAccessPolicy.Mode.BLOCK_BY_DEFAULT, config.allow(), config.block());
    }

    @Override
    public void admit(ChatConversationId chat, String url) throws BrowserException {
        BrowserAccessPolicy current = policy.get();
        if (current == null) {
            current = BrowserAccessPolicy.defaults();
        }
        BrowserAccessPolicy.Verdict verdict = current.decide(url);
        String host = BrowserAccessPolicy.hostOf(url);
        if (verdict == BrowserAccessPolicy.Verdict.ALLOWED) {
            return;
        }
        if (verdict == BrowserAccessPolicy.Verdict.BLOCKED || host == null) {
            throw blocked(host == null ? String.valueOf(url) : host);
        }
        ApprovalRequest request = new ApprovalRequest(TOOL_NAME,
                new ApprovalTarget("open", null, host, null, host + " möchte geöffnet werden: " + url), true);
        ApprovalPromptPort port = prompt.get();
        ApprovalDecision decision;
        try {
            decision = (port == null ? ApprovalPromptPort.unavailable() : port).ask(request, timeoutMillis);
        } catch (RuntimeException e) {
            decision = ApprovalDecision.of(request, ApprovalDecision.Outcome.UNAVAILABLE);
        }
        if (decision == null || !request.id().equals(decision.requestId())) {
            throw notApproved(host);
        }
        switch (decision.outcome()) {
            case APPROVED:
                return;
            case APPROVED_ALWAYS:
                remember(true, host);
                return;
            case REJECTED_ALWAYS:
                remember(false, host);
                throw blocked(host);
            default:
                throw notApproved(host);
        }
    }

    /** Persists the answer; a failed write still applies the answer to this call. */
    private void remember(boolean allow, String host) {
        try {
            if (allow) {
                store.allow(host);
            } else {
                store.block(host);
            }
        } catch (IOException | RuntimeException ignored) {
            // The decision holds for this navigation; the user can add the rule in Einstellungen > Browser.
        }
    }

    private static BrowserException blocked(String host) {
        return new BrowserException(BrowserException.Kind.BLOCKED,
                "Die Browser-Richtlinie sperrt " + host + " (Einstellungen → Browser).");
    }

    private static BrowserException notApproved(String host) {
        return new BrowserException(BrowserException.Kind.BLOCKED,
                host + " wurde nicht freigegeben (Einstellungen → Browser).");
    }
}

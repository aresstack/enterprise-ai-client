package com.aresstack.enterpriseai.browser.api;

/**
 * What stands between the model and the content of a page: a cookie or consent banner (with the labels of its
 * buttons) and a challenge only the user can solve in the visible browser. {@code challenge} is empty, {@code captcha}
 * (bot check, Cloudflare and similar) or {@code login} (a password field: the model never fills passwords).
 */
public final class BrowserProbe {

    public static final String CAPTCHA = "captcha";
    public static final String LOGIN = "login";

    private final boolean consent;
    private final String consentOptions;
    private final String challenge;

    public BrowserProbe(boolean consent, String consentOptions, String challenge) {
        this.consent = consent;
        this.consentOptions = consentOptions == null ? "" : consentOptions;
        this.challenge = challenge == null ? "" : challenge;
    }

    public static BrowserProbe clear() {
        return new BrowserProbe(false, "", "");
    }

    public boolean consent() {
        return consent;
    }

    /** The button labels of the consent banner, separated by {@code " | "}. */
    public String consentOptions() {
        return consentOptions;
    }

    public String challenge() {
        return challenge;
    }

    public boolean hasChallenge() {
        return !challenge.isEmpty();
    }
}

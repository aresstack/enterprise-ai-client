package com.aresstack.enterpriseai.app.ui.settings;

/**
 * Eine Wissensquelle im Einstellungen-Dialog, so wie der Benutzer sie eingibt (Text und Schalter, keine
 * Secrets, keine Adaptertypen). Je nach {@link #type()} sind nur ein Teil der Felder sinnvoll: MediaWiki
 * nutzt {@code url} als API-URL, {@code siteKey}, {@code displayName} und {@code requiresLogin}; Confluence nutzt
 * {@code url} als Basis-URL, {@code searchSpaceKeys} und {@code includeAttachments}.
 */
public final class SourceForm {

    public static final String TYPE_MEDIAWIKI = "mediawiki";
    public static final String TYPE_CONFLUENCE = "confluence";

    private final String id;
    private final String type;
    private final String url;
    private final String credentialRef;
    private final String startPoints;
    private final String maxDepth;
    private final String maxResources;
    private final boolean requiresLogin;
    private final String siteKey;
    private final String displayName;
    private final String searchSpaceKeys;
    private final boolean includeAttachments;

    private SourceForm(Builder b) {
        this.id = b.id;
        this.type = b.type;
        this.url = b.url;
        this.credentialRef = b.credentialRef;
        this.startPoints = b.startPoints;
        this.maxDepth = b.maxDepth;
        this.maxResources = b.maxResources;
        this.requiresLogin = b.requiresLogin;
        this.siteKey = b.siteKey;
        this.displayName = b.displayName;
        this.searchSpaceKeys = b.searchSpaceKeys;
        this.includeAttachments = b.includeAttachments;
    }

    /** Eine neue Quelle des Typs mit leeren Feldern und den Standardwerten des Loaders. */
    public static Builder builder(String id, String type) {
        return new Builder(id, type);
    }

    public Builder toBuilder() {
        return new Builder(id, type).url(url).credentialRef(credentialRef).startPoints(startPoints)
                .maxDepth(maxDepth).maxResources(maxResources).requiresLogin(requiresLogin).siteKey(siteKey)
                .displayName(displayName).searchSpaceKeys(searchSpaceKeys).includeAttachments(includeAttachments);
    }

    public String id() {
        return id;
    }

    public String type() {
        return type;
    }

    public boolean isMediaWiki() {
        return TYPE_MEDIAWIKI.equals(type);
    }

    public boolean isConfluence() {
        return TYPE_CONFLUENCE.equals(type);
    }

    /** API-URL (MediaWiki) bzw. Basis-URL (Confluence). */
    public String url() {
        return url;
    }

    public String credentialRef() {
        return credentialRef;
    }

    /** Kommagetrennte Startpunkte des Crawls. */
    public String startPoints() {
        return startPoints;
    }

    public String maxDepth() {
        return maxDepth;
    }

    public String maxResources() {
        return maxResources;
    }

    public boolean requiresLogin() {
        return requiresLogin;
    }

    public String siteKey() {
        return siteKey;
    }

    public String displayName() {
        return displayName;
    }

    public String searchSpaceKeys() {
        return searchSpaceKeys;
    }

    public boolean includeAttachments() {
        return includeAttachments;
    }

    @Override
    public String toString() {
        return "SourceForm[" + id + ", " + type + "]";
    }

    public static final class Builder {
        private final String id;
        private final String type;
        private String url = "";
        private String credentialRef = "";
        private String startPoints = "";
        private String maxDepth = "1";
        private String maxResources = "";
        private boolean requiresLogin;
        private String siteKey = "";
        private String displayName = "";
        private String searchSpaceKeys = "";
        private boolean includeAttachments;

        private Builder(String id, String type) {
            this.id = SettingsForm.text(id);
            this.type = SettingsForm.text(type);
        }

        public Builder url(String value) {
            this.url = SettingsForm.text(value);
            return this;
        }

        public Builder credentialRef(String value) {
            this.credentialRef = SettingsForm.text(value);
            return this;
        }

        public Builder startPoints(String value) {
            this.startPoints = SettingsForm.text(value);
            return this;
        }

        public Builder maxDepth(String value) {
            this.maxDepth = SettingsForm.text(value);
            return this;
        }

        public Builder maxResources(String value) {
            this.maxResources = SettingsForm.text(value);
            return this;
        }

        public Builder requiresLogin(boolean value) {
            this.requiresLogin = value;
            return this;
        }

        public Builder siteKey(String value) {
            this.siteKey = SettingsForm.text(value);
            return this;
        }

        public Builder displayName(String value) {
            this.displayName = SettingsForm.text(value);
            return this;
        }

        public Builder searchSpaceKeys(String value) {
            this.searchSpaceKeys = SettingsForm.text(value);
            return this;
        }

        public Builder includeAttachments(boolean value) {
            this.includeAttachments = value;
            return this;
        }

        public SourceForm build() {
            return new SourceForm(this);
        }
    }
}

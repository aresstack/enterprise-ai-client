package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * What a fat jar is for the patch chain: its {@code Implementation-Version} and its
 * {@link JarDigests#contentSha256 content digest}. Patches connect by both, never by the version alone (#105).
 */
public final class JarIdentity {

    private final String version;
    private final String contentSha256;

    public JarIdentity(String version, String contentSha256) {
        this.version = version;
        this.contentSha256 = contentSha256;
    }

    /** Reads version and content digest of {@code jar}. */
    public static JarIdentity of(File jar) throws JarPatchException {
        try {
            return new JarIdentity(manifestVersion(jar), JarDigests.contentSha256(jar));
        } catch (IOException ex) {
            throw new JarPatchException("Die Jar-Datei " + jar.getName() + " ist nicht lesbar: " + ex.getMessage(),
                    ex);
        }
    }

    /** {@code Implementation-Version} of the manifest, or an empty string without one. */
    static String manifestVersion(File jar) throws IOException {
        try (JarFile file = new JarFile(jar)) {
            Manifest manifest = file.getManifest();
            String version = manifest == null ? null
                    : manifest.getMainAttributes().getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            return version == null ? "" : version.trim();
        }
    }

    public String version() {
        return version;
    }

    public String contentSha256() {
        return contentSha256;
    }

    /** Same version and same content. */
    public boolean matches(String otherVersion, String otherContentSha256) {
        return version.equals(otherVersion) && contentSha256.equals(otherContentSha256);
    }

    @Override
    public String toString() {
        return version + " (" + shortSha(contentSha256) + ")";
    }

    static String shortSha(String sha) {
        return sha.length() > 12 ? sha.substring(0, 12) + "…" : sha;
    }
}

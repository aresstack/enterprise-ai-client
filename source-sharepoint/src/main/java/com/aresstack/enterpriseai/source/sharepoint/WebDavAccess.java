package com.aresstack.enterpriseai.source.sharepoint;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeSourceId;
import com.aresstack.enterpriseai.domain.security.SecretRef;
import com.aresstack.enterpriseai.security.api.SecretMaterial;
import com.aresstack.enterpriseai.security.api.SecretProvider;
import com.aresstack.enterpriseai.security.api.SecretUnavailableException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException;
import com.aresstack.enterpriseai.source.api.KnowledgeSourceException.Kind;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Zugang zur WebDAV-Freigabe einer SharePoint-Bibliothek, Reihenfolge aus MainframeMate
 * {@code SharePointAuthenticator.ensureAuthenticated}:
 * <ol>
 *   <li>Probe: Verzeichnis lesbar (Windows hat schon eine Sitzung).</li>
 *   <li>SSO: {@code net use \\host@SSL\DavWWWRoot} ohne Zugangsdaten (Kerberos/NTLM des angemeldeten Benutzers).</li>
 *   <li>Nur mit {@code credentialRef}: {@code net use ... /user:<Benutzer> <Passwort>} mit dem KeePass-Eintrag.
 *       Das Passwort steht dabei, wie in MainframeMate, auf der Befehlszeile von {@code net}; eine API ohne
 *       Befehlszeile bietet Windows dafür nicht.</li>
 * </ol>
 * Kein Anmeldedialog: ohne SSO und ohne KeePass-Eintrag ist die Quelle nicht erreichbar.
 */
final class WebDavAccess {

    private static final Logger LOG = Logger.getLogger(WebDavAccess.class.getName());
    private static final long COMMAND_TIMEOUT_SECONDS = 60;

    private final KnowledgeSourceId sourceId;
    private final Path root;
    private final String shareRoot;
    private final SecretRef credentialRef;
    private final SecretProvider secrets;

    WebDavAccess(KnowledgeSourceId sourceId, Path root, String shareRoot, SecretRef credentialRef,
                 SecretProvider secrets) {
        this.sourceId = sourceId;
        this.root = root;
        this.shareRoot = shareRoot;
        this.credentialRef = credentialRef;
        this.secrets = secrets;
    }

    /** Stellt sicher, dass das Wurzelverzeichnis lesbar ist; sonst {@link KnowledgeSourceException}. */
    synchronized void ensure() throws KnowledgeSourceException {
        if (Files.isDirectory(root)) {
            return;
        }
        if (File.separatorChar != '\\') {
            throw new KnowledgeSourceException(Kind.UNAVAILABLE, "SharePoint-Quelle " + sourceId
                    + ": WebDAV über UNC-Pfade geht nur unter Windows (WebClient-Dienst)");
        }
        if (netUse(Arrays.asList("net", "use", shareRoot)) && Files.isDirectory(root)) {
            LOG.fine("SharePoint " + sourceId + ": SSO über net use");
            return;
        }
        if (credentialRef == null) {
            throw new KnowledgeSourceException(Kind.ACCESS_DENIED, "SharePoint-Quelle " + sourceId + ": " + root
                    + " nicht erreichbar (SSO fehlgeschlagen, kein KeePass-Eintrag; WebClient-Dienst gestartet?)");
        }
        boolean connected;
        try {
            connected = secrets.withSecret(credentialRef, (SecretMaterial material) -> {
                char[] password = material.copySecret();
                try {
                    netUse(Arrays.asList("net", "use", shareRoot, "/delete", "/y"));
                    List<String> command = new ArrayList<String>(Arrays.asList("net", "use", shareRoot,
                            "/user:" + material.principal(), new String(password)));
                    return netUse(command);
                } finally {
                    Arrays.fill(password, '\0');
                }
            });
        } catch (SecretUnavailableException e) {
            Kind kind = e.reason() == SecretUnavailableException.Reason.NOT_AVAILABLE ? Kind.UNAVAILABLE : Kind.ACCESS_DENIED;
            throw new KnowledgeSourceException(kind, "Anmeldedaten für SharePoint-Quelle " + sourceId
                    + " nicht verfügbar (" + e.reason() + ", " + credentialRef + ")");
        }
        if (!connected || !Files.isDirectory(root)) {
            throw new KnowledgeSourceException(Kind.ACCESS_DENIED, "SharePoint-Quelle " + sourceId + ": Anmeldung an "
                    + shareRoot + " fehlgeschlagen oder " + root + " nicht gefunden");
        }
        LOG.fine("SharePoint " + sourceId + ": angemeldet mit KeePass-Eintrag");
    }

    /** Führt {@code net use} aus; protokolliert nie die Befehlszeile (Passwort). */
    private boolean netUse(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            process.getOutputStream().close();
            String output = drain(process.getInputStream());
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                LOG.warning("SharePoint " + sourceId + ": net use antwortet nicht");
                return false;
            }
            if (process.exitValue() != 0) {
                LOG.fine("SharePoint " + sourceId + ": net use exit=" + process.exitValue() + ": " + output.trim());
                return false;
            }
            return true;
        } catch (IOException e) {
            LOG.log(Level.FINE, "SharePoint " + sourceId + ": net use nicht ausführbar", e);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static String drain(InputStream in) throws IOException {
        try (InputStream input = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), Charset.defaultCharset());
        }
    }
}

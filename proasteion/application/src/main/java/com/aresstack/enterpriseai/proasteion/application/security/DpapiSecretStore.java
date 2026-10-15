package com.aresstack.enterpriseai.proasteion.application.security;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

/**
 * „API-Key speichern“ unter Windows: das Geheimnis wird mit der Windows Data Protection API (DPAPI, Bereich des
 * angemeldeten Benutzers) verschlüsselt und nur so in einer Datei im Anwendungsverzeichnis abgelegt; entschlüsseln
 * kann es nur derselbe Windows-Benutzer. Java 8 hat keinen eigenen Zugang zu DPAPI; die Verschlüsselung leistet das
 * mitgelieferte PowerShell ({@code ConvertFrom-SecureString}), das Geheimnis läuft dabei nur über stdin, nie über
 * die Befehlszeile. Auf anderen Systemen ist der Store {@linkplain #available() nicht verfügbar} und speichert nichts.
 *
 * <p>Dateiname ist ein Hash der Referenz (der Name verrät nichts); die Datei enthält nur den DPAPI-Block.
 */
public final class DpapiSecretStore implements ProtectedSecretStore {

    private static final Logger LOG = Logger.getLogger(DpapiSecretStore.class.getName());

    /** Verzeichnis unter dem Anwendungsverzeichnis. */
    public static final String DIRECTORY_NAME = "manual-secrets";
    private static final String SUFFIX = ".dpapi";

    /** Schützt bzw. entschlüsselt Bytes; produktiv PowerShell/DPAPI, in Tests ein Ersatz. */
    public interface Protector {
        String protect(byte[] plain) throws Exception;

        byte[] unprotect(String blob) throws Exception;
    }

    private final Path directory;
    private final Protector protector;
    private final boolean available;
    private final String reason;

    private DpapiSecretStore(Path directory, Protector protector, boolean available, String reason) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory.toAbsolutePath();
        this.protector = protector;
        this.available = available && protector != null;
        this.reason = reason;
    }

    /** Produktiv: unter Windows mit DPAPI, sonst nicht verfügbar. */
    public static ProtectedSecretStore forDirectory(Path directory) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
        return windows ? new DpapiSecretStore(directory, new PowerShellDpapi(), true,
                "Der API-Key wird mit Windows (DPAPI) verschlüsselt für dieses Benutzerkonto gespeichert.")
                : ProtectedSecretStore.unavailable("Dauerhaftes Speichern ist nur unter Windows (DPAPI) möglich; "
                + "der API-Key gilt hier nur für diese Sitzung.");
    }

    /** Für Tests: mit eigenem Schutz. */
    public static DpapiSecretStore withProtector(Path directory, Protector protector) {
        return new DpapiSecretStore(directory, protector, true, "geschützt (Test)");
    }

    @Override
    public boolean available() {
        return available;
    }

    @Override
    public String describe() {
        return reason;
    }

    @Override
    public synchronized void store(String ref, char[] secret) throws Exception {
        if (!available) {
            throw new IllegalStateException(reason);
        }
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("secret must not be empty");
        }
        byte[] plain = utf8(secret);
        String blob;
        try {
            blob = protector.protect(plain);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        if (blob == null || blob.trim().isEmpty()) {
            throw new IOException("Verschlüsselung lieferte kein Ergebnis");
        }
        Files.createDirectories(directory);
        Path target = file(ref);
        Path temp = Files.createTempFile(directory, "secret", ".tmp");
        try {
            Files.write(temp, blob.trim().getBytes(StandardCharsets.US_ASCII));
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
        LOG.info("Manueller API-Key geschützt gespeichert");
    }

    @Override
    public synchronized char[] load(String ref) throws Exception {
        Path file = file(ref);
        if (!available || !Files.isRegularFile(file)) {
            return null;
        }
        String blob = new String(Files.readAllBytes(file), StandardCharsets.US_ASCII).trim();
        byte[] plain = protector.unprotect(blob);
        try {
            CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
            char[] secret = Arrays.copyOf(chars.array(), chars.limit());
            Arrays.fill(chars.array(), '\0');
            return secret;
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    @Override
    public synchronized boolean contains(String ref) {
        return available && Files.isRegularFile(file(ref));
    }

    @Override
    public synchronized void remove(String ref) throws Exception {
        Path file = file(ref);
        if (Files.isRegularFile(file)) {
            long size = Files.size(file);
            Files.write(file, new byte[(int) Math.min(size, 1 << 16)]); // nur der verschlüsselte Block, trotzdem leeren
            Files.delete(file);
            LOG.info("Gespeicherter manueller API-Key entfernt");
        }
    }

    Path file(String ref) {
        return directory.resolve(hash(ManualCredentials.label(ref).toLowerCase(Locale.ROOT)) + SUFFIX);
    }

    private static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 16; i++) {
                hex.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] utf8(char[] secret) {
        ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(secret));
        byte[] bytes = Arrays.copyOf(buffer.array(), buffer.limit());
        Arrays.fill(buffer.array(), (byte) 0);
        return bytes;
    }

    @Override
    public String toString() {
        return "DpapiSecretStore[" + (available ? "available" : "unavailable") + "]";
    }

    /** DPAPI über das Windows-PowerShell; ein- und ausgegeben wird Base64 über stdin/stdout. */
    static final class PowerShellDpapi implements Protector {

        private static final String PROTECT = "$i=[Console]::In.ReadLine();"
                + "$p=[Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($i));"
                + "$s=ConvertTo-SecureString -String $p -AsPlainText -Force;$p=$null;"
                + "[Console]::Out.Write((ConvertFrom-SecureString -SecureString $s))";
        private static final String UNPROTECT = "$i=[Console]::In.ReadLine();"
                + "$s=ConvertTo-SecureString -String $i;"
                + "$b=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($s);"
                + "try{$p=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($b);"
                + "[Console]::Out.Write([Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($p)))}"
                + "finally{[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($b)}";

        @Override
        public String protect(byte[] plain) throws Exception {
            byte[] input = java.util.Base64.getEncoder().encode(plain);
            try {
                return new String(run(PROTECT, input), StandardCharsets.US_ASCII).trim();
            } finally {
                Arrays.fill(input, (byte) 0);
            }
        }

        @Override
        public byte[] unprotect(String blob) throws Exception {
            byte[] output = run(UNPROTECT, blob.getBytes(StandardCharsets.US_ASCII));
            try {
                return java.util.Base64.getDecoder().decode(new String(output, StandardCharsets.US_ASCII).trim());
            } finally {
                Arrays.fill(output, (byte) 0);
            }
        }

        private static byte[] run(String script, byte[] input) throws Exception {
            Process process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                    "-ExecutionPolicy", "Bypass", "-Command", script).redirectErrorStream(false).start();
            try {
                OutputStream stdin = process.getOutputStream();
                stdin.write(input);
                stdin.write('\n');
                stdin.close();
                byte[] output = readAll(process.getInputStream());
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    throw new IOException("DPAPI antwortet nicht");
                }
                if (process.exitValue() != 0 || output.length == 0) {
                    // stderr kann Teile der Eingabe enthalten: nur der Code wandert in die Meldung
                    throw new IOException("DPAPI fehlgeschlagen (Code " + process.exitValue() + ")");
                }
                return output;
            } finally {
                process.destroy();
            }
        }

        private static byte[] readAll(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}

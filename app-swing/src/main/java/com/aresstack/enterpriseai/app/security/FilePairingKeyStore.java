package com.aresstack.enterpriseai.app.security;

import com.aresstack.enterpriseai.security.keepassrpc.KeePassPairingKeyStore;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Dauerhafte Ablage des KeePassRPC-Pairing-Schlüssels in einer Datei im Benutzerverzeichnis, damit nach einem
 * Neustart kein neues Pairing nötig ist (MainframeMate hielt ihn in den Settings).
 *
 * <p>Schutz: Die Datei wird mit Rechten nur für den Besitzer angelegt ({@code rw-------} auf POSIX; auf Windows
 * die Lese-/Schreibrechte über {@code java.io.File}, soweit das Dateisystem sie umsetzt), atomar ersetzt und beim
 * Verwerfen vor dem Löschen überschrieben. Der Schlüssel erscheint nie in Logs; Fehler werden ohne Inhalt
 * geloggt und machen das Pairing für diese Sitzung nicht unbrauchbar (Speicherkopie bleibt beim Adapter).
 */
public final class FilePairingKeyStore implements KeePassPairingKeyStore {

    private static final Logger LOG = Logger.getLogger(FilePairingKeyStore.class.getName());

    private final Path file;

    public FilePairingKeyStore(Path file) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        this.file = file.toAbsolutePath();
    }

    public Path file() {
        return file;
    }

    @Override
    public synchronized char[] load() {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Pairing-Schlüssel nicht lesbar: " + file + " (" + e.getClass().getSimpleName() + ")");
            return null;
        }
        try {
            CharBuffer chars = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(bytes));
            char[] all = new char[chars.remaining()];
            chars.get(all);
            char[] trimmed = trim(all);
            Arrays.fill(all, '\0');
            clearBuffer(chars);
            return trimmed.length == 0 ? null : trimmed;
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public synchronized void save(char[] sessionKey) {
        if (sessionKey == null || sessionKey.length == 0) {
            clear();
            return;
        }
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(sessionKey));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        clearBuffer(encoded);
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.deleteIfExists(temp);
            Files.write(temp, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            restrictToOwner(temp);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictToOwner(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Pairing-Schlüssel konnte nicht gespeichert werden: " + file + " ("
                    + e.getClass().getSimpleName() + "); nach dem nächsten Start ist ein neues Pairing nötig");
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // best effort
            }
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    @Override
    public synchronized void clear() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            long size = Files.size(file);
            if (size > 0 && size < 1024 * 1024) {
                Files.write(file, new byte[(int) size], StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException ignored) {
            // Überschreiben ist best effort; gelöscht wird in jedem Fall
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Pairing-Schlüssel konnte nicht gelöscht werden: " + file + " ("
                    + e.getClass().getSimpleName() + ")");
        }
    }

    /** Rechte nur für den Besitzer, soweit das Dateisystem es erlaubt; schlägt nie fehl. */
    static void restrictToOwner(Path path) {
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            try {
                Set<PosixFilePermission> owner = EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
                Files.setPosixFilePermissions(path, owner);
                return;
            } catch (IOException | UnsupportedOperationException e) {
                LOG.log(Level.FINE, "POSIX-Rechte nicht setzbar: " + e.getClass().getSimpleName());
            }
        }
        java.io.File file = path.toFile();
        file.setReadable(false, false);
        file.setReadable(true, true);
        file.setWritable(false, false);
        file.setWritable(true, true);
        file.setExecutable(false, false);
    }

    private static char[] trim(char[] chars) {
        int start = 0;
        int end = chars.length;
        while (start < end && Character.isWhitespace(chars[start])) {
            start++;
        }
        while (end > start && Character.isWhitespace(chars[end - 1])) {
            end--;
        }
        return Arrays.copyOfRange(chars, start, end);
    }

    private static void clearBuffer(ByteBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
    }

    private static void clearBuffer(CharBuffer buffer) {
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), '\0');
        }
    }

    @Override
    public String toString() {
        return "FilePairingKeyStore[" + file + ", ***]";
    }
}

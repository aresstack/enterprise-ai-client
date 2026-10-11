package com.aresstack.enterpriseai.proasteion.application.knowledge;

import com.aresstack.enterpriseai.astu.acropolis.navigation.DocumentNavigationStore;
import com.aresstack.enterpriseai.astu.navigation.DocumentNavigation;
import com.aresstack.enterpriseai.astu.navigation.NavigationSection;
import com.aresstack.enterpriseai.astu.navigation.SourcePosition;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link DocumentNavigationStore} als eine Datei je Ressource im Verzeichnis {@code navigation} neben der
 * Konfiguration: Name = SHA-256 der Resource-ID, Inhalt = die Navigation der zuletzt analysierten Fassung. Eine neue
 * Fassung ersetzt die Datei; eine fremde Capture, ein anderes Format oder eine kaputte Datei gilt als nicht
 * vorhanden und wird gelöscht (kein Produktivbetrieb: verwerfen statt migrieren).
 */
public final class FileDocumentNavigationStore implements DocumentNavigationStore {

    /** Formatkennung; ein anderer Wert verwirft die Datei. */
    static final int FORMAT = 0x4e415631; // "NAV1"

    private final Path directory;

    public FileDocumentNavigationStore(Path directory) {
        if (directory == null) {
            throw new IllegalArgumentException("directory must not be null");
        }
        this.directory = directory;
    }

    @Override
    public DocumentNavigation load(String resourceId, String captureId) {
        Path file = fileOf(resourceId);
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (InputStream raw = Files.newInputStream(file);
             DataInputStream in = new DataInputStream(new BufferedInputStream(raw))) {
            if (in.readInt() != FORMAT) {
                throw new IOException("unknown format");
            }
            String storedResource = in.readUTF();
            String storedCapture = in.readUTF();
            if (!storedResource.equals(resourceId) || !storedCapture.equals(captureId)) {
                return null; // ältere Fassung: wird beim nächsten Speichern ersetzt
            }
            String title = in.readUTF();
            DocumentNavigation.Analysis analysis = DocumentNavigation.Analysis.valueOf(in.readUTF());
            String reason = in.readUTF();
            List<NavigationSection> chapters = readSections(in, resourceId, captureId);
            return new DocumentNavigation(resourceId, captureId, title, chapters, analysis, reason);
        } catch (IOException | RuntimeException unreadable) {
            delete(file);
            return null;
        }
    }

    @Override
    public void save(DocumentNavigation navigation) {
        if (navigation == null) {
            return;
        }
        Path file = fileOf(navigation.resourceId());
        Path temp = null;
        try {
            Files.createDirectories(directory);
            temp = Files.createTempFile(directory, "nav", ".tmp");
            try (OutputStream raw = Files.newOutputStream(temp);
                 DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw))) {
                out.writeInt(FORMAT);
                out.writeUTF(navigation.resourceId());
                out.writeUTF(navigation.captureId());
                out.writeUTF(navigation.title());
                out.writeUTF(navigation.analysis().name());
                out.writeUTF(navigation.fallbackReason());
                writeSections(out, navigation.chapters());
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException failed) {
            // nicht gespeichert: nach dem Neustart wird neu analysiert
            if (temp != null) {
                delete(temp);
            }
        }
    }

    private static void writeSections(DataOutputStream out, List<NavigationSection> sections) throws IOException {
        out.writeInt(sections.size());
        for (NavigationSection section : sections) {
            out.writeUTF(section.id());
            out.writeUTF(section.label());
            out.writeUTF(section.origin().name());
            out.writeInt(section.level());
            out.writeInt(section.position().start());
            out.writeInt(section.position().end());
            out.writeUTF(section.position().anchor());
            out.writeBoolean(section.parentId() != null);
            if (section.parentId() != null) {
                out.writeUTF(section.parentId());
            }
            writeSections(out, section.children());
        }
    }

    private static List<NavigationSection> readSections(DataInputStream in, String resourceId, String captureId)
            throws IOException {
        int count = in.readInt();
        if (count < 0 || count > 100_000) {
            throw new IOException("implausible section count " + count);
        }
        List<NavigationSection> sections = new ArrayList<NavigationSection>(count);
        for (int i = 0; i < count; i++) {
            String id = in.readUTF();
            String label = in.readUTF();
            NavigationSection.Origin origin = NavigationSection.Origin.valueOf(in.readUTF());
            int level = in.readInt();
            int start = in.readInt();
            int end = in.readInt();
            String anchor = in.readUTF();
            String parentId = in.readBoolean() ? in.readUTF() : null;
            List<NavigationSection> children = readSections(in, resourceId, captureId);
            sections.add(new NavigationSection(id, label, origin, level,
                    new SourcePosition(resourceId, captureId, start, end, anchor), parentId, children));
        }
        return sections;
    }

    private Path fileOf(String resourceId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(resourceId.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return directory.resolve(hex + ".nav");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // bleibt liegen; wird beim nächsten Speichern ersetzt
        }
    }
}

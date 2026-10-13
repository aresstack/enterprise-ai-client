package com.aresstack.enterpriseai.proasteion.application.update;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * SHA-256 of a file and of a jar's content.
 *
 * <p>The content digest covers every entry name and its bytes, sorted by name. It does not depend on compression
 * level, timestamps or entry order, so the patcher can prove that the jar it wrote holds exactly the entries of the
 * released target jar even when its zlib compresses differently from the build machine's. It is the one identity
 * patches are chained by (#105): a jar the updater wrote has the same content digest as the released one, while
 * its file digest may differ.
 */
public final class JarDigests {

    private static final int BUFFER = 64 * 1024;

    private JarDigests() {
    }

    /** Hex SHA-256 of the whole file. */
    public static String fileSha256(File file) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = new FileInputStream(file)) {
            update(digest, in);
        }
        return hex(digest.digest());
    }

    /** Hex SHA-256 over the sorted entries (name, NUL, SHA-256 of the bytes) of a jar. */
    public static String contentSha256(File jar) throws IOException {
        try (ZipFile zip = new ZipFile(jar)) {
            List<String> names = new ArrayList<String>();
            for (ZipEntry entry : Collections.list(zip.entries())) {
                names.add(entry.getName());
            }
            Collections.sort(names);
            MessageDigest digest = sha256();
            for (String name : names) {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(entrySha256(zip, zip.getEntry(name)));
            }
            return hex(digest.digest());
        }
    }

    /** Raw SHA-256 of one entry's bytes. */
    static byte[] entrySha256(ZipFile zip, ZipEntry entry) throws IOException {
        MessageDigest digest = sha256();
        try (InputStream in = zip.getInputStream(entry)) {
            update(digest, in);
        }
        return digest.digest();
    }

    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            text.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return text.toString();
    }

    private static void update(MessageDigest digest, InputStream in) throws IOException {
        byte[] buffer = new byte[BUFFER];
        int read;
        while ((read = in.read(buffer)) > 0) {
            digest.update(buffer, 0, read);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", ex);
        }
    }
}

package com.aresstack.enterpriseai.resource.api;

import com.aresstack.enterpriseai.domain.resource.ResourceFingerprint;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 content hashing utility for change detection and deduplication.
 *
 * <p>Provides a reusable, storage-agnostic hashing facility that can be
 * shared across modules (tamias, anagraphai, pinakes) for computing
 * {@link ResourceFingerprint} instances and {@link ResourceDigest} values.
 *
 * <p>This class is stateless and thread-safe.
 */
public final class ContentHasher {

    private static final String ALGORITHM = "SHA-256";
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private ContentHasher() {
        // utility class
    }

    /**
     * Computes a SHA-256 fingerprint for the given bytes.
     *
     * @param data the content bytes
     * @return a {@link ResourceFingerprint} with algorithm "SHA-256"
     * @throws IllegalStateException if SHA-256 is not available on this JVM
     */
    public static ResourceFingerprint fingerprint(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        byte[] hash = sha256(data);
        return new ResourceFingerprint(ALGORITHM, bytesToHex(hash));
    }

    /**
     * Computes a SHA-256 fingerprint for the given text using UTF-8 encoding.
     *
     * @param text the text content
     * @return a {@link ResourceFingerprint} with algorithm "SHA-256"
     * @throws IllegalStateException if SHA-256 is not available on this JVM
     */
    public static ResourceFingerprint fingerprint(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return fingerprint(text.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Computes a full {@link ResourceDigest} for the given bytes.
     *
     * @param data the content bytes
     * @return a digest containing both the fingerprint and size
     */
    public static ResourceDigest digest(byte[] data) {
        return new ResourceDigest(fingerprint(data), data.length);
    }

    /**
     * Computes the SHA-256 hex string for raw bytes.
     *
     * @param data the content bytes
     * @return the hex-encoded hash
     */
    public static String hash(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return bytesToHex(sha256(data));
    }

    /**
     * Computes the SHA-256 hex string for text using UTF-8 encoding.
     *
     * @param text the text content
     * @return the hex-encoded hash
     */
    public static String hash(String text) {
        if (text == null) {
            throw new IllegalArgumentException("text must not be null");
        }
        return hash(text.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] sha256(byte[] data) {
        try {
            MessageDigest md = MessageDigest.getInstance(ALGORITHM);
            return md.digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hex[i * 2] = HEX_CHARS[v >>> 4];
            hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(hex);
    }

}

package com.aresstack.enterpriseai.knowledge.lucene;

import com.aresstack.enterpriseai.domain.knowledge.KnowledgeChunk;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeResource;
import com.aresstack.enterpriseai.domain.knowledge.KnowledgeRevision;

import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Stempel eines indexierten Chunks: SHA-256 (hex) über Text samt Überschriften und die Ressourcen-Revision.
 * Vektor- und Textindex leiten ihn unabhängig voneinander ab; stimmen beide überein, stammen Vektor und
 * gespeicherter Text aus derselben Fassung.
 */
final class EntryStamp {

    private static final Charset UTF_8 = Charset.forName("UTF-8");

    private EntryStamp() {
    }

    static String of(KnowledgeResource resource, KnowledgeChunk chunk) {
        KnowledgeRevision revision = resource.revision();
        String material = chunk.textWithHeading() + '\u0000' + revision.version() + '\u0000'
                + (revision.modifiedAt().isPresent() ? revision.modifiedAt().get().toString() : "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(material.getBytes(UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 fehlt in dieser JVM", ex);
        }
    }
}

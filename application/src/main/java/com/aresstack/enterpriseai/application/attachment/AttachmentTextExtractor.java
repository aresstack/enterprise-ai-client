package com.aresstack.enterpriseai.application.attachment;

/** Port: Klartext aus einer Datei (PDF, Office, HTML, Mail, Text …). */
public interface AttachmentTextExtractor {

    /** @throws AttachmentException wenn das Format nicht unterstützt wird oder die Extraktion scheitert */
    String extractText(String fileName, byte[] content);
}

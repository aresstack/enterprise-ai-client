/**
 * Dateianhänge eines Chats: Ablage ({@link com.aresstack.enterpriseai.application.attachment.AttachmentStore})
 * und Textextraktion ({@link com.aresstack.enterpriseai.application.attachment.AttachmentTextExtractor}) als
 * Ports, dazu die Werkzeuge {@code read_attachment} und {@code search_attachment}. Anhänge bleiben lokal; nur
 * Werkzeugergebnisse gehen an das Modell, der extrahierte Text erscheint nie als Nutzernachricht.
 */
package com.aresstack.enterpriseai.application.attachment;

/**
 * Chat-Historie (aus askai-java8 arch, {@code history}): gespeicherte Chats ({@link
 * com.aresstack.enterpriseai.astu.acropolis.history.ChatRecord}) mit ihren Nachrichten und den Metadaten ihrer
 * Anhänge, abgelegt über den Port {@link com.aresstack.enterpriseai.astu.acropolis.history.ChatHistoryStore}. Die
 * Bytes der Anhänge liegen im {@link com.aresstack.enterpriseai.astu.acropolis.attachment.AttachmentStore} derselben
 * Unterhaltung; ein Datensatz hält nur Kennung, Name und Größe.
 */
package com.aresstack.enterpriseai.astu.acropolis.history;

/**
 * Anbindung der Chat-Oberfläche an die Use Cases: übersetzt Bedienabsichten der Shell in Aufrufe des
 * {@code ChatService} bzw. {@code RagChatUseCase} (AP22: RAG-Schalter, Quellen, Hinweise) und deren
 * Turn-Callbacks zurück in das Presentation-Model, auf dem UI-Thread; {@code KnowledgeIndexingBinding} speist die
 * Statuszeile der Wissensbasis aus dem {@code IndexKnowledgeUseCase}.
 *
 * <p>Liegt bewusst außerhalb von {@code app.ui}: Nur hier sieht app-swing Use-Case- und Port-Typen
 * (Fehlerklassen des Chat-Ports); die Swing-Ansichten bleiben davon frei (siehe ComicUiBoundaryTest).
 */
package com.aresstack.enterpriseai.app.chat;

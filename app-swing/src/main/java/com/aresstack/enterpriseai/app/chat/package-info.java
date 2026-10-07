/**
 * Anbindung der Chat-Oberfläche an den Chat-Use-Case: übersetzt Bedienabsichten der Shell in Aufrufe des
 * {@code ChatService} und dessen Turn-Callbacks zurück in das Presentation-Model, auf dem UI-Thread.
 *
 * <p>Liegt bewusst außerhalb von {@code app.ui}: Nur hier sieht app-swing Use-Case- und Port-Typen
 * (Fehlerklassen des Chat-Ports); die Swing-Ansichten bleiben davon frei (siehe ComicUiBoundaryTest).
 */
package com.aresstack.enterpriseai.app.chat;

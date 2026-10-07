/**
 * Die Chat-Oberfläche im Comic-Stil (AP4): ein headless testbares Presentation-Model
 * ({@link com.aresstack.enterpriseai.app.ui.chat.ChatShellModel}) und die Swing-Ansichten, die es darstellen.
 *
 * <p>Die Ansichten melden Bedienabsichten (Senden, Stop) über
 * {@link com.aresstack.enterpriseai.app.ui.chat.ChatShellActions}; wer sie erfüllt (Fake im Test, später der
 * Chat-Use-Case aus {@code application}), ist der Oberfläche unbekannt. Kein HTTP, kein Adapter.
 */
package com.aresstack.enterpriseai.app.ui.chat;

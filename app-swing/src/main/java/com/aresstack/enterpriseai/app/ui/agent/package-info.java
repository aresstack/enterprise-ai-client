/**
 * Modus-Umschaltung Chat/Agent der Comic-Shell (AP21). Reine Oberfläche: ein Presentation-Model ohne Swing
 * ({@link com.aresstack.enterpriseai.app.ui.agent.ShellModeModel}) und die Swing-Ansichten dazu.
 *
 * <p>Jeder Modus hat seine eigene {@code ChatShellPanel}-Instanz mit eigenem {@code ChatShellModel}; beim
 * Umschalten wird nur die sichtbare Karte gewechselt, Verläufe und laufende Antworten bleiben getrennt. Kein
 * ACP, kein MCP, kein Use Case: die Anbindung liegt in {@code app.agent} bzw. {@code app.chat}.
 */
package com.aresstack.enterpriseai.app.ui.agent;

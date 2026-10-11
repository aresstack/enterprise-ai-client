/**
 * Der eingebaute Recherche-Agent als eigener Prozess über ACP (askai-java8 arch: {@code ResearchAgentMain}).
 * STDOUT gehört ausschließlich ACP; Logs gehen über java.util.logging auf STDERR. Zugangsdaten bekommt der
 * Prozess nie: das Hauptmodell erreicht er über das Werkzeug {@code main_model_complete} des Hosts.
 */
package com.aresstack.enterpriseai.acp.research;

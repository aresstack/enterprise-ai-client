package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AcpConnection;
import com.aresstack.enterpriseai.application.agent.AgentLauncher;
import com.aresstack.enterpriseai.chat.api.ChatCompletionPort;
import com.aresstack.enterpriseai.domain.chat.ChatOptions;
import com.aresstack.enterpriseai.research.runtime.team.PhaseAssistantProfileRegistry;
import com.aresstack.enterpriseai.research.runtime.team.PhaseContextAssembler;
import com.aresstack.enterpriseai.research.runtime.team.MainModelChat;
import com.aresstack.enterpriseai.research.runtime.team.ResearchTeamAgent;

import java.util.logging.Logger;

/**
 * Startet den eingebauten Recherche-Agenten (askai-java8 arch: {@code ResearchAgentMain}) im Prozess: jede
 * Verbindung bekommt eine frische Gesprächs-Engine ({@link ResearchTeamAgent}) auf dem Hauptmodell des Clients;
 * der Weidezaun des aktuellen Projekts ({@link ResearchAgentHost}) geht vor jedem Turn als maßgeblicher Umfang
 * in den Kontext.
 *
 * <p>Mit Konzeptwerkzeugen (Konzeptpapier mit Inseln): vor jeder Inferenz sieht das Modell das gespeicherte
 * Konzept (CURRENT_CONCEPT). „Immer Suchvorschläge anbieten“ ist wie in askai standardmäßig aus.
 */
public final class ResearchAgentLauncher implements AgentLauncher {

    private static final Logger LOG = Logger.getLogger(ResearchAgentLauncher.class.getName());

    private final ResearchAgentHost host;
    private final ChatCompletionPort chat;
    private final ChatOptions defaults;

    public ResearchAgentLauncher(ResearchAgentHost host, ChatCompletionPort chat, ChatOptions defaults) {
        if (host == null || chat == null) {
            throw new IllegalArgumentException("host and chat must not be null");
        }
        this.host = host;
        this.chat = chat;
        this.defaults = defaults;
    }

    @Override
    public AcpConnection launch() {
        final ResearchAgentConnection[] connection = new ResearchAgentConnection[1];
        MainModelChat mainModelChat = new ChatPortMainModelChat(chat, defaults);
        ResearchTeamAgent teamAgent = new ResearchTeamAgent(mainModelChat,
                PhaseAssistantProfileRegistry.defaults(false, true),
                new PhaseContextAssembler(new PhaseContextAssembler.CurrentLanguage() {
                    @Override
                    public String displayName() {
                        return "German";
                    }
                }).withCurrentConcept(new PhaseContextAssembler.CurrentConcept() {
                    @Override
                    public String rendered() {
                        ResearchAgentConnection current = connection[0];
                        return current == null ? "" : current.currentConceptRendered();
                    }
                }).withCurrentScope(new PhaseContextAssembler.CurrentScope() {
                    @Override
                    public String rendered() {
                        return host.renderedFence();
                    }
                }));
        teamAgent.setDiagnostics(new java.util.function.Consumer<String>() {
            @Override
            public void accept(String line) {
                LOG.info("[research-agent] " + line);
            }
        });
        connection[0] = new ResearchAgentConnection(host, teamAgent, mainModelChat);
        return connection[0];
    }
}

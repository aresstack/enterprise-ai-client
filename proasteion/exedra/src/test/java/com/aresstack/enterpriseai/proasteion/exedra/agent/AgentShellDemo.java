package com.aresstack.enterpriseai.proasteion.exedra.agent;

import com.aresstack.enterpriseai.proasteion.application.agent.AcpAgentLauncher;
import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.proasteion.exedra.chat.ChatServiceBinding;
import com.aresstack.enterpriseai.proasteion.exedra.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.proasteion.exedra.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.astu.acropolis.agent.AgentService;
import com.aresstack.enterpriseai.astu.acropolis.chat.ChatService;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.proasteion.katagogion.McpEndpointDefinition;
import com.aresstack.enterpriseai.proasteion.katagogion.testkit.McpTestTools;
import com.aresstack.enterpriseai.proasteion.katagogion.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lokaler Start der Arbeitsfläche mit Modus-Pille Chat/Agent: {@code ./gradlew :proasteion:exedra:runAgentDemo}. Chat gegen den
 * Fake-Port (antwortet mit "Hallo aus dem Fake"), Agent-Modus mit dem Demo-Agenten als Kindprozess und einem
 * MCP-Endpoint mit Test-Tools. "slow" im Auftrag lässt den Agenten streamen, bis Stop gedrückt wird. Liegt im
 * Testumfang; die echte Composition Root folgt in AP23.
 */
public final class AgentShellDemo {

    private AgentShellDemo() {
    }

    public static void main(String[] args) {
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        final SolonMcpServerRuntime mcp = new SolonMcpServerRuntime();
        final ExecutorService agentStarts = Executors.newSingleThreadExecutor();
        final AgentService agentService = new AgentService(new AcpAgentLauncher(
                new SolonAcpAgentConnector(Duration.ofSeconds(30), null),
                new AgentLaunchSpec(javaBin, Arrays.asList("-jar", System.getProperty("acp.demo.agent.jar")), null),
                mcp, new McpEndpointDefinition("agent-tools", "Agent-Tools"),
                Arrays.asList(McpTestTools.ping(), McpTestTools.echo())), agentStarts);
        final FakeChatCompletionPort chatPort = new FakeChatCompletionPort(80L);
        final ChatService chatService = new ChatService(chatPort);

        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
                ComicTheme.installMenuDefaults(palette);
                ChatShellModel chatModel = new ChatShellModel(System::currentTimeMillis);
                final ChatServiceBinding chatBinding = new ChatServiceBinding(chatService,
                        chatService.openConversation(), chatModel, SwingUtilities::invokeLater);
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new com.aresstack.enterpriseai.proasteion.exedra.ui.chat
                        .ChatShellActions() {
                    @Override
                    public void sendRequested(String text, boolean ragEnabled) {
                        chatPort.enqueueAnswer("Hallo ", "aus ", "dem ", "Fake.");
                        chatBinding.sendRequested(text, ragEnabled);
                    }

                    @Override
                    public void stopRequested() {
                        chatBinding.stopRequested();
                    }
                }, palette, bubbles);
                AgentModeAssembly.AgentView agent = AgentModeAssembly.create(agentService,
                        SwingUtilities::invokeLater, System::currentTimeMillis, palette, bubbles);
                ChatWorkspacePanel shell = new ChatWorkspacePanel(new ShellModeModel(true), chatShell, agent.shell(),
                        palette);

                JFrame frame = ShellFrame.create("Enterprise AI Client – Agent-Demo", shell, palette);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        agentService.close();
                        agentStarts.shutdownNow();
                        mcp.shutdown();
                        SolonMcpServerRuntime.stopSharedServer();
                    }
                });
                frame.setVisible(true);
            }
        });
    }
}

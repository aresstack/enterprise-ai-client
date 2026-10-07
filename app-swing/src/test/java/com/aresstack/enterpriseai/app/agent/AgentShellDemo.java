package com.aresstack.enterpriseai.app.agent;

import com.aresstack.enterpriseai.acp.api.AgentLaunchSpec;
import com.aresstack.enterpriseai.acp.solon.SolonAcpAgentConnector;
import com.aresstack.enterpriseai.app.chat.ChatServiceBinding;
import com.aresstack.enterpriseai.app.ui.agent.ModalShellPanel;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.application.agent.AgentService;
import com.aresstack.enterpriseai.application.chat.ChatService;
import com.aresstack.enterpriseai.chat.api.fake.FakeChatCompletionPort;
import com.aresstack.enterpriseai.mcp.api.McpEndpointDefinition;
import com.aresstack.enterpriseai.mcp.api.testkit.McpTestTools;
import com.aresstack.enterpriseai.mcp.solon.SolonMcpServerRuntime;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lokaler Start der Shell mit Modus-Umschaltung: {@code ./gradlew :app-swing:runAgentDemo}. Chat gegen den
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
                ChatShellPanel chatShell = new ChatShellPanel(chatModel, new com.aresstack.enterpriseai.app.ui.chat
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
                ModalShellPanel shell = new ModalShellPanel(new ShellModeModel(true), chatShell, agent.shell(),
                        palette);

                JFrame frame = new JFrame("Enterprise AI Client – Agent-Demo");
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                JPanel content = new JPanel(new BorderLayout());
                content.setBackground(palette.getSurface());
                content.setBorder(ComicBorder.roundedBorder(palette, 6));
                content.add(shell, BorderLayout.CENTER);
                frame.setContentPane(content);
                frame.setSize(new Dimension(860, 700));
                frame.setLocationRelativeTo(null);
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

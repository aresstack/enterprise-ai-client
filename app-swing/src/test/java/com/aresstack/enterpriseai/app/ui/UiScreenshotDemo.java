package com.aresstack.enterpriseai.app.ui;

import com.aresstack.enterpriseai.app.ui.agent.ShellMode;
import com.aresstack.enterpriseai.app.ui.agent.ShellModeModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatComposerPanel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellActions;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellModel;
import com.aresstack.enterpriseai.app.ui.chat.ChatShellPanel;
import com.aresstack.enterpriseai.app.ui.chat.KnowledgeStatusModel;
import com.aresstack.enterpriseai.app.ui.chat.TranscriptEntry;
import com.aresstack.enterpriseai.app.ui.settings.SettingsDialogDemo;
import com.aresstack.enterpriseai.app.ui.settings.SettingsPanel;
import com.aresstack.enterpriseai.app.ui.workspace.ChatWorkspacePanel;
import com.aresstack.enterpriseai.app.ui.workspace.KnowledgeSourceItem;
import com.aresstack.enterpriseai.app.ui.workspace.ShellFrame;
import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.bubble.SpeechBubblePanel;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowCloseButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Rendert die Abnahme-Bilder A–H der Oberfläche headless als PNG: {@code ./gradlew :app-swing:runUiScreenshots
 * --args="<Zielverzeichnis>"}. Alles mit Attrappen (kein Dienst, keine Datei, kein KeePass); die Bilder zeigen
 * den Fensterinhalt samt Tintenrand und Comic-✕ wie im rahmenlosen Hauptfenster.
 *
 * <ul>
 *   <li>A leeres Hauptfenster, B Chat mit Nutzer- und Antwortblase, C Composer in Ruhe (Ausschnitt),
 *       D Composer während einer Antwort (Fenster und Ausschnitt), E Drawer offen, F Agent-Modus,
 *       G Einstellungen, H Fehlerblase (eingeklappt und mit Details).</li>
 * </ul>
 */
public final class UiScreenshotDemo {

    static final int WIDTH = 1040;
    static final int HEIGHT = 720;
    private static final String QUESTION = "Wie lange ist die Kündigungsfrist in der Probezeit?";
    private static final String ANSWER = "In der Probezeit beträgt die Kündigungsfrist zwei Wochen (§ 622 Abs. 3 BGB). "
            + "Laut Handbuch gilt das für beide Seiten; eine Verlängerung ist nur per Tarifvertrag möglich.";

    private UiScreenshotDemo() {
    }

    public static void main(String[] args) throws Exception {
        final File dir = new File(args.length > 0 ? args[0] : "build/ui-screenshots");
        dir.mkdirs();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    render(dir);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            }
        });
    }

    private static void render(File dir) throws IOException {
        ComicPalette palette = ComicPalette.defaultPalette();
        BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
        ComicTheme.installMenuDefaults(palette);

        // A: leeres Hauptfenster (mit Agent konfiguriert, Drawer zu)
        Scene scene = new Scene(palette, bubbles, true);
        write(scene.window, new File(dir, "A-hauptfenster-leer.png"));

        // B: Chat mit Nutzer- und Antwortblase
        scene.chatModel.addUserMessage(QUESTION);
        scene.chatModel.beginAssistantMessage();
        scene.chatModel.appendAssistantDelta(ANSWER);
        scene.chatModel.completeAssistantMessage();
        scene.status.finished("Wissensbasis: 3 Dokumente aus handbuch indiziert");
        write(scene.window, new File(dir, "B-chat-blasen.png"));

        // C: Composer in Ruhe (Ausschnitt)
        scene.chatShell.composer().editor().setText("Und wie ist es nach der Probezeit?");
        scene.chatShell.composer().editor().requestFocus();
        writeCrop(scene.window, scene.chatShell.composer(), 12, new File(dir, "C-composer-idle.png"));

        // D: Composer während einer Antwort (Stop statt Senden)
        scene.chatShell.composer().editor().setText("");
        scene.chatModel.addUserMessage("Und wie ist es nach der Probezeit?");
        scene.chatModel.beginAssistantMessage();
        scene.chatModel.appendAssistantDelta("Nach der Probezeit gilt die gesetzliche Grundfrist von vier Wochen zum "
                + "15. oder zum Monatsende; mit der Betriebszugehörigkeit");
        write(scene.window, new File(dir, "D-composer-streaming.png"));
        writeCrop(scene.window, scene.chatShell.composer(), 12, new File(dir, "D-composer-streaming-ausschnitt.png"));
        scene.chatModel.completeAssistantMessage();

        // E: Drawer offen mit Reiterleiste, Chats-Seite
        scene.workspace.openDrawer();
        scene.workspace.ribbon().finishAnimation();
        write(scene.window, new File(dir, "E-sidebar-offen.png"));
        scene.workspace.sidebar().showTab(ChatWorkspacePanel.KNOWLEDGE_TAB);
        write(scene.window, new File(dir, "E2-sidebar-wissensquellen.png"));
        scene.workspace.closeDrawer();
        scene.workspace.ribbon().finishAnimation();

        // F: Agent-Modus
        scene.modes.select(ShellMode.AGENT);
        scene.agentModel.addUserMessage("Lies die Seite „Urlaub“ aus dem Handbuch und fasse die Regeln zusammen.");
        TranscriptEntry working = scene.agentModel.beginAssistantMessage();
        scene.agentModel.setAssistantActivity("Werkzeug knowledge.search: „Urlaub“");
        scene.agentModel.appendAssistantDelta("Zusammenfassung der Urlaubsregeln:\n• 30 Tage Jahresurlaub, anteilig im "
                + "Eintrittsjahr\n• Antrag über das Portal, Genehmigung durch die Führungskraft\n• Resturlaub bis zum "
                + "31. März des Folgejahres");
        scene.agentModel.completeAssistantMessage();
        write(scene.window, new File(dir, "F-agent-modus.png"));
        scene.modes.select(ShellMode.CHAT);

        // H: Fehlerblase in Blasengeometrie, Details eingeklappt und aufgeklappt
        scene.chatModel.addUserMessage("Gibt es eine Regel für Homeoffice?");
        TranscriptEntry failed = scene.chatModel.beginAssistantMessage();
        scene.chatModel.failAssistantMessage("Der KI-Dienst ist nicht erreichbar.\n"
                + "Technische Ursache: connection to demo2.example.org failed: UnknownHostException\n"
                + "Hinweis: Namensauflösung oder Proxy-Regel prüfen (Einstellungen → Netzwerk & Agent → "
                + "„Verbindung zum KI-Dienst prüfen“).");
        write(scene.window, new File(dir, "H-fehlerblase.png"));
        SpeechBubblePanel bubble = scene.chatShell.transcript().bubbleFor(failed.getId());
        bubble.setDetailsExpanded(true);
        write(scene.window, new File(dir, "H2-fehlerblase-details.png"));

        // G: Einstellungen (Dialoginhalt mit Tintenrand und ✕ wie der rahmenlose Dialog)
        SettingsPanel settings = new SettingsPanel(SettingsDialogDemo.sampleForm(), Collections.<String>emptyList(),
                SettingsPanel.Mode.EDIT, new SettingsDialogDemo.DemoActions(), palette);
        settings.setWindowControls(new ComicWindowCloseButton(palette, new Runnable() {
            @Override
            public void run() {
            }
        }, "Abbrechen", 24));
        JPanel dialog = new JPanel(new BorderLayout());
        dialog.setBackground(palette.getSurface());
        dialog.setBorder(ComicBorder.windowBorder(palette, 4));
        dialog.add(settings, BorderLayout.CENTER);
        dialog.setSize(800, 680);
        write(dialog, new File(dir, "G-einstellungen.png"));
        settings.selectTab(3);
        write(dialog, new File(dir, "G2-einstellungen-netzwerk.png"));
    }

    /** Die Arbeitsfläche mit Chat- und Agent-Ansicht in einem fensterartigen Inhalt (Tintenrand, ✕). */
    private static final class Scene {
        final ChatShellModel chatModel = new ChatShellModel(System::currentTimeMillis);
        final ChatShellModel agentModel = new ChatShellModel(System::currentTimeMillis);
        final KnowledgeStatusModel status = new KnowledgeStatusModel();
        final ShellModeModel modes;
        final ChatShellPanel chatShell;
        final ChatShellPanel agentShell;
        final ChatWorkspacePanel workspace;
        final JComponent window;

        Scene(ComicPalette palette, BubblePalette bubbles, boolean agent) {
            modes = new ShellModeModel(agent);
            chatShell = new ChatShellPanel(chatModel, new NoActions(), status, palette, bubbles);
            chatModel.setRagEnabled(true);
            if (agent) {
                agentShell = new ChatShellPanel(agentModel, new NoActions(), palette, bubbles);
                agentShell.composer().setRagToggleVisible(false);
            } else {
                agentShell = null;
            }
            workspace = new ChatWorkspacePanel(modes, chatShell, agentShell, palette);
            workspace.setKnowledgeSources(Arrays.asList(
                    new KnowledgeSourceItem("handbuch", "mediawiki · Urlaub, Kündigung, Gleitzeit, Homeoffice"),
                    new KnowledgeSourceItem("it-wiki", "confluence · IT")));
            window = ShellFrame.content("Enterprise AI Client", workspace, palette, new Runnable() {
                @Override
                public void run() {
                }
            });
            window.setSize(WIDTH, HEIGHT);
        }
    }

    private static final class NoActions implements ChatShellActions {
        @Override
        public void sendRequested(String text, boolean ragEnabled) {
        }

        @Override
        public void stopRequested() {
        }
    }

    static BufferedImage paint(JComponent root) {
        invalidateTree(root);
        layoutTree(root);
        BufferedImage image = new BufferedImage(root.getWidth(), root.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = image.createGraphics();
        try {
            root.paint(g2);
        } finally {
            g2.dispose();
        }
        return image;
    }

    private static void write(JComponent root, File target) throws IOException {
        ImageIO.write(paint(root), "png", target);
        System.out.println("Screenshot: " + target.getAbsolutePath());
    }

    /** Ein Ausschnitt um eine Komponente herum (mit Rand), aus dem gemalten Fensterinhalt. */
    private static void writeCrop(JComponent root, JComponent part, int margin, File target) throws IOException {
        BufferedImage image = paint(root);
        Point origin = SwingUtilities.convertPoint(part, 0, 0, root);
        Rectangle crop = new Rectangle(origin.x - margin, origin.y - margin, part.getWidth() + 2 * margin,
                part.getHeight() + 2 * margin).intersection(new Rectangle(0, 0, image.getWidth(), image.getHeight()));
        ImageIO.write(image.getSubimage(crop.x, crop.y, crop.width, crop.height), "png", target);
        System.out.println("Screenshot: " + target.getAbsolutePath());
    }

    private static void invalidateTree(Component component) {
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                invalidateTree(child);
            }
        }
        component.invalidate();
    }

    private static void layoutTree(Component component) {
        component.doLayout();
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                layoutTree(child);
            }
        }
    }
}

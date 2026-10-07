package com.aresstack.enterpriseai.app;

import com.aresstack.enterpriseai.app.composition.AdapterAssembly;
import com.aresstack.enterpriseai.app.composition.ApplicationPorts;
import com.aresstack.enterpriseai.app.composition.CompositionRoot;
import com.aresstack.enterpriseai.app.composition.ShellAssembly;
import com.aresstack.enterpriseai.app.composition.StartupNotices;
import com.aresstack.enterpriseai.app.config.AppConfig;
import com.aresstack.enterpriseai.app.config.AppConfigException;
import com.aresstack.enterpriseai.app.config.AppConfigLoader;
import com.aresstack.enterpriseai.app.config.AppPaths;
import com.aresstack.enterpriseai.app.net.ProxyPolicy;
import com.aresstack.enterpriseai.app.security.SwingPairingCallback;
import com.aresstack.enterpriseai.ui.comic.bubble.BubblePalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Einstiegspunkt der Desktop-Anwendung und Composition Root (AP23). Ablauf: Konfiguration laden (fehlt sie,
 * wird die Beispieldatei angelegt und erklärt), Proxy-Regel installieren, Adapter bauen, Graphen komponieren,
 * Shutdown-Hook registrieren, Fenster zeigen, Hintergrund-Indexierung starten. Schließen des Fensters fährt
 * geordnet herunter und beendet die JVM; der Shutdown-Hook deckt hartes Beenden ab (beide idempotent).
 *
 * <p>Start: {@code ./gradlew :app-swing:run}; Konfigurationsdatei per {@code -Denterpriseai.config=<Pfad>},
 * Benutzerverzeichnis per {@code -Denterpriseai.home=<Pfad>} (siehe {@link AppPaths}).
 */
public final class EnterpriseAiClientMain {

    private static final Logger LOG = Logger.getLogger(EnterpriseAiClientMain.class.getName());

    static final int EXIT_CONFIG = 2;

    private EnterpriseAiClientMain() {
    }

    public static void main(String[] args) {
        Path configFile = AppPaths.configFile();
        AppConfig config;
        try {
            config = AppConfigLoader.load(configFile);
        } catch (AppConfigException e) {
            String message = configProblem(configFile, e);
            LOG.severe(message);
            showError("Konfiguration", message);
            System.exit(EXIT_CONFIG);
            return;
        }

        final ProxyPolicy proxy = new ProxyPolicy(config.network());
        proxy.install();
        final ApplicationPorts ports;
        final CompositionRoot root;
        try {
            ports = AdapterAssembly.create(config, proxy, new SwingPairingCallback(
                    config.keePass().rpc().host() + ":" + config.keePass().rpc().port()));
            root = CompositionRoot.compose(config, ports, SwingUtilities::invokeLater, System::currentTimeMillis,
                    null);
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Anwendung konnte nicht zusammengesetzt werden", e);
            showError("Start fehlgeschlagen", "Die Anwendung konnte nicht gestartet werden: "
                    + e.getClass().getSimpleName() + ". Details im Log.");
            System.exit(1);
            return;
        }
        Runtime.getRuntime().addShutdownHook(root.shutdown().asShutdownHook());

        final AppConfig started = config;
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                BubblePalette bubbles = BubblePalette.windowsPhoneInspired();
                ShellAssembly.ShellView view = ShellAssembly.createShell(root, palette, bubbles);
                JFrame frame = ShellAssembly.createFrame(started.windowTitle(), view, palette);
                frame.addWindowListener(new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        shutdownAndExit(root);
                    }
                });
                frame.setVisible(true);
                root.startBackgroundWork();
                List<String> notices = StartupNotices.of(started);
                if (!notices.isEmpty()) {
                    showNotices(frame, notices);
                }
            }
        });
    }

    private static void shutdownAndExit(final CompositionRoot root) {
        Thread exit = new Thread(new Runnable() {
            @Override
            public void run() {
                root.shutdown().run();
                System.exit(0);
            }
        }, "enterprise-ai-exit");
        exit.setDaemon(false);
        exit.start();
    }

    /** Meldung ohne Werte aus der Datei; legt bei fehlender Datei die kommentierte Beispieldatei an. */
    static String configProblem(Path configFile, AppConfigException e) {
        if (!Files.exists(configFile)) {
            try {
                Path parent = configFile.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.write(configFile, AppConfigLoader.exampleConfiguration().getBytes(StandardCharsets.UTF_8));
                return "Es gab noch keine Konfiguration. Eine kommentierte Vorlage wurde angelegt unter\n"
                        + configFile + "\nBitte ausfüllen (Basis-URL, Modell, KeePass-Eintrag für den API-Key) "
                        + "und die Anwendung neu starten.";
            } catch (IOException io) {
                return "Es gibt keine Konfiguration unter\n" + configFile + "\nund die Vorlage konnte dort nicht "
                        + "angelegt werden (" + io.getClass().getSimpleName() + ").";
            }
        }
        StringBuilder sb = new StringBuilder("Die Konfiguration unter\n").append(configFile)
                .append("\nhat Fehler:\n");
        for (String problem : e.problems()) {
            sb.append("  - ").append(problem).append('\n');
        }
        return sb.toString();
    }

    private static void showError(String title, String message) {
        if (GraphicsEnvironment.isHeadless()) {
            return;
        }
        try {
            JOptionPane.showMessageDialog(null, message, title, JOptionPane.ERROR_MESSAGE);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Fehlerdialog nicht anzeigbar", e);
        }
    }

    private static void showNotices(JFrame owner, List<String> notices) {
        StringBuilder sb = new StringBuilder();
        for (String notice : notices) {
            sb.append("• ").append(notice).append("\n\n");
        }
        for (String notice : notices) {
            LOG.warning(notice);
        }
        try {
            JOptionPane.showMessageDialog(owner, sb.toString().trim(), "Hinweise zur Konfiguration",
                    JOptionPane.WARNING_MESSAGE);
        } catch (RuntimeException e) {
            LOG.log(Level.FINE, "Hinweisdialog nicht anzeigbar", e);
        }
    }
}

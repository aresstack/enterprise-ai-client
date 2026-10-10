package com.aresstack.enterpriseai.app.ui.settings;

import com.aresstack.enterpriseai.ui.comic.border.ComicBorder;
import com.aresstack.enterpriseai.ui.comic.control.ComicWindowCloseButton;
import com.aresstack.enterpriseai.ui.comic.theme.ComicPalette;
import com.aresstack.enterpriseai.ui.comic.theme.ComicTheme;

import javax.imageio.ImageIO;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Zeigt den Einstellungen-Dialog ohne Datei und ohne KeePass (Aktionen sind Attrappen: Prüfen findet nichts zu
 * beanstanden, die KeePass-Probe antwortet fest). Start: {@code ./gradlew :app-swing:runSettingsDemo}. Mit
 * {@code --args="--screenshot datei.png [--tab 0-2] [--problems] [--height 680]"} wird der Dialoginhalt ohne
 * Fenster als PNG geschrieben (headless), z. B. für die Dokumentation; eine größere Höhe zeigt lange Reiter ohne
 * Rollbalken.
 */
public final class SettingsDialogDemo {

    private SettingsDialogDemo() {
    }

    /** Attrappe: keine Probleme, Speichern merkt sich das Formular, die Probe meldet ein festes Ergebnis. */
    public static final class DemoActions implements SettingsDialogActions {
        final List<SettingsForm> saved = new ArrayList<SettingsForm>();

        @Override
        public List<String> validate(SettingsForm form) {
            return Collections.emptyList();
        }

        @Override
        public void save(SettingsForm form) throws IOException {
            saved.add(form);
        }

        @Override
        public void checkSecret(SettingsForm form, String secretRef, Consumer<SecretCheckResult> onResult) {
            onResult.accept(SecretCheckResult.ok("Eintrag \u201e" + secretRef.replace("keepass:", "")
                    + "\u201c gefunden; das Passwortfeld ist gefüllt."));
        }

        @Override
        public void checkConnection(SettingsForm form, ConnectionCheckListener listener) {
            listener.onStep(ConnectionCheckStep.ok("Proxy-Route", "Ziel " + form.chatBaseUrl() + "/models: PROXY "
                    + "proxy.intern.beispiel:8080 laut PAC-Skript aus den Windows-Einstellungen"));
            listener.onStep(ConnectionCheckStep.ok("Namensauflösung", "proxy.intern.beispiel -> 10.0.0.8 (Proxy; "
                    + "den Zielhost ki.intern.beispiel löst der Proxy auf)"));
            listener.onStep(ConnectionCheckStep.ok("API-Key", "Aus dem KeePass-Eintrag \u201eEnterprise AI API\u201c "
                    + "gelesen (wird nicht angezeigt)."));
            listener.onStep(ConnectionCheckStep.ok("Verbindung und TLS", "TLS-Handshake mit ki.intern.beispiel "
                    + "erfolgreich (TLS_AES_256_GCM_SHA384); Serverzertifikat für \u201eki.intern.beispiel\u201c, "
                    + "ausgestellt von \u201eFirmen-CA\u201c; Vertrauensquellen: JVM, Windows-ROOT, CA-Datei."));
            listener.onStep(ConnectionCheckStep.warning("GET /models", "HTTP 200: 3 Modell(e), aber \u201e"
                    + form.chatModel() + "\u201c fehlt (chat.model prüfen). Verfügbar: modell-a, modell-b, modell-c"));
            listener.onFinished(true);
        }
    }

    public static SettingsForm sampleForm() {
        return SettingsForm.builder()
                .windowTitle("Enterprise AI Client")
                .chatBaseUrl("https://ki.intern.beispiel/v1")
                .chatModel("chat-modell")
                .chatApiKeyRef("keepass:Enterprise AI API")
                .chatSystemPrompt("Du bist ein hilfreicher Assistent. Antworte auf Deutsch.")
                .embeddingModel("embedding-modell")
                .embeddingDimension("768")
                .indexDirectory("C:/Daten/enterprise-ai-index")
                .caCertificatesFile("C:/Zertifikate/firmen-ca.pem")
                .build();
    }

    public static void main(String[] args) throws Exception {
        String screenshot = null;
        int tab = 0;
        int height = DEFAULT_HEIGHT;
        boolean problems = false;
        boolean check = false;
        for (int i = 0; i < args.length; i++) {
            if ("--screenshot".equals(args[i]) && i + 1 < args.length) {
                screenshot = args[++i];
            } else if ("--tab".equals(args[i]) && i + 1 < args.length) {
                tab = Integer.parseInt(args[++i]);
            } else if ("--height".equals(args[i]) && i + 1 < args.length) {
                height = Integer.parseInt(args[++i]);
            } else if ("--problems".equals(args[i])) {
                problems = true;
            } else if ("--check".equals(args[i])) {
                check = true;
            }
        }
        final List<String> shownProblems = problems
                ? Arrays.asList("Basis-URL des KI-Dienstes (chat.baseUrl): muss eine absolute http(s)-URL mit Host sein",
                        "Embedding-Dimension (embedding.dimension): keine ganze Zahl")
                : Collections.<String>emptyList();
        if (screenshot != null) {
            screenshot(new File(screenshot), tab, height, shownProblems, check);
            return;
        }
        final DemoActions actions = new DemoActions();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                SettingsForm saved = SettingsDialog.show(null, sampleForm(), shownProblems, SettingsPanel.Mode.EDIT,
                        actions, ComicPalette.defaultPalette());
                System.out.println(saved == null ? "Abgebrochen" : "Gespeichert: " + saved.chatModel());
            }
        });
        System.exit(0);
    }

    private static final int DEFAULT_HEIGHT = 680;

    /**
     * Ohne Fenster (headless) wird der Dialoginhalt auf 800×{@code height} gelegt und per rekursivem {@code doLayout}
     * gesetzt; {@code check} drückt vorher „Verbindung zum KI-Dienst prüfen“ (Attrappen-Schritte).
     */
    static void screenshot(final File target, final int tab, final int height, final List<String> problems,
                           final boolean check) throws Exception {
        final BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                ComicPalette palette = ComicPalette.defaultPalette();
                ComicTheme.installMenuDefaults(palette);
                SettingsPanel panel = new SettingsPanel(sampleForm(), problems, SettingsPanel.Mode.EDIT,
                        new DemoActions(), palette);
                panel.selectTab(tab);
                if (check) {
                    panel.serviceTab().connectionCheck().button().doClick();
                }
                JPanel content = new JPanel(new BorderLayout());
                content.setBackground(palette.getSurface());
                content.setBorder(ComicBorder.windowBorder(palette, 4));
                content.add(panel, BorderLayout.CENTER);
                panel.setWindowControls(new ComicWindowCloseButton(palette, new Runnable() {
                    @Override
                    public void run() {
                    }
                }, "Abbrechen", 24));
                content.setSize(800, height);
                if (GraphicsEnvironment.isHeadless()) {
                    invalidateTree(content);
                    layoutTree(content);
                } else {
                    content.validate();
                }
                image[0] = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = image[0].createGraphics();
                try {
                    content.paint(g2);
                } finally {
                    g2.dispose();
                }
            }
        });
        File parent = target.getAbsoluteFile().getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        ImageIO.write(image[0], "png", target);
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

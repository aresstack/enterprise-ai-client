package com.aresstack.enterpriseai.architecture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Die einzige Stelle, an der Module und ihre erlaubten Abhängigkeiten festgelegt sind.
 *
 * <p>Ein neues Gradle-Modul muss hier bewusst eingetragen werden (Name, Basispaket, Rolle, Strang,
 * erlaubte Abhängigkeiten). Fehlt der Eintrag, wird {@code BuildModelTest.everyGradleModuleIsRegistered}
 * rot. Eine Lockerung der erlaubten Richtung ist eine Architekturentscheidung und gehört in
 * ARCHITECTURE.md; {@link ModuleRegistryTest} verhindert, dass die Grundregeln dabei unterlaufen werden.
 */
final class ModuleRegistry {

    static final String ROOT_PACKAGE = "com.aresstack.enterpriseai";

    /**
     * Third-party sources copied unchanged into exactly one adapter module, under their original package (see the
     * module's VENDORED.md). Only that module may hold classes of the package; it stays an implementation detail of
     * the adapter. Adding an entry is an architecture decision.
     */
    private static final Map<String, String> VENDORED_PACKAGES = Collections.singletonMap(
            // WD4J, the WebDriver BiDi client from MainframeMate (#141, owner decision 2026-10-16).
            "browser-firefox", "de.bund.zrb");

    private final Map<String, ArchitectureModule> modules;

    private ModuleRegistry(Map<String, ArchitectureModule> modules) {
        // Geschachtelte Basispakete wie in corenth: jedes Modul kennt die Pakete der in ihm liegenden Module.
        Map<String, ArchitectureModule> withNested = new LinkedHashMap<String, ArchitectureModule>();
        for (ArchitectureModule module : modules.values()) {
            Set<String> nested = new LinkedHashSet<String>();
            for (ArchitectureModule other : modules.values()) {
                if (other != module && other.basePackage().startsWith(module.basePackage() + ".")) {
                    nested.add(other.basePackage());
                }
            }
            withNested.put(module.name(), module.withNested(nested));
        }
        this.modules = Collections.unmodifiableMap(withNested);
    }

    static ModuleRegistry standard() {
        return new Builder()
                // Grenzmodule der Stadt (corenth) ohne eigene Klassen: Vorstadt, Hafen, Plattform.
                .module("proasteion", "proasteion", ModuleKind.BOUNDARY, "Stadt")
                .module("emporion", "proasteion.emporion", ModuleKind.BOUNDARY, "Stadt")
                .module("platform", "proasteion.platform", ModuleKind.BOUNDARY, "Stadt")

                .module("astu", "astu", ModuleKind.DOMAIN, "Kern")
                .module("acropolis", "astu.acropolis", ModuleKind.APPLICATION, "Kern (AP10, AP20, AP21, AP23)",
                        "astu", "chat-api", "embedding-api", "chalcotheca", "tamias", "source-api", "adyton",
                        "acp-client-api", "katagogion", "model-api", "speech-api")

                .module("chat-api", "chat.api", ModuleKind.PORT, "A", "astu")
                .module("chat-openai", "chat.openai", ModuleKind.ADAPTER, "A", "astu", "chat-api", "network")

                // Netz: HTTP-Route je Ziel-URL (Proxy-Entscheidung) als neutraler Port; die einzige
                // Implementierung (win-proxy-java) liegt im Kompositionspunkt (proasteion.application.net.HttpRoutes).
                .module("network", "proasteion.platform.network", ModuleKind.PORT, "N (Netz)", "astu")

                .module("embedding-api", "embedding.api", ModuleKind.PORT, "C", "astu")
                .module("embedding-openai", "embedding.openai", ModuleKind.ADAPTER, "C", "astu", "embedding-api",
                        "network")

                // corenth: anagraphai ist das lexikalische Register (Lucene) und kennt pinakes nicht; pinakes ist der
                // semantische Katalog (Vektoren) und verbindet beide als Index-Adapter (KnowledgeIndexPort, RRF).
                .module("anagraphai", "astu.acropolis.chalcotheca.anagraphai", ModuleKind.ADAPTER, "D", "astu", "chalcotheca")
                .module("pinakes", "astu.acropolis.chalcotheca.pinakes", ModuleKind.ADAPTER, "D", "astu", "chalcotheca",
                        "anagraphai")

                // Metadaten-Katalog (#106): Facts an der stabilen KnowledgeResourceId; H2 nur im Adapter fact-h2.
                // Browser per chat (#141): port with engine SPI; runtime with profile per chat and recovery.
                .module("browser-api", "browser.api", ModuleKind.PORT, "Browser", "astu")
                .module("browser-runtime", "browser.runtime", ModuleKind.APPLICATION, "Browser", "astu",
                        "browser-api")
                // Firefox over WebDriver BiDi; the protocol layer is WD4J, vendored under de.bund.zrb (VENDORED.md).
                .module("browser-firefox", "browser.firefox", ModuleKind.ADAPTER, "Browser", "astu", "browser-api")
                .module("fact-api", "fact.api", ModuleKind.PORT, "Fakten", "astu")
                .module("fact-h2", "fact.h2", ModuleKind.ADAPTER, "Fakten", "astu", "fact-api")

                .module("source-api", "source.api", ModuleKind.PORT, "E", "astu")
                .module("source-mediawiki", "proasteion.emporion.holkas.mediawiki", ModuleKind.ADAPTER, "E", "astu", "source-api",
                        "network")
                .module("source-confluence", "proasteion.emporion.holkas.confluence", ModuleKind.ADAPTER, "F",
                        "astu", "source-api", "adyton", "network")
                // Lokale Dateien: Verzeichnis als Quelle; Erkennung/Extraktion nur über den Port deigma.
                .module("source-localfiles", "proasteion.emporion.holkas.localfiles", ModuleKind.ADAPTER, "E",
                        "astu", "source-api", "deigma")
                // Git repositories (#137): JGit internal to the adapter, read only; token through a callback of the
                // composition root (like SharePoint), proxy and TLS through network.
                .module("source-git", "proasteion.emporion.holkas.git", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api", "deigma", "network")
                // FTP (MVS/z/OS): Datasets und PDS-Member; Commons Net adapterintern, Anmeldung über adyton.
                .module("source-ftp", "proasteion.emporion.holkas.ftp", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api", "adyton")
                // NDV (Natural Development Server): NATSPOD/PAL aus MainframeMate, Anmeldung über adyton.
                .module("source-ndv", "proasteion.emporion.holkas.ndv", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api", "adyton")
                // Outlook-Postfachdateien (PST/OST): java-libpst adapterintern, Auswahl aus MainframeMate.
                .module("source-outlook", "proasteion.emporion.holkas.outlook", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api")
                // SharePoint (REST) und BetaView (Weboberfläche aus MainframeMate): Anmeldung wie MediaWiki über einen
                // Credential-Callback der Composition Root, ohne adyton; Extraktion über deigma.
                .module("source-sharepoint", "proasteion.emporion.holkas.sharepoint", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api", "network", "deigma")
                .module("source-betaview", "proasteion.emporion.holkas.betaview", ModuleKind.ADAPTER, "Quellen",
                        "astu", "source-api", "network", "deigma")

                // Ressourcenschicht aus corenth: AcquisitionPort und Bronze-Typen (Port), Holkas-Connectoren über
                // den Quellen-Ports (äußerer Adapterring, keine UI-API).
                .module("chalcotheca", "astu.acropolis.chalcotheca", ModuleKind.PORT, "Ressourcen, D", "astu", "tamias")
                // Tamias: Wächter am Archivschalter (ResourceAccessPolicy), liegt in chalcotheca.
                .module("tamias", "astu.acropolis.chalcotheca.tamias", ModuleKind.PORT, "Ressourcen", "astu")
                .module("holkas", "proasteion.emporion.holkas", ModuleKind.ADAPTER, "Ressourcen",
                        "astu", "chalcotheca", "source-api")

                // Dokument-Extraktion (aus corenth deigma): neutraler Port und Adapter mit Apache Tika.
                .module("deigma", "proasteion.emporion.deigma", ModuleKind.PORT, "Dokumente", "astu")
                .module("document-tika", "proasteion.emporion.deigma.tika", ModuleKind.ADAPTER, "Dokumente", "deigma")

                // Modellverwaltung nach Kategorien: neutraler Katalog-Port, KIPITZ über GET /models.
                .module("model-api", "model.api", ModuleKind.PORT, "Modelle", "astu")
                // Der KIPITZ-Adapter bedient Katalog und Sprachausgabe seiner TTS-Modelle.
                .module("model-kipitz", "model.kipitz", ModuleKind.ADAPTER, "Modelle", "astu", "model-api",
                        "network", "speech-api")

                // Lokale Stimmen vom Hugging Face Hub (huggingface4j), Proxy-Route über network.
                .module("model-huggingface", "model.huggingface", ModuleKind.ADAPTER, "Modelle", "astu",
                        "model-api", "network")

                // Sprachausgabe: neutraler Port (Modell aus der Katalog-Kategorie TTS, Audio als WAV).
                .module("speech-api", "speech.api", ModuleKind.PORT, "Sprache", "astu")
                // Inferenz-Laufzeit lokaler Stimmen (Token-Ids zu Audio), austauschbar gegen eine eigene Engine.
                .module("speech-runtime-api", "speech.runtime", ModuleKind.PORT, "Sprache", "astu")
                // Lokale Stimmen im Prozess (Piper, VITS) über speech-runtime-api; kein externes Programm.
                .module("speech-local", "speech.local", ModuleKind.ADAPTER, "Sprache", "astu", "speech-api",
                        "speech-runtime-api")
                // Pilot-Backend ONNX Runtime (Java-Bibliothek im Prozess); nur hier ONNX-Typen.
                .module("speech-onnx", "speech.onnx", ModuleKind.ADAPTER, "Sprache", "astu",
                        "speech-runtime-api")
                // Satz- und Spracherkennung beim Vorlesen (Apache OpenNLP wie askai arch); nur hier OpenNLP-Typen.
                .module("speech-opennlp", "speech.opennlp", ModuleKind.ADAPTER, "Sprache", "astu", "speech-api")

                // Inferenz-Laufzeit lokaler Text-Modelle (Encoder für Embeddings, Decoder für Chat), austauschbar.
                .module("text-runtime-api", "text.runtime", ModuleKind.PORT, "Lokale Modelle", "astu")
                // Lokale Embeddings, lokaler Chat und lokale Spracheingabe im Prozess: Tokenizer (tokenizer.json) in reinem Java, Pooling,
                // Chat-Vorlage, Generierung; rechnet über text-runtime-api.
                .module("text-local", "text.local", ModuleKind.ADAPTER, "Lokale Modelle", "astu", "embedding-api",
                        "chat-api", "text-runtime-api", "speech-api")
                // Pilot-Backend ONNX Runtime (Java-Bibliothek im Prozess); nur hier ONNX-Typen für Text-Modelle.
                .module("text-onnx", "text.onnx", ModuleKind.ADAPTER, "Lokale Modelle", "astu",
                        "text-runtime-api")

                .module("adyton", "adyton", ModuleKind.PORT, "F", "astu")
                .module("security-keepassrpc", "proasteion.platform.security.keepassrpc", ModuleKind.ADAPTER, "F", "astu", "adyton")

                .module("acp-client-api", "acp.api", ModuleKind.PORT, "G", "astu")
                .module("acp-solon-client", "acp.solon", ModuleKind.ADAPTER, "G", "astu", "acp-client-api")
                .module("acp-demo-agent", "acp.demo", ModuleKind.TEST_FIXTURE, "G")

                .module("katagogion", "proasteion.katagogion", ModuleKind.PORT, "H", "astu")
                .module("mcp-solon-runtime", "proasteion.katagogion.solon", ModuleKind.ADAPTER, "H", "astu", "katagogion")
                // Eingebaute Produkthilfe: Kapitel aus docs/help, eigene BM25-Suche ohne Netz und Embedding; die
                // Werkzeuge app_help_search/app_help_read baut der Kompositionspunkt darauf.
                .module("help", "proasteion.help", ModuleKind.APPLICATION, "Hilfe")

                // Research-Agent aus askai-java8 (Zweig arch): reiner Fachkern (Projekt, Quellen, Weidezaun).
                .module("research-domain", "research.domain", ModuleKind.DOMAIN, "Research")
                // Ports des Research-Agenten (Ablage des Projekts); Implementierung in research-store.
                .module("research-api", "research.api", ModuleKind.PORT, "Research", "research-domain")
                // Weidezaun auf der Host-Seite: Koordinator des Scope-Entwurfs, Zaun-Prüfung, Sweep (Use Cases).
                .module("research-scope", "research.scope", ModuleKind.APPLICATION, "Research",
                        "research-domain", "research-api")
                // Gesprächs-Engine des Recherche-Agenten (TeamAgent): spricht das Hauptmodell über den eigenen Port
                // MainModelChat an; läuft im Agentenprozess (research-agent-acp).
                .module("research-agent-runtime", "research.runtime", ModuleKind.APPLICATION, "Research",
                        "research-domain")
                // Ablage des Research-Projekts (JSON-Dateien je Projektverzeichnis) hinter den Ports aus research-scope.
                .module("research-store", "research.store", ModuleKind.ADAPTER, "Research",
                        "research-domain", "research-api")
                // Konzeptpapier (Konzeptbaum mit Inseln) aus askai-java8 (arch): JSON-Dokument mit Revisionen
                // hinter dem Lese-Port aus research-api; gson bleibt im Modul.
                .module("research-concept", "research.concept", ModuleKind.ADAPTER, "Research",
                        "research-domain", "research-api")
                // Der eingebaute Recherche-Agent als eigener Prozess (askai-java8 arch: ResearchAgentMain): ACP über
                // STDIO zum Client, Research-Steuerung und Hauptmodell über den MCP-Endpoint des Hosts.
                .module("research-agent-acp", "acp.research", ModuleKind.AGENT_PROCESS, "Research",
                        "research-domain", "research-agent-runtime", "katagogion", "mcp-solon-runtime")

                .module("comic-controls", "ui.comic", ModuleKind.UI_LIBRARY, "B")
                // corenth ADR-0001: neutraler Kompositionspunkt ohne Oberfläche (Konfiguration, Netz, Tresor-Brücken,
                // Adapter, Index, Use Cases → ApplicationPorts); exedra ist nur noch die Swing-Schale darauf.
                .module("application", "proasteion.application", ModuleKind.COMPOSITION_ROOT, "Stadt (ADR-0001)",
                        "astu", "acropolis",
                        "chat-api", "embedding-api", "chalcotheca", "source-api", "adyton",
                        "acp-client-api", "katagogion", "network",
                        "chat-openai", "embedding-openai", "anagraphai", "pinakes", "source-mediawiki",
                        "source-confluence", "security-keepassrpc", "acp-solon-client", "mcp-solon-runtime",
                        "deigma", "document-tika", "source-localfiles", "source-git", "tamias",
                        "holkas", "model-api", "model-kipitz", "speech-api", "source-ftp",
                        "source-ndv", "source-outlook", "source-sharepoint", "source-betaview", "model-huggingface", "speech-runtime-api", "speech-local", "speech-onnx",
                        "text-runtime-api", "text-local", "text-onnx", "speech-opennlp", "research-domain",
                        "research-api", "research-scope", "research-store", "research-agent-runtime",
                        "research-concept", "research-agent-acp", "help", "fact-api", "fact-h2", "browser-api",
                        "browser-runtime", "browser-firefox")
                // Die Swing-Schale über dem Kompositionspunkt (corenth ADR-0001): kennt application, Kern, Ports und
                // comic-controls, aber keinen Adapter; nicht holkas, deigma, tamias, anagraphai oder pinakes.
                .module("exedra", "proasteion.exedra", ModuleKind.UI_SHELL, "B (AP4, AP22, AP23)",
                        "application", "astu", "acropolis", "comic-controls",
                        "chat-api", "chalcotheca", "source-api", "acp-client-api", "network", "speech-api",
                        "model-api")

                .module("architecture-tests", "architecture", ModuleKind.ARCHITECTURE_TESTS, "AP24")
                .module("integration-tests", "integration", ModuleKind.INTEGRATION_TESTS, "AP25")
                .build();
    }

    /** Is {@code packageName} the vendored third-party package of {@code moduleName}? */
    boolean isVendored(String moduleName, String packageName) {
        String vendored = VENDORED_PACKAGES.get(moduleName);
        return vendored != null && (packageName.equals(vendored) || packageName.startsWith(vendored + "."));
    }

    Collection<ArchitectureModule> modules() {
        return modules.values();
    }

    Set<String> names() {
        return modules.keySet();
    }

    boolean contains(String name) {
        return modules.containsKey(name);
    }

    ArchitectureModule module(String name) {
        ArchitectureModule module = modules.get(name);
        if (module == null) {
            throw new IllegalArgumentException("Modul nicht registriert: " + name);
        }
        return module;
    }

    List<ArchitectureModule> modulesOfKind(ModuleKind... kinds) {
        List<ModuleKind> wanted = Arrays.asList(kinds);
        List<ArchitectureModule> result = new ArrayList<ArchitectureModule>();
        for (ArchitectureModule module : modules.values()) {
            if (wanted.contains(module.kind())) {
                result.add(module);
            }
        }
        return result;
    }

    /** Module, deren Klassen von {@code source} aus nicht referenziert werden dürfen. */
    List<ArchitectureModule> forbiddenTargetsOf(ArchitectureModule source) {
        List<ArchitectureModule> result = new ArrayList<ArchitectureModule>();
        for (ArchitectureModule candidate : modules.values()) {
            if (candidate != source && !source.allowedDependencies().contains(candidate.name())) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Liefert das Modul, dem ein Paket gehört, oder {@code null}. */
    ArchitectureModule ownerOfPackage(String packageName) {
        for (ArchitectureModule module : modules.values()) {
            if (module.ownsPackage(packageName)) {
                return module;
            }
        }
        return null;
    }

    static final class Builder {

        private final Map<String, ArchitectureModule> modules = new LinkedHashMap<String, ArchitectureModule>();

        Builder module(String name, String packageSuffix, ModuleKind kind, String owner, String... allowedDependencies) {
            if (modules.containsKey(name)) {
                throw new IllegalArgumentException("Modul doppelt registriert: " + name);
            }
            modules.put(name, new ArchitectureModule(name, ROOT_PACKAGE + "." + packageSuffix, kind, owner,
                    new LinkedHashSet<String>(Arrays.asList(allowedDependencies))));
            return this;
        }

        ModuleRegistry build() {
            return new ModuleRegistry(modules);
        }
    }
}

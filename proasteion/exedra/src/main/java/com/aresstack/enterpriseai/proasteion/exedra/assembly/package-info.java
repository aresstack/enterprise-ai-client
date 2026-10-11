/**
 * Zusammenbau der Swing-Schale über dem Kompositionspunkt {@code proasteion:application} (corenth ADR-0001). Adapter
 * entstehen hier nicht (Architekturregel {@code CompositionRootBoundaryTest}); die Schale bekommt Use Cases und
 * Ports aus {@link com.aresstack.enterpriseai.proasteion.application.composition.CompositionRoot}.
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.proasteion.exedra.assembly.ShellAssembly}: die Oberfläche (Chat/Agent),
 *       Bindings und Statuszeile der Wissensbasis auf dem EDT.</li>
 *   <li>{@link com.aresstack.enterpriseai.proasteion.exedra.assembly.SettingsAssembly}: der Einstellungen-Dialog mit
 *       KeePass-Probe und Verbindungstest; Pairing über
 *       {@link com.aresstack.enterpriseai.proasteion.application.security.PairingPrompt}.</li>
 * </ul>
 *
 * <p>Herkunft: askai-java8 (Komposition der Comic-App), MainframeMate {@code MainFrame}/{@code Main}
 * (Settings-Verdrahtung), corenth (Exedra als dünne Schale über dem Kompositionspunkt).
 */
package com.aresstack.enterpriseai.proasteion.exedra.assembly;

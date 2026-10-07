/**
 * Die Composition Root (AP23): der einzige Ort, an dem Adapter instanziiert und mit Use Cases, Shell und
 * Agent-Modus verbunden werden (Architekturregel {@code CompositionRootBoundaryTest}).
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.app.composition.ApplicationPorts}: die Ports, gegen die alles
 *       Weitere gebaut wird; produktiv aus {@link com.aresstack.enterpriseai.app.composition.AdapterAssembly},
 *       im Test aus Fakes.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.composition.CompositionRoot}: Use Cases, Agent-Modus mit
 *       MCP-Werkzeugen (AP20), Hintergrund-Indexierung und die geordnete, idempotente
 *       {@link com.aresstack.enterpriseai.app.composition.ShutdownSequence}.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.composition.ShellAssembly}: die Oberfläche (Chat/Agent) auf dem EDT.</li>
 * </ul>
 *
 * <p>Herkunft: askai-java8 (Komposition der Comic-App und ihrer Hintergrunddienste in {@code main}),
 * MainframeMate {@code MainFrame}/{@code Main} (Shutdown-Reihenfolge, Settings-Verdrahtung), corenth
 * (Composition Root als einziger Ort für Adapter).
 */
package com.aresstack.enterpriseai.app.composition;

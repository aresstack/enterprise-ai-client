/**
 * Brücke zwischen dem Security-Port und den Adaptern, die Zugangsdaten brauchen (AP23). Nur hier und in den
 * Security-/Confluence-Adaptern darf Secret-Material vorkommen (Architekturregel {@code SecretBoundaryTest});
 * es lebt ausschließlich für die Dauer eines Aufrufs von {@code SecretProvider.withSecret}, kein Feld hält es.
 *
 * <ul>
 *   <li>{@link com.aresstack.enterpriseai.app.security.SecretBackedTokenSource} und
 *       {@link com.aresstack.enterpriseai.app.security.SecretBackedBearerTokenSource}: API-Key für Chat- und
 *       Embedding-Adapter je Anfrage.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.security.SecretBackedMediaWikiCredentialsProvider}: Wiki-Login.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.security.FilePairingKeyStore}: dauerhafter KeePassRPC-Pairing-Schlüssel.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.security.SwingPairingCallback}: Pairing-Dialog auf dem EDT.</li>
 *   <li>{@link com.aresstack.enterpriseai.app.security.UnavailableSecretProvider}: Verhalten ohne KeePass.</li>
 * </ul>
 *
 * <p>Herkunft: MainframeMate {@code KeePassProvider} (Verdrahtung der Zugangsdaten je Aufruf, Pairing-Dialog),
 * corenth {@code adyton} (Secret-Material verlässt den Aufruf nicht).
 */
package com.aresstack.enterpriseai.app.security;

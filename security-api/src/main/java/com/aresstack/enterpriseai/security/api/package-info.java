/**
 * Neutraler Secret-Port (Strang F, AP13).
 *
 * <p>{@link com.aresstack.enterpriseai.domain.security.SecretRef} (domain, loggbar) wird von Konfiguration und
 * Use Cases getragen; {@link com.aresstack.enterpriseai.security.api.SecretProvider} löst ihn in kurzlebiges
 * {@link com.aresstack.enterpriseai.security.api.SecretMaterial} auf, das nur Adapter sehen. Keine
 * KeePass-, HTTP- oder Swing-Typen in diesem Paket.
 */
package com.aresstack.enterpriseai.security.api;

/**
 * Adapter: KeePassRPC hinter dem Security-Port (Strang F, AP14).
 *
 * <p>Öffentlich sind nur {@link com.aresstack.enterpriseai.security.keepassrpc.KeePassRpcSecretProvider}, seine
 * Konfiguration und die zwei Naht-Interfaces für die Composition Root (Pairing-Callback, Ablage des
 * Pairing-Schlüssels). WebSocket, JSON und die KeePassRPC-Kryptografie bleiben paketintern.
 */
package com.aresstack.enterpriseai.security.keepassrpc;

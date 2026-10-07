package com.aresstack.enterpriseai.app.config;

/** Wie ausgehende HTTP-Verbindungen geroutet werden. */
public enum ProxyMode {
    /** JVM-Standard: Proxy-System-Properties ({@code https.proxyHost} ...) bzw. kein Proxy. */
    SYSTEM,
    /** Nie über einen Proxy, auch wenn die JVM einen kennt. */
    NONE,
    /** Fester HTTP-Proxy aus {@code network.proxy.host}/{@code network.proxy.port}. */
    MANUAL
}

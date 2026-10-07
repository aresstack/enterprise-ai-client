package com.aresstack.enterpriseai.domain.chat;

/**
 * Rolle einer Chat-Nachricht, unabhängig vom Provider.
 *
 * <p>{@link #DEVELOPER} ist fachlich vorgesehen (Anweisungen des Anwendungsentwicklers mit Vorrang vor
 * Nutzereingaben). Ob ein Provider die Rolle akzeptiert, entscheidet der Adapter; der derzeit getestete
 * OpenAI-kompatible Server lehnt sie mit HTTP 500 ab.
 */
public enum ChatRole {
    SYSTEM,
    DEVELOPER,
    USER,
    ASSISTANT
}

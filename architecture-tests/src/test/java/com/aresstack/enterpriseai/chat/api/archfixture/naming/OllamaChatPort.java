package com.aresstack.enterpriseai.chat.api.archfixture.naming;

/** Absichtlicher Verstoß (Nachtrag 1): providerspezifischer Typ im Port. */
public interface OllamaChatPort {

    String complete(String prompt);
}

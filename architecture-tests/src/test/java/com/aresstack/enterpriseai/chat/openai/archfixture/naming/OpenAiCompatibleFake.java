package com.aresstack.enterpriseai.chat.openai.archfixture.naming;

/** Gegenprobe: im Adapter darf der Provider im Namen stehen. */
public final class OpenAiCompatibleFake {

    public String model() {
        return "configured";
    }
}

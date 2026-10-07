package com.aresstack.enterpriseai.chat.api.archfixture;

/** Steht für den Chat-Port als Ziel von Gegenbeispielen. */
public interface FakeChatPort {

    String complete(String prompt);
}

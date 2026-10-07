package com.aresstack.enterpriseai.chat.openai.archfixture;

import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;

/** Gegenprobe: ein Adapter implementiert ein Interface seines Ports. */
public final class AdapterImplementingItsPort implements FakeChatPort {

    @Override
    public String complete(String prompt) {
        return prompt;
    }
}

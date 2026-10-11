package com.aresstack.enterpriseai.proasteion.application.composition.archfixture.matrix;

import com.aresstack.enterpriseai.astu.acropolis.archfixture.NeutralUseCase;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;
import com.aresstack.enterpriseai.astu.chat.archfixture.FakeChatValue;

/** Gegenprobe: der Kompositionspunkt (application) darf Adapter, Use Cases, Ports und Domain verdrahten. */
public final class CompositionRootUsingEverything {

    private final FakeChatAdapter adapter = new FakeChatAdapter();
    private final NeutralUseCase useCase = new NeutralUseCase();
    private final FakeChatValue value = new FakeChatValue();

    public FakeChatPort wire() {
        return null;
    }

    public String describe() {
        return adapter.model() + useCase.run() + value.text();
    }
}

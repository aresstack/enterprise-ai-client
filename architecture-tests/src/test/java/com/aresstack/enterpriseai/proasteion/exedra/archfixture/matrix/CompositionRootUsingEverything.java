package com.aresstack.enterpriseai.proasteion.exedra.archfixture.matrix;

import com.aresstack.enterpriseai.astu.acropolis.archfixture.NeutralUseCase;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.chat.openai.archfixture.FakeChatAdapter;
import com.aresstack.enterpriseai.astu.chat.archfixture.FakeChatValue;
import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Gegenprobe: die Composition Root darf Adapter, Use Cases, Ports, Domain und comic-controls verdrahten. */
public final class CompositionRootUsingEverything {

    private final FakeChatAdapter adapter = new FakeChatAdapter();
    private final NeutralUseCase useCase = new NeutralUseCase();
    private final FakeChatValue value = new FakeChatValue();
    private final FakeComicPanel panel = new FakeComicPanel();

    public FakeChatPort wire() {
        return null;
    }

    public String describe() {
        return adapter.model() + useCase.run() + value.text() + panel.style();
    }
}

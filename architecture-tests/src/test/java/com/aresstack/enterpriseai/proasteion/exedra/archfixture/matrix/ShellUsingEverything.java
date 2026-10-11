package com.aresstack.enterpriseai.proasteion.exedra.archfixture.matrix;

import com.aresstack.enterpriseai.astu.acropolis.archfixture.NeutralUseCase;
import com.aresstack.enterpriseai.astu.chat.archfixture.FakeChatValue;
import com.aresstack.enterpriseai.chat.api.archfixture.FakeChatPort;
import com.aresstack.enterpriseai.ui.comic.archfixture.FakeComicPanel;

/** Gegenprobe: die Schale (exedra) darf Use Cases, Ports, Domain und comic-controls nutzen. */
public final class ShellUsingEverything {

    private final NeutralUseCase useCase = new NeutralUseCase();
    private final FakeChatValue value = new FakeChatValue();
    private final FakeComicPanel panel = new FakeComicPanel();

    public FakeChatPort port() {
        return null;
    }

    public String describe() {
        return useCase.run() + value.text() + panel.style();
    }
}

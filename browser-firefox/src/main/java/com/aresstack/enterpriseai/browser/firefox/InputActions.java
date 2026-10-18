package com.aresstack.enterpriseai.browser.firefox;

import de.bund.zrb.command.request.parameters.input.sourceActions.KeySourceAction;
import de.bund.zrb.command.request.parameters.input.sourceActions.PauseAction;
import de.bund.zrb.command.request.parameters.input.sourceActions.PointerSourceAction;
import de.bund.zrb.command.request.parameters.input.sourceActions.SourceActions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** WebDriver BiDi input sequences for real clicks and typing: trusted events, as a person would cause them. */
final class InputActions {

    static final String MOUSE = "eai-mouse";
    static final String KEYBOARD = "eai-keyboard";
    /** A short hold between press and release, so handlers that look at the press see it. */
    private static final int HOLD_MILLIS = 40;

    private InputActions() {
    }

    /** Moves the mouse to viewport point ({@code x}, {@code y}) and clicks the left button there. */
    static SourceActions click(long x, long y) {
        List<PointerSourceAction> actions = Arrays.<PointerSourceAction>asList(
                new PointerSourceAction.PointerMoveAction(x, y),
                new PointerSourceAction.PointerDownAction(0),
                new PauseAction(HOLD_MILLIS),
                new PointerSourceAction.PointerUpAction(0));
        return new SourceActions.PointerSourceActions(MOUSE,
                new SourceActions.PointerSourceActions.PointerParameters(), actions);
    }

    /** Selects the focused field's content, deletes it and types {@code text} key by key. */
    static SourceActions typeReplacing(String text) {
        List<KeySourceAction> actions = new ArrayList<KeySourceAction>();
        actions.add(new KeySourceAction.KeyDownAction(BidiBrowserConnection.KEY_CONTROL));
        actions.add(new KeySourceAction.KeyDownAction("a"));
        actions.add(new KeySourceAction.KeyUpAction("a"));
        actions.add(new KeySourceAction.KeyUpAction(BidiBrowserConnection.KEY_CONTROL));
        actions.add(new KeySourceAction.KeyDownAction(BidiBrowserConnection.KEY_BACKSPACE));
        actions.add(new KeySourceAction.KeyUpAction(BidiBrowserConnection.KEY_BACKSPACE));
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String key = new String(Character.toChars(codePoint));
            actions.add(new KeySourceAction.KeyDownAction(key));
            actions.add(new KeySourceAction.KeyUpAction(key));
            i += Character.charCount(codePoint);
        }
        return new SourceActions.KeySourceActions(KEYBOARD, actions);
    }

    /** Presses and releases one key, e.g. {@link BidiBrowserConnection#KEY_ENTER}. */
    static SourceActions press(String key) {
        return new SourceActions.KeySourceActions(KEYBOARD, Arrays.<KeySourceAction>asList(
                new KeySourceAction.KeyDownAction(key), new KeySourceAction.KeyUpAction(key)));
    }
}

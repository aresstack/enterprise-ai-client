package com.aresstack.enterpriseai.app.ui.agent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** AP21: das Presentation-Model der Modus-Umschaltung, ohne Swing. */
public class ShellModeModelTest {

    @Test
    public void modelStartsInChatAndOffersAgentOnlyWhenConfigured() {
        ShellModeModel withoutAgent = new ShellModeModel(false);
        assertEquals(ShellMode.CHAT, withoutAgent.getMode());
        assertFalse(withoutAgent.select(ShellMode.AGENT));
        assertEquals(ShellMode.CHAT, withoutAgent.getMode());

        ShellModeModel withAgent = new ShellModeModel(true);
        final List<ShellMode> seen = new ArrayList<ShellMode>();
        withAgent.addListener(new ShellModeModel.Listener() {
            @Override
            public void modeChanged(ShellMode mode) {
                seen.add(mode);
            }
        });
        assertTrue(withAgent.select(ShellMode.AGENT));
        assertTrue(withAgent.select(ShellMode.AGENT)); // kein zweites Ereignis
        assertTrue(withAgent.select(ShellMode.CHAT));
        assertEquals(Arrays.asList(ShellMode.AGENT, ShellMode.CHAT), seen);
    }
}

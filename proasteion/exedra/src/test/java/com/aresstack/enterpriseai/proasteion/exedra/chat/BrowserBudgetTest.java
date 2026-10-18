package com.aresstack.enterpriseai.proasteion.exedra.chat;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** #141: the browser budget sets the tool rounds of a turn with the browser on, never below the general limit. */
public class BrowserBudgetTest {

    @Test
    public void browserTurnsUseTheChatBudgetAndOthersTheGeneralLimit() {
        assertEquals(20, BrowserBudget.roundsFor(true, 0, 8));
        assertEquals(40, BrowserBudget.roundsFor(true, 40, 8));
        assertEquals(12, BrowserBudget.roundsFor(true, 5, 12));
        assertEquals(8, BrowserBudget.roundsFor(false, 40, 8));
    }

    @Test
    public void anyValueWithinTheBoundsIsKept() {
        assertEquals(BrowserBudget.DEFAULT, BrowserBudget.of(0));
        assertEquals(137, BrowserBudget.of(137));
        assertEquals(BrowserBudget.MAX, BrowserBudget.of(5000));
    }
}

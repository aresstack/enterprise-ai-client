package com.aresstack.enterpriseai.app.ui.chat;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class KnowledgeStatusModelTest {

    private final KnowledgeStatusModel model = new KnowledgeStatusModel();
    private final List<String> events = new ArrayList<String>();

    @Test
    public void runLifecycleAndCancelRequest() {
        model.addListener(new KnowledgeStatusModel.Listener() {
            @Override
            public void statusChanged() {
                events.add(model.getText() + "|" + model.isRunning() + "|" + model.isCancelRequested());
            }
        });
        assertFalse(model.isVisible());

        model.started("Indexierung …");
        assertTrue(model.isVisible());
        assertTrue(model.isRunning());
        model.progressed("1 von 3");
        model.requestCancel();
        model.requestCancel(); // kein zweites Ereignis
        assertTrue(model.isCancelRequested());
        model.finished("abgebrochen");
        assertFalse(model.isRunning());
        assertFalse(model.isCancelRequested());

        assertEquals(java.util.Arrays.asList(
                "Indexierung …|true|false",
                "1 von 3|true|false",
                "1 von 3|true|true",
                "abgebrochen|false|false"), events);
    }

    @Test
    public void cancelWithoutRunIsIgnoredAndShowHidesWithEmptyText() {
        model.requestCancel();
        assertFalse(model.isCancelRequested());
        model.show("Wissensbasis: 3 Seiten");
        assertTrue(model.isVisible());
        model.show("");
        assertFalse(model.isVisible());
        model.finished(null);
        assertEquals("", model.getText());
    }

    @Test
    public void progressAndShowGuardTheRunState() {
        try {
            model.progressed("x");
            fail("progress without run");
        } catch (IllegalStateException expected) {
            // erwartet
        }
        model.started("läuft");
        try {
            model.show("x");
            fail("show during run");
        } catch (IllegalStateException expected) {
            // erwartet
        }
    }
}

package com.aresstack.enterpriseai.acp.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The guarded lifecycles: valid chains pass, invalid transitions are rejected without mutation. */
public class AcpStatesTest {

    @Test
    public void connectionHappyPathAndInvalids() {
        AcpStates.Connection c = new AcpStates.Connection();
        assertTrue(c.to(AcpConnectionState.INITIALIZING));
        assertTrue(c.to(AcpConnectionState.READY));
        assertTrue(c.to(AcpConnectionState.CLOSED));
        assertFalse("CLOSED is final", c.to(AcpConnectionState.READY));
        assertEquals(AcpConnectionState.CLOSED, c.get());

        AcpStates.Connection f = new AcpStates.Connection();
        assertTrue(f.to(AcpConnectionState.INITIALIZING));
        assertTrue(f.to(AcpConnectionState.FAILED));
        assertFalse("FAILED -> READY must be rejected", f.to(AcpConnectionState.READY));
    }

    @Test
    public void sessionHappyPathAndInvalids() {
        AcpStates.Session s = new AcpStates.Session();
        assertTrue(s.to(AcpSessionState.ACTIVE));
        assertTrue(s.to(AcpSessionState.CLOSING));
        assertTrue(s.to(AcpSessionState.CLOSED));
        assertFalse("CLOSED -> ACTIVE must be rejected", s.to(AcpSessionState.ACTIVE));
    }

    @Test
    public void promptMatrixWithSingleTerminal() {
        AcpStates.Prompt p = new AcpStates.Prompt();
        assertTrue(p.to(AcpPromptState.RUNNING));
        assertTrue(p.to(AcpPromptState.CANCELLING));
        assertTrue(p.to(AcpPromptState.CANCELLED));
        assertFalse("terminal is final", p.to(AcpPromptState.COMPLETED));

        AcpStates.Prompt done = new AcpStates.Prompt();
        assertTrue(done.to(AcpPromptState.RUNNING));
        assertTrue(done.to(AcpPromptState.COMPLETED));
        assertFalse("COMPLETED -> CANCELLING must be rejected", done.to(AcpPromptState.CANCELLING));
        assertEquals(AcpPromptState.COMPLETED, done.get());
    }

    @Test
    public void connectionCanFailFromEveryLiveStateButNotTwice() {
        AcpStates.Connection fromStarting = new AcpStates.Connection();
        assertTrue(fromStarting.to(AcpConnectionState.FAILED));

        AcpStates.Connection fromReady = new AcpStates.Connection();
        assertTrue(fromReady.to(AcpConnectionState.INITIALIZING));
        assertTrue(fromReady.to(AcpConnectionState.READY));
        assertTrue("process death while READY", fromReady.to(AcpConnectionState.FAILED));
        assertFalse(fromReady.to(AcpConnectionState.FAILED));
        assertFalse("FAILED is not CLOSED", fromReady.to(AcpConnectionState.CLOSED));

        AcpStates.Connection skipping = new AcpStates.Connection();
        assertFalse("STARTING -> READY skips initialize", skipping.to(AcpConnectionState.READY));
        assertFalse("never back to STARTING", skipping.to(AcpConnectionState.STARTING));
        assertEquals(AcpConnectionState.STARTING, skipping.get());
    }

    @Test
    public void sessionMayCloseDirectlyButNeverReopen() {
        AcpStates.Session created = new AcpStates.Session();
        assertTrue("never activated, closed right away", created.to(AcpSessionState.CLOSED));

        AcpStates.Session active = new AcpStates.Session();
        assertTrue(active.to(AcpSessionState.ACTIVE));
        assertFalse("ACTIVE -> ACTIVE", active.to(AcpSessionState.ACTIVE));
        assertTrue(active.to(AcpSessionState.CLOSED));
        assertFalse("CLOSED -> CLOSING", active.to(AcpSessionState.CLOSING));
        assertFalse("never back to CREATED", active.to(AcpSessionState.CREATED));
    }

    @Test
    public void promptCannotStartTwiceOrTerminateBeforeRunning() {
        AcpStates.Prompt p = new AcpStates.Prompt();
        assertFalse("IDLE -> COMPLETED", p.to(AcpPromptState.COMPLETED));
        assertFalse("IDLE -> CANCELLING", p.to(AcpPromptState.CANCELLING));
        assertTrue(p.to(AcpPromptState.RUNNING));
        assertFalse("RUNNING -> RUNNING", p.to(AcpPromptState.RUNNING));
        assertFalse("never back to IDLE", p.to(AcpPromptState.IDLE));
        assertTrue(p.to(AcpPromptState.FAILED));
        assertEquals(AcpPromptState.FAILED, p.get());
    }
}

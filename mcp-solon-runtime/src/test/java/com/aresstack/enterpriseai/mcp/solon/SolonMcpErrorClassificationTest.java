package com.aresstack.enterpriseai.mcp.solon;

import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;

import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Nur "Invalid params" des Servers ist ein Tool-Fehler; jeder andere McpError gilt als nicht erreichbar. */
public class SolonMcpErrorClassificationTest {

    private static McpError rpcError(int code) {
        return new McpError(new McpSchema.JSONRPCResponse.JSONRPCError(code, "x", null));
    }

    @Test
    public void invalidParamsFromTheServerIsAToolFailure() {
        assertTrue(SolonMcpToolClientFactory.isToolFailure(rpcError(-32602)));
        assertTrue(SolonMcpToolClientFactory.isToolFailure(new IllegalStateException("wrapped", rpcError(-32602))));
    }

    @Test
    @SuppressWarnings("deprecation") // McpError(Object) ist der Konstruktor, den das SDK selbst für Nicht-RPC-Fehler nutzt
    public void transportProtocolAndOtherFailuresMeanUnavailable() {
        assertFalse(SolonMcpToolClientFactory.isToolFailure(rpcError(-32603)));
        assertFalse(SolonMcpToolClientFactory.isToolFailure(rpcError(-32601)));
        assertFalse(SolonMcpToolClientFactory.isToolFailure(new McpError("parse failure")));
        assertFalse(SolonMcpToolClientFactory.isToolFailure(new IllegalStateException(new IOException("refused"))));
        assertFalse(SolonMcpToolClientFactory.isToolFailure(new RuntimeException()));
    }
}

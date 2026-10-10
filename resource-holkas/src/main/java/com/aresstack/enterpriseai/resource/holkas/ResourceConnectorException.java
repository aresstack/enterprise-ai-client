package com.aresstack.enterpriseai.resource.holkas;

import java.io.IOException;

/**
 * Connector-level failure while acquiring or listing a raw resource.
 */
public class ResourceConnectorException extends IOException {

    public ResourceConnectorException(String message) {
        super(message);
    }

    public ResourceConnectorException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.strands.agents.mcp;

import java.io.Closeable;

public interface McpTransport extends Closeable {

    void connect() throws Exception;

    String sendAndReceive(String message) throws Exception;

    boolean isConnected();
}

package com.strands.agents.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.*;
import org.junit.jupiter.api.Test;

class StdioTransportTest {

    @Test
    void shouldCommunicateWithSubprocess() throws Exception {
        var transport = new StdioTransport("bash", "-c",
            "read line; echo \"$line\"");

        transport.connect();
        var response = transport.sendAndReceive("{\"test\": true}");
        assertThat(response).isEqualTo("{\"test\": true}");
        transport.close();
    }

    @Test
    void shouldHandleMultipleMessages() throws Exception {
        var transport = new StdioTransport("bash", "-c",
            "while read line; do echo \"$line\"; done");

        transport.connect();
        assertThat(transport.sendAndReceive("msg1")).isEqualTo("msg1");
        assertThat(transport.sendAndReceive("msg2")).isEqualTo("msg2");
        transport.close();
    }

    @Test
    void shouldTrackConnectionState() throws Exception {
        var transport = new StdioTransport("cat");
        assertThat(transport.isConnected()).isFalse();
        transport.connect();
        assertThat(transport.isConnected()).isTrue();
        transport.close();
        assertThat(transport.isConnected()).isFalse();
    }
}

package com.strands.agents.telemetry;

import com.strands.agents.core.AgentEventListener;
import com.strands.agents.core.model.event.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingHook implements AgentEventListener {

    private static final Logger log = LoggerFactory.getLogger(LoggingHook.class);

    @Override
    public void onEvent(AgentEvent event) {
        switch (event) {
            case AgentStartedEvent e -> log.info("Agent gestartet: session={}, prompt=\"{}\"",
                e.sessionId(), truncate(e.initialPrompt(), 80));
            case ModelRequestedEvent e -> log.debug("LLM-Call: session={}, messages={}",
                e.sessionId(), e.promptHistory().size());
            case ToolExecutionStartedEvent e -> log.info("Tool gestartet: session={}, tool={}",
                e.sessionId(), e.toolCall().toolName());
            case ToolExecutionFinishedEvent e -> log.debug("Tool beendet: session={}, tool={}, error={}",
                e.sessionId(), e.result().toolName(), e.result().isError());
            case TokenEvent e -> log.trace("Token: session={}, token=\"{}\"",
                e.sessionId(), e.token());
            case AgentFinishedEvent e -> log.info("Agent beendet: session={}, answer=\"{}\"",
                e.sessionId(), truncate(e.finalAnswer(), 80));
        }
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}

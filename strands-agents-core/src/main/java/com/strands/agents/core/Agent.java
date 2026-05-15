package com.strands.agents.core;

import com.strands.agents.core.model.agent.AgentResult;

public interface Agent {
    AgentResult execute(String prompt);
}

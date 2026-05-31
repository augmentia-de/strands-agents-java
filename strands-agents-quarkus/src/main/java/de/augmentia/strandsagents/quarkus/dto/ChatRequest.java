package de.augmentia.strandsagents.quarkus.dto;

import java.util.List;

public class ChatRequest {
    public String prompt;
    public String sessionId;
    public List<String> tools;
    public List<String> skills;
    public String systemPrompt;
}

package de.augmentia.agenttest;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

public record StepSchemas(Map<String, JsonNode> stepSchemas) {}

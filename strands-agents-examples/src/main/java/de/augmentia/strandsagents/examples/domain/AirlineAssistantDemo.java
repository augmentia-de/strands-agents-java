package de.augmentia.strandsagents.examples.domain;

import de.augmentia.strandsagents.core.*;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;

import java.nio.file.Path;
import java.util.Collections;

public class AirlineAssistantDemo {

    public static void main(String[] args) {
        System.out.println("🚀 Initializing Airline Assistant Agent with Strands...");

        // 1. ChatModel: The core LLM (using OpenAI from environment variables)
        ChatModel model = ModelFactory.createOpenAiFromEnv();

        // 2. ToolRegistry: Registering tools the agent can use
        ToolRegistry toolRegistry = new ToolRegistry();
        AirlineService airlineService = new AirlineService();
        toolRegistry.register(airlineService); // Register the service as a tool provider

        // 3. ToolExecutor: The engine that runs the tools
        ToolExecutor toolExecutor = new ToolExecutor();

        // 4. ConversationManager: Handles chat history (e.g., sliding window of 10 messages)
        ConversationManager conversationManager = new SlidingWindowConversationManager(10);

        // 5. SessionManager: Persists sessions to local JSON files
        SessionManager sessionManager = new FileSessionManager(Path.of("logs/sessions"));

        // 6. ResilienceConfig: Not strictly needed for this example, but can be added if desired
        // ResilienceConfig resilienceConfig = new ResilienceConfig(...);

        // 7. Plugins: Not strictly needed for this example, but can be added if desired
        // List<Plugin> plugins = List.of(...);

        // 8. Hooks: Not strictly needed for this example, but can be added if desired
        // HookRegistry hookRegistry = new HookRegistry();

        // --- INSTANTIATION ---
        Agent agent = new Agent(
            model,
            toolRegistry,
            toolExecutor,
            conversationManager,
            sessionManager,
            null, // resilienceConfig (optional)
            Collections.emptyList(), // plugins (optional)
            null // hookRegistry (optional)
        );

        // Set a system prompt to guide the agent's persona
        agent.setSystemPrompt("You are a helpful airline assistant. You can book flights and check flight statuses.");

        // --- EXECUTION ---
        System.out.println("\n[User]: Hello, I need help with my flight.");
        AgentResult result1 = agent.execute("Hello, I need help with my flight.");
        System.out.println("[Agent]: " + result1.finalAnswer());

        System.out.println("\n[User]: Can I book a flight from London to New York?");
        AgentResult result2 = agent.execute("Can I book a flight from London to New York?");
        System.out.println("[Agent]: " + result2.finalAnswer());

        System.out.println("\n[User]: What is the status of flight BA286?");
        AgentResult result3 = agent.execute("What is the status of flight BA286?");
        System.out.println("[Agent]: " + result3.finalAnswer());
    }

    static class AirlineService {

        @Tool
        public String bookFlight(String origin, String destination) {
            return String.format("Booking flight from %s to %s.", origin, destination);
        }

        @Tool
        public String getFlightStatus(String flightNumber) {
            return String.format("Getting status for flight %s.", flightNumber);
        }
    }
}

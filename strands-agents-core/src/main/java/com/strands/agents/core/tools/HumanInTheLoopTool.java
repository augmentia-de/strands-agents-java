package com.strands.agents.core.tools;

import com.strands.agents.core.ApprovalResult;
import com.strands.agents.core.HITLProvider;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.Scanner;

/**
 * Tool that allows the agent to explicitly hand off control to a human user.
 * This mirrors the 'handoff_to_user.py' tool from the Python SDK.
 * 
 * It can be used to:
 * 1. Ask for clarification
 * 2. Get approval for critical actions
 * 3. Request missing information
 */
public class HumanInTheLoopTool {

    private final HITLProvider provider;

    /**
     * Creates a new HITL tool using the provided provider.
     * @param provider The provider to use for human interaction.
     */
    public HumanInTheLoopTool(HITLProvider provider) {
        this.provider = provider != null ? provider : new ConsoleHITLProvider();
    }

    /**
     * Default constructor using a console-based provider.
     */
    public HumanInTheLoopTool() {
        this(new ConsoleHITLProvider());
    }

    @Tool("Asks the human user for input, clarification, or approval to proceed.")
    public String askUser(@P("The message or question to display to the user") String message) {
        // Log the handoff request
        System.out.println("\n🤝 [AGENT REQUESTING HUMAN INTERVENTION]");
        System.out.println("Message: " + message);
        
        // Delegate to the provider to get the actual human response
        ApprovalResult result = provider.requestApproval("handoff_to_user", message);
        String response = result.feedback();
        
        System.out.println("👤 [USER RESPONSE RECEIVED]: " + response + "\n");
        return response;
    }

    /**
     * A simple console-based implementation of a HITLProvider.
     */
    public static class ConsoleHITLProvider implements HITLProvider {
        private final Scanner scanner = new Scanner(System.in);

        @Override
        public ApprovalResult requestApproval(String action, String context) {
            System.out.print("> ");
            String input = scanner.nextLine();
            return new ApprovalResult(action, true, input, java.time.Instant.now());
        }
    }
}

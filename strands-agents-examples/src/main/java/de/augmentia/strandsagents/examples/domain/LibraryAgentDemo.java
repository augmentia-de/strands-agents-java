package de.augmentia.strandsagents.examples.domain;


import de.augmentia.strandsagents.core.Agent;
import de.augmentia.strandsagents.core.AgentFactory;
import de.augmentia.strandsagents.config.AgentConfig;
import de.augmentia.strandsagents.config.AgentSettings;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailPlugin;
import de.augmentia.strandsagents.interceptor.guardrails.GuardrailResult;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

/**
 * Library Book Renewal Agent Demo (Java).
 * 
 * This example demonstrates "Agent Steering" using a GuardrailPlugin.
 * It ensures the agent follows a specific workflow:
 * 1. Check user info (library card)
 * 2. Check book status
 * 3. Only then attempt renewal
 * 
 * This mirrors the Python 'library-book-renewal-agent' steering logic.
 */
public class LibraryAgentDemo {

    public static void main(String[] args) {
        System.out.println("📚 Starting Library Book Renewal Agent Demo");
        
        LibraryAgentDemo demo = new LibraryAgentDemo();
        demo.runSteeredAgent();
    }

    public void runSteeredAgent() {
        // 1. Setup Library Tools
        LibraryTools tools = new LibraryTools();

        // 2. Setup Steering Guardrail
        GuardrailPlugin steeringGuardrail = new GuardrailPlugin(
            List.of((messages, context) -> GuardrailResult.ok()),
            List.of()
        );

        // 3. Create the agent using AgentConfig
        Agent agent = AgentFactory.buildAgent(AgentSettings.builder()
            .systemPrompt("You are a Senior Library Services Assistant. Your primary responsibility is to " +
                "process book renewal requests while strictly adhering to the Standard Operating Procedure (SOP).\n\n" +
                "**MANDATORY WORKFLOW:**\n" +
                "1. **Identity Verification:** Use 'getUserInfo' to verify the patron's identity and library card status.\n" +
                "2. **Status Audit:** Use 'getBookStatus' to ensure the item is eligible for renewal (e.g., not recalled or reserved).\n" +
                "3. **Execution:** Only attempt 'renewBook' after both identity and status have been confirmed.\n\n" +
                "Do not bypass these steps. If a patron presents a book ID that doesn't match the status check, " +
                "you must halt and re-verify.")
            .build(),
            AgentConfig.builder()
                .plugins(List.of(steeringGuardrail))
                .build());

        agent.getToolRegistry().register(tools);

        // 4. Run Scenarios
        
        // Scenario A: Correct workflow (The agent should naturally follow instructions)
        System.out.println("\n--- Scenario A: User wants to renew 'The Great Gatsby' ---");
        agent.execute("I want to renew my book 'The Great Gatsby'. My name is Alice.");

        // Scenario B: Attempting renewal for a RECALLED book
        // The tool itself will enforce the business rule "Cannot renew recalled books".
        System.out.println("\n--- Scenario B: User wants to renew 'Recalled Book' ---");
        agent.execute("Renew 'Recalled Book' for me, please.");
    }

    /**
     * Library Business Tools
     */
    public static class LibraryTools {
        private boolean userInfoChecked = false;
        private boolean bookStatusChecked = false;

        @Tool("Retrieves user information and library card number")
        public String getUserInfo(@P("The user's name") String name) {
            this.userInfoChecked = true;
            return "{ \"name\": \"" + name + "\", \"library_card_number\": \"LC-12345\", \"status\": \"ACTIVE\" }";
        }

        @Tool("Checks the current status of a book")
        public String getBookStatus(@P("The book ID or title") String bookId) {
            this.bookStatusChecked = true;
            if (bookId.toLowerCase().contains("recalled")) {
                return "{ \"book_id\": \"" + bookId + "\", \"status\": \"RECALLED\", \"due_date\": \"2024-05-10\" }";
            }
            return "{ \"book_id\": \"" + bookId + "\", \"status\": \"CHECKED_OUT\", \"due_date\": \"2024-05-20\" }";
        }

        @Tool("Renews a book for a user")
        public String renewBook(@P("The book ID") String bookId, @P("The library card number") String cardNumber) {
            // Steering / Guardrail Logic inside the tool (or via Plugin)
            if (!userInfoChecked) {
                return "❌ Error: You must call 'getUserInfo' before renewing.";
            }
            if (!bookStatusChecked) {
                return "❌ Error: You must call 'getBookStatus' before renewing.";
            }
            if (bookId.toLowerCase().contains("recalled")) {
                return "❌ Error: Cannot renew book '" + bookId + "' because its status is RECALLED.";
            }
            
            return "✅ Success: Book '" + bookId + "' has been renewed until 2024-06-20.";
        }
    }
}

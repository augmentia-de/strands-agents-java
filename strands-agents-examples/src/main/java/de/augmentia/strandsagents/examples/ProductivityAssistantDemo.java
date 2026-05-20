package de.augmentia.strandsagents.examples;


import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.agent.a2a.AgentTool;
import de.augmentia.strandsagents.core.config.ModelFactory;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * Productivity Assistant Demo (Java).
 * 
 * This sample demonstrates a multi-agent productivity system that coordinates:
 * 1. Email Assistant (Writing and formatting emails)
 * 2. Calendar Assistant (Managing appointments)
 * 3. Search Assistant (Finding information)
 * 
 * This mirrors the Python 'personal-assistant' use case.
 */
public class ProductivityAssistantDemo {

    public static void main(String[] args) {
        System.out.println("🤖 Welcome to your Personal Productivity Assistant");
        
        ProductivityAssistantDemo demo = new ProductivityAssistantDemo();
        demo.runAssistant();
    }

    public void runAssistant() {
        // 1. Create specialized sub-agents
        Agent emailAgent = createEmailAgent();
        Agent calendarAgent = createCalendarAgent();
        Agent searchAgent = createSearchAgent();

        // 2. Setup the Coordinator (Personal Assistant)
        Agent coordinator = new Agent(ModelFactory.createOpenAiFromEnv());
        coordinator.setSystemPrompt("You are an advanced Personal Productivity Orchestrator. " +
                "Your role is to maximize the user's efficiency by coordinating a team of specialized agents:\n" +
                "- **email_agent**: High-level communication specialist for drafting, analyzing, and managing professional emails.\n" +
                "- **calendar_agent**: Logistics specialist for schedule optimization, appointment management, and conflict resolution.\n" +
                "- **search_agent**: Intelligence specialist for deep web research and information synthesis.\n\n" +
                "Be proactive, concise, and professional. Seamlessly bridge information between agents to complete complex, multi-stage workflows.");

        // Register sub-agents as tools using the Agent-as-a-Tool pattern
        coordinator.getToolRegistry().register(new AgentTool(emailAgent, "email_agent", 
                "Strategic communication expert for professional email drafting and analysis."));
        coordinator.getToolRegistry().register(new AgentTool(calendarAgent, "calendar_agent", 
                "Efficiency expert for calendar management and schedule coordination."));
        coordinator.getToolRegistry().register(new AgentTool(searchAgent, "search_agent", 
                "Research expert for gathering and synthesizing information from across the web."));

        // 3. Example Complex Task
        String task = "I need to prepare for a meeting tomorrow. " +
                     "Research the current state of Quantum Computing and then " +
                     "draft a summary email I can send to my team. " +
                     "Also, check if I have any other meetings tomorrow morning.";
        
        System.out.println("\n[User Task]: " + task);
        System.out.println("\n🤖 Coordinator is working...");
        
        var result = coordinator.execute(task);

        System.out.println("\n==========================================");
        System.out.println("🏁 ASSISTANT RESPONSE");
        System.out.println("==========================================");
        System.out.println(result.finalAnswer());
        System.out.println("==========================================");
    }

    private Agent createEmailAgent() {
        Agent agent = new Agent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Professional Communications Consultant. " +
                "Draft emails that are clear, impactful, and tailored to the target audience.");
        return agent;
    }

    private Agent createCalendarAgent() {
        Agent agent = new Agent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Logistics and Schedule Optimizer. " +
                "Ensure calendars are organized and conflicts are proactively identified.");
        agent.getToolRegistry().register(new CalendarTools());
        return agent;
    }

    private Agent createSearchAgent() {
        Agent agent = new Agent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Lead Intelligence Researcher. " +
                "Provide high-signal, accurate information synthesized from multiple web sources.");
        agent.getToolRegistry().register(new SearchTools());
        return agent;
    }

    // --- Simulated Tools ---

    public static class CalendarTools {
        @Tool("Lists appointments for a specific date")
        public String listAppointments(@P("The date in YYYY-MM-DD format") String date) {
            return "📅 Appointments for " + date + ":\n" +
                   "- 09:00: Team Standup\n" +
                   "- 14:00: Client Review\n" +
                   "- 16:30: Project Planning";
        }

        @Tool("Schedules a new appointment")
        public String scheduleAppointment(String title, String startTime, String duration) {
            return "✅ Successfully scheduled '" + title + "' at " + startTime + " (" + duration + ").";
        }
    }

    public static class SearchTools {
        @Tool("Searches the web for a given query")
        public String webSearch(@P("The search query") String query) {
            if (query.toLowerCase().contains("quantum computing")) {
                return "Quantum computing is a type of computing that uses quantum-mechanical phenomena. " +
                       "Key trends in 2024 include: error correction breakthroughs, increased qubit counts by IBM and Google, " +
                       "and rising investment in post-quantum cryptography.";
            }
            return "Search result for: " + query + " - No specific information found.";
        }
    }
}

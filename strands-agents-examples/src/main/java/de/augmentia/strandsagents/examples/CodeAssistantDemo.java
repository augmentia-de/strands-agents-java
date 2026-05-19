package de.augmentia.strandsagents.examples;


import de.augmentia.strandsagents.core.agent.StrandsAgent;
import de.augmentia.strandsagents.core.agent.a2a.AgentTool;
import de.augmentia.strandsagents.core.config.ModelFactory;
import de.augmentia.strandsagents.core.tools.BashTool;
import de.augmentia.strandsagents.core.tools.WriteTool;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.io.File;
import java.nio.file.Path;

/**
 * Code Assistant Agent Demo (Java).
 * 
 * This sample demonstrates a multi-agent system for software engineering tasks:
 * 1. Project Reading (Analyzing existing codebases)
 * 2. Code Generation (Creating new logic)
 * 3. Code Review (Optimizing and checking for best practices)
 * 4. Code Execution (Running and testing code)
 * 
 * Architecture:
 * Code Assistant (Coordinator)
 *   ├── Generator Sub-Agent (Writing logic)
 *   ├── Reviewer Sub-Agent (Code Analysis)
 *   └── Executor Sub-Agent (Running Bash/Scripts)
 */
public class CodeAssistantDemo {

    public static void main(String[] args) {
        System.out.println("💻 Welcome to the Java Code Assistant");
        
        CodeAssistantDemo demo = new CodeAssistantDemo();
        demo.runCodeAssistant();
    }

    public void runCodeAssistant() {
        // 1. Setup specialized sub-agents
        StrandsAgent generator = createGeneratorAgent();
        StrandsAgent reviewer = createReviewerAgent();
        StrandsAgent executor = createExecutorAgent();

        // 2. Setup the Coordinator (Code Assistant)
        StrandsAgent assistant = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        assistant.setSystemPrompt("You are an elite Software Engineering Assistant specializing in multi-language development.\n\n" +
                "Your mission is to resolve complex engineering tasks by orchestrating a team of specialized agents:\n" +
                "- **code_generator**: Use this to write clean, efficient, and idiomatic code or scripts.\n" +
                "- **code_reviewer**: Use this to analyze code for quality, performance bottlenecks, security vulnerabilities, and adherence to SOLID principles.\n" +
                "- **code_executor**: Use this to run code, execute unit tests, or perform terminal operations for verification.\n" +
                "- **project_reader**: Use this to ingest and analyze the context of existing project directories.\n\n" +
                "Always follow a 'Think -> Plan -> Execute -> Review' workflow. Verify your logic before finalizing the output.");

        // Register sub-agents and tools
        assistant.getToolRegistry().register(new AgentTool(generator, "code_generator", 
                "Expert in writing clean, functional, and idiomatic code across multiple languages."));
        assistant.getToolRegistry().register(new AgentTool(reviewer, "code_reviewer", 
                "Expert in deep code analysis, identifying bugs, security issues, and performance optimization opportunities."));
        assistant.getToolRegistry().register(new AgentTool(executor, "code_executor", 
                "Operations specialist capable of executing scripts, running tests, and managing terminal-level system tasks."));
        assistant.getToolRegistry().register(new ProjectTools());

        // 3. Example Engineering Task
        String task = "Read the files in the current directory, summarize what the project does, " +
                     "and then generate a simple README.md file for it.";
        
        System.out.println("\n[User Task]: " + task);
        System.out.println("\n💻 Assistant is analyzing...");
        
        var result = assistant.execute(task);

        System.out.println("\n==========================================");
        System.out.println("🏁 ENGINEERING SUMMARY");
        System.out.println("==========================================");
        System.out.println(result.finalAnswer());
        System.out.println("==========================================");
    }

    private StrandsAgent createGeneratorAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Lead Software Developer. Your output must be high-quality, " +
                "production-ready, and extensively documented code. Prioritize readability and maintainability.");
        agent.getToolRegistry().register(new WriteTool(Path.of("")));
        return agent;
    }

    private StrandsAgent createReviewerAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Senior Code Reviewer. You analyze code for performance, security, " +
                "and architectural soundness. Provide actionable improvements and highlight potential pitfalls.");
        return agent;
    }

    private StrandsAgent createExecutorAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a DevSecOps Specialist. You execute system commands and scripts with precision. " +
                "Always report execution status and capture all logs/outputs for analysis.");
        agent.getToolRegistry().register(new BashTool(Path.of("")));
        return agent;
    }

    // --- Specialized Engineering Tools ---

    public static class ProjectTools {
        @Tool("Reads all source files in a given directory to provide context")
        public String project_reader(@P("The directory path to analyze") String path) {
            File dir = new File(path);
            if (!dir.exists() || !dir.isDirectory()) {
                return "Error: Directory not found or invalid.";
            }

            File[] files = dir.listFiles();
            if (files == null || files.length == 0) {
                return "The directory is empty.";
            }

            StringBuilder sb = new StringBuilder("Project Structure at " + path + ":\n");
            for (File f : files) {
                if (f.isFile()) {
                    sb.append("- ").append(f.getName()).append(" (Size: ").append(f.length()).append(" bytes)\n");
                }
            }
            return sb.toString();
        }
    }
}

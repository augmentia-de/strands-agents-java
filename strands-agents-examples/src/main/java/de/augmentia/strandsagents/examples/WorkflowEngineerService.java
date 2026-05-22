package de.augmentia.strandsagents.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.augmentia.strandsagents.core.ToolExecutor;
import de.augmentia.strandsagents.core.ToolRegistry;
import de.augmentia.strandsagents.core.agent.Agent;
import de.augmentia.strandsagents.core.conversation.ConversationManager;
import de.augmentia.strandsagents.core.conversation.SlidingWindowConversationManager;
import de.augmentia.strandsagents.core.model.agent.AgentResult;
import de.augmentia.strandsagents.core.model.agent.StopReason;
import de.augmentia.strandsagents.core.resilience.CircuitBreakerConfig;
import de.augmentia.strandsagents.core.resilience.ResilienceConfig;
import de.augmentia.strandsagents.core.resilience.RetryConfig;
import de.augmentia.strandsagents.core.tools.*;
import de.augmentia.strandsagents.sessions.FileSessionManager;
import de.augmentia.strandsagents.sessions.SessionManager;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

public class WorkflowEngineerService {
    private static final Logger log = LoggerFactory.getLogger(WorkflowEngineerService.class);

    private final ObjectMapper objectMapper;

    static {
        System.setProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager");
    }

    public WorkflowEngineerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public record DesignResult(boolean success, String filename) {}
    public record DesignerOutput(boolean success, String yaml, String filename) {}

    public AgentResult designAndRunWorkflow(String taskDescription, String projectRoot) {
        log.info("WorkflowEngineerService: designAndRunWorkflow for task: {}", taskDescription);
        
        // Phase 1: Design
        AgentResult designResult = designWorkflow(taskDescription, projectRoot);
        if (designResult.stopReason() == StopReason.ERROR) {
            return designResult;
        }

        if (designResult.structuredOutput() == null) {
            log.warn("WorkflowEngineerService: Design phase finished but no structured output found.");
            return designResult;
        }

        try {
            DesignerOutput dr = objectMapper.readValue(designResult.structuredOutput(), DesignerOutput.class);
            if (!dr.success() || dr.filename() == null) {
                log.warn("WorkflowEngineerService: Design phase reported failure or missing filename.");
                return designResult;
            }

            log.info("WorkflowEngineerService: Design phase successful. File created: {}. Starting execution phase...", dr.filename());

            // Phase 2: Run
            String runPrompt = String.format(
                "Execute the workflow defined in '%s'. User task context: %s",
                dr.filename(), taskDescription);
            
            return runWorkflow(runPrompt, projectRoot);

        } catch (Exception e) {
            log.error("WorkflowEngineerService: Failed to process DesignerOutput: {}", e.getMessage());
            return designResult;
        }
    }

    public AgentResult designWorkflow(String taskDescription, String projectRoot) {
        log.info("WorkflowEngineerService: Starting radical design split for: {}", taskDescription);
        
        // 1. Research phase
        String researchContext = performResearch(projectRoot);
        log.info("WorkflowEngineerService: Research phase completed.");

        // 2. Design phase (Generates YAML)
        AgentResult designResult = executeDesignerAgent(taskDescription, researchContext, projectRoot);
        
        if (designResult.stopReason() == StopReason.COMPLETED && designResult.structuredOutput() != null) {
            try {
                DesignerOutput output = objectMapper.readValue(designResult.structuredOutput(), DesignerOutput.class);
                if (output.success() && output.yaml() != null && output.filename() != null) {
                    writeWorkflowFiles(output.yaml(), output.filename(), projectRoot);
                }
            } catch (Exception e) {
                log.error("WorkflowEngineerService: Failed to write workflow files: {}", e.getMessage());
            }
        }
        
        return designResult;
    }

    private String performResearch(String projectRoot) {
        ChatModel model = createChatModel();
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new ReadTool(Path.of(projectRoot)));
        ToolExecutor toolExecutor = new ToolExecutor();
        
        Agent researcher = new Agent(model, toolRegistry, toolExecutor);
        researcher.setSystemPrompt("""
            You are a Research Agent for the Collaborative Agent Mesh (CAM) platform. 
            Your task is to extract available agent roles and workflow constraints.
            Read AGENT_USE.md and search for relevant documentation in the docs/ directory.
            Return a concise summary of:
            1. Available Roles for agents in the workflow.
            2. Workflow Parser Constraints or specific naming conventions.
            3. YAML Schema details for standard, loop, switch, join, and discussion steps.
            """);
            
        AgentResult result = researcher.execute("Find agent roles and workflow constraints in the documentation.");
        return result.finalAnswer();
    }

    private AgentResult executeDesignerAgent(String taskDescription, String researchContext, String projectRoot) {
        ChatModel model = createChatModel();
        Agent agent = new Agent(model);
        
        agent.setStructuredOutputModel(DesignerOutput.class);
        agent.setSystemPrompt(String.format("""
            You are the Workflow Designer Agent for the Collaborative Agent Mesh (CAM) platform. 
            Your sole task is to design new workflows by generating a valid YAML definition based on the user's requirements.

            ## Research Context (Use these roles and rules)
            %s

            ## Workflow Schema Rules
            - name: UPPERCASE_WITH_UNDERSCORES (e.g., CODE_REVIEW)
            - start: ID of the first step.
            - steps: Map of step definitions. Terminal steps must NOT have a 'next' field.

            ## Step Types & Required Fields
            - standard: Default step (no type field needed). Fields: [role, next, in, out]
            - loop: Retry gateway. Type: 'loop'
            - switch: Conditional routing gateway. Type: 'switch'. 'next' must be a map of conditions to target step IDs.
            - join: Parallel synchronization gateway. Type: 'join'. Waits for all predecessor steps.
            - discussion: Multi-agent panel. Type: 'discussion'. Additional fields required: [pool (list of roles), moderator (role), moderator_interval (int), max_rounds (int)]

            ## Input Mapping Syntax ('in' field)
            Format: `source_step.field_name: target_variable`
            Special Source: `context.field_name` (for original user input data)

            ## Output Schema ('out' field)
            Define structured output fields with types: string, number, boolean, array, or nested object.

            Format your response strictly as a valid YAML block in the 'yaml' field of your structured output. 
            Do not try to use tools to write files.
            """, researchContext));

        return agent.execute(taskDescription);
    }

    private void writeWorkflowFiles(String yaml, String filename, String projectRoot) throws Exception {
        Path basePath = Path.of(projectRoot);
        Path fileNamePath = Path.of(filename).getFileName();
        
        // Location 1: setup/workflows/
        Path setupPath = basePath.resolve("setup/workflows").resolve(fileNamePath);
        Files.createDirectories(setupPath.getParent());
        Files.writeString(setupPath, yaml);
        log.info("WorkflowEngineerService: Wrote workflow to {}", setupPath);

        // Location 2: strands-agents-quarkus/src/main/resources/workflows/
        Path resourcesPath = basePath.resolve("strands-agents-quarkus/src/main/resources/workflows").resolve(fileNamePath);
        if (Files.exists(basePath.resolve("strands-agents-quarkus"))) {
            Files.createDirectories(resourcesPath.getParent());
            Files.writeString(resourcesPath, yaml);
            log.info("WorkflowEngineerService: Wrote workflow to {}", resourcesPath);
        }
    }

    public AgentResult runWorkflow(String runDescription, String projectRoot) {
        log.info("WorkflowEngineerService: runWorkflow for task: {}", runDescription);
        return executeAgent(runDescription, projectRoot, buildRunSystemPrompt(), "run");
    }

    private AgentResult executeAgent(String prompt, String projectRoot, String systemPrompt, String sessionSuffix) {
        ChatModel model = createChatModel();
        Path basePath = Path.of(projectRoot);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(new BashTool(basePath));
        toolRegistry.register(new ReadTool(basePath));
        toolRegistry.register(new WriteTool(basePath));
        toolRegistry.register(new EditTool(basePath));
        toolRegistry.register(new FindTool(basePath));
        toolRegistry.register(new GrepTool(basePath));
        toolRegistry.register(new LsTool(basePath));

        ToolExecutor toolExecutor = new ToolExecutor(300_000L);

        ConversationManager conversationManager = new SlidingWindowConversationManager(20);

        SessionManager sessionManager = new FileSessionManager(
                basePath.resolve("logs/sessions/workflow-engineer-" + sessionSuffix + "-" + UUID.randomUUID()));

        ResilienceConfig resilienceConfig = new ResilienceConfig(
                new RetryConfig(3, 1000, 2.0),
                new CircuitBreakerConfig(0.5f, 10L, 30L));

        Agent agent = new Agent(model, toolRegistry, toolExecutor,
                conversationManager, sessionManager, resilienceConfig, List.of());

        agent.setSystemPrompt(systemPrompt);
        if (sessionSuffix.startsWith("design")) {
            agent.setStructuredOutputModel(DesignerOutput.class);
        }

        log.info("WorkflowEngineerService: Executing agent ({})...", sessionSuffix);
        AgentResult result = agent.execute(prompt);
        log.info("WorkflowEngineerService: Agent ({}) finished. Stop reason: {}", sessionSuffix, result.stopReason());

        if (result.stopReason() == StopReason.ERROR) {
            log.error("WorkflowEngineerService: Agent ({}) encountered an error.", sessionSuffix);
            log.error("  finalAnswer: {}", result.finalAnswer());
            if (result.structuredOutput() != null) {
                log.error("  structuredOutput: {}", result.structuredOutput());
            }
        }

        return result;
    }

    private ChatModel createChatModel() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException(
                    "OPENAI_API_KEY environment variable is not set. "
                    + "The WorkflowEngineerService requires an OpenAI-compatible API key. "
                    + "Set OPENAI_API_KEY in your environment or .env file.");
        }
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(System.getenv().getOrDefault("LLM_CHAT_MODEL", "google/gemini-3.1-flash-lite"))
                .temperature(0.3);

        String baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://openrouter.ai/api/v1");
        if (baseUrl != null && !baseUrl.isEmpty()) {
            builder.baseUrl(baseUrl);
        }

        return builder.build();
    }

    private String buildRunSystemPrompt() {
        return """
                You are a workflow executor agent for the CAM (Collaborative Agent Mesh) platform.
                Your task is to launch existing workflow definitions via the REST API.
                
                ## Your Workflow
                
                1. Read the existing workflow YAML files in `setup/workflows/` to find available workflow types.
                2. Based on the user's request, select the appropriate workflow type.
                3. Read the selected workflow's YAML to understand what payload the first step expects.
                4. Start the workflow by calling the REST API using curl:
                   ```
                   curl -s -X POST http://localhost:8087/api/v1/workflows/start \
                     -H "Content-Type: application/json" \
                     -d '{"workflow_type": "<NAME>", "payload": { ... }}'
                   ```
                5. Construct an appropriate payload matching the first step's output schema fields.
                6. Report the workflow_id from the response.
                
                ## Constraints
                
                - NEVER use the `rm` command or any destructive shell operations.
                - You may NOT create or modify YAML files — only execute existing workflows.
                - Construct meaningful payload data based on the user's description.
                """;
    }

    public static void main(String[] args) {
        WorkflowEngineerService svc = new WorkflowEngineerService(new ObjectMapper());

        log.info("=== BuildWorkflow started ===");

        // Option 1: Design only — research + create YAML files
        var designResult = svc.designWorkflow(
            "Create a triage workflow for security incidents: intake -> classify severity -> [low→auto-response, high→expert-review] -> resolve",
            ".");

        // Option 2: Full pipeline — research + design + create + execute
        log.info("Starting designAndRunWorkflow...");
        var fullResult = svc.designAndRunWorkflow(
                "Create a workflow that processes customer feedback: classify sentiment, route to appropriate team, generate response",
                ".");
        log.info("designAndRunWorkflow completed. stopReason={}", fullResult.stopReason());
        log.info("=== BuildWorkflow finished ===");

    }
}

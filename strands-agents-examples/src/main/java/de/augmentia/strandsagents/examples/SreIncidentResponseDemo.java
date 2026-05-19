package de.augmentia.strandsagents.examples;

import de.augmentia.strandsagents.core.AgentTool;
import de.augmentia.strandsagents.core.ModelFactory;
import de.augmentia.strandsagents.core.StrandsAgent;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

/**
 * SRE Incident Response Agent Demo (Java).
 * 
 * This sample demonstrates a multi-agent SRE system that:
 * 1. Monitors CloudWatch (simulated) for alarms.
 * 2. Performs Root Cause Analysis (RCA) via a specialized sub-agent.
 * 3. Proposes remediation actions (kubectl/helm).
 * 4. Generates an incident report.
 * 
 * Architecture:
 * Supervisor (Incident Commander)
 *   ├── Monitoring Sub-Agent (CloudWatch)
 *   ├── RCA Sub-Agent (SRE Reasoning)
 *   └── Operations Sub-Agent (K8s/Remediation)
 */
public class SreIncidentResponseDemo {

    public static void main(String[] args) {
        System.out.println("🚨 Starting SRE Incident Response Demo (Java)");
        
        SreIncidentResponseDemo demo = new SreIncidentResponseDemo();
        demo.runIncidentWorkflow();
    }

    public void runIncidentWorkflow() {
        // 1. Setup specialized agents
        StrandsAgent monitoringAgent = createMonitoringAgent();
        StrandsAgent rcaAgent = createRcaAgent();
        StrandsAgent opsAgent = createOpsAgent();
        
        // 2. Setup the Supervisor (Incident Commander)
        // We use the "Agents-as-Tools" pattern, just like in the Python example.
        StrandsAgent supervisor = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        supervisor.setSystemPrompt("You are the SRE Incident Commander (IC) leading a critical system response.\n\n" +
                "Your objective is to restore service stability by orchestrating specialized SRE agents:\n" +
                "1. **Audit:** Use 'monitoring_agent' to gather high-fidelity alarm data, performance metrics, and log segments.\n" +
                "2. **Diagnose:** Use 'rca_agent' to perform Root Cause Analysis (RCA), assess blast radius, and determine severity.\n" +
                "3. **Mitigate:** Use 'ops_agent' to apply the safest remediation action (restart, rollback, or scale).\n" +
                "4. **Report:** Synthesize all findings into a professional Incident Report including resolution steps and follow-up items.");

        // Register sub-agents as tools
        supervisor.getToolRegistry().register(new AgentTool(monitoringAgent, "monitoring_agent", 
                "Expert monitoring specialist capable of auditing CloudWatch alarms, metrics, and logs."));
        supervisor.getToolRegistry().register(new AgentTool(rcaAgent, "rca_agent", 
                "Strategic SRE analyst for deep root cause analysis and impact assessment."));
        supervisor.getToolRegistry().register(new AgentTool(opsAgent, "ops_agent", 
                "Operations specialist for executing Kubernetes and Helm remediation tasks."));

        // 3. Trigger the workflow
        String trigger = "There are reports of high latency in the production-api. Please investigate and fix it.";
        System.out.println("\n[Trigger]: " + trigger);
        
        var result = supervisor.execute(trigger);

        System.out.println("\n==========================================");
        System.out.println("🏁 FINAL INCIDENT REPORT");
        System.out.println("==========================================");
        System.out.println(result.finalAnswer());
        System.out.println("==========================================");
    }

    private StrandsAgent createMonitoringAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a CloudWatch Observability Specialist. " +
                "Extract precise metrics and relevant log lines to provide a clear picture of system health.");
        agent.getToolRegistry().register(new MonitoringTools());
        return agent;
    }

    private StrandsAgent createRcaAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Senior SRE (Root Cause Analysis). " +
                "Analyze telemetry and log data to identify the failure mechanism and evaluate impact.");
        return agent;
    }

    private StrandsAgent createOpsAgent() {
        StrandsAgent agent = new StrandsAgent(ModelFactory.createOpenAiFromEnv());
        agent.setSystemPrompt("You are a Kubernetes and Platform Engineer. " +
                "Execute infrastructure-level remediation with a focus on safety, speed, and reversibility.");
        agent.getToolRegistry().register(new OpsTools());
        return agent;
    }

    // --- Simulated Tools ---

    public static class MonitoringTools {
        @Tool("Lists active CloudWatch alarms")
        public String listAlarms() {
            return "[{ \"name\": \"HighLatency-API\", \"state\": \"ALARM\", \"reason\": \"P99 latency > 500ms\" }]";
        }

        @Tool("Fetches error logs for a service")
        public String fetchLogs(String serviceName) {
            return "2024-05-16 10:15:22 ERROR [production-api] ConnectionTimeout: Failed to connect to database-master";
        }
    }

    public static class OpsTools {
        @Tool("Restarts a Kubernetes deployment")
        public String restartDeployment(@P("The deployment name") String name) {
            return "✅ [DRY-RUN] kubectl rollout restart deployment/" + name + " initiated.";
        }

        @Tool("Gets status of Kubernetes pods")
        public String getPodStatus(String namespace) {
            return "production-api-xyz-123   0/1   Pending   0   2m (Reason: NodePressure)";
        }
    }
}

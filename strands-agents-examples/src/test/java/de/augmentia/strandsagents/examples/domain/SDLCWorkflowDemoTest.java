package de.augmentia.strandsagents.examples.domain;

import static org.assertj.core.api.Assertions.assertThat;

import de.augmentia.strandsagents.core.MockStreamingChatModel;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class SDLCWorkflowDemoTest {

    @Test
    void testWorkflowRunsSuccessfullyToCompletion() throws Exception {
        Path sessionFile = Path.of(".sessions/session-reppit-test-001.json");
        Files.deleteIfExists(sessionFile);

        // 5 approvals are required: RESEARCH -> PROPOSAL -> PLAN -> IMPLEMENT -> TEST -> COMPLETED
        String simulatedInput = "1\n1\n1\n1\n1\n";
        InputStream originalSystemIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));

        try {
            var streamingModel = new MockStreamingChatModel("Mock response for: %s");
            SDLCWorkflowDemo workflow = new SDLCWorkflowDemo(
                    streamingModel,
                    null,
                    "session-reppit-test-001"
            );

            workflow.runWorkflow("Create a hello world java file.");

            // Verify session file is created and has state metadata indicating it was completed
            assertThat(sessionFile).exists();
            String sessionContent = Files.readString(sessionFile);
            assertThat(sessionContent).contains("\"currentPhase\":\"COMPLETED\"");
        } finally {
            System.setIn(originalSystemIn);
            Files.deleteIfExists(sessionFile);
        }
    }

    @Test
    void testWorkflowHandlesReworkAndThenCompletes() throws Exception {
        Path sessionFile = Path.of(".sessions/session-reppit-test-002.json");
        Files.deleteIfExists(sessionFile);

        // Simulated user inputs:
        // 1. RESEARCH: "2\nneed more detail\n" (rework)
        // 2. RESEARCH: "1\n" (approve)
        // 3. PROPOSAL: "1\n" (approve)
        // 4. PLAN: "1\n" (approve)
        // 5. IMPLEMENT: "1\n" (approve)
        // 6. TEST: "1\n" (approve)
        String simulatedInput = "2\nneed more detail\n1\n1\n1\n1\n1\n";
        InputStream originalSystemIn = System.in;
        System.setIn(new ByteArrayInputStream(simulatedInput.getBytes()));

        try {
            var streamingModel = new MockStreamingChatModel("Mock response for: %s");
            SDLCWorkflowDemo workflow = new SDLCWorkflowDemo(
                    streamingModel,
                    null,
                    "session-reppit-test-002"
            );

            workflow.runWorkflow("Create a hello world java file.");

            assertThat(sessionFile).exists();
            String sessionContent = Files.readString(sessionFile);
            assertThat(sessionContent).contains("\"currentPhase\":\"COMPLETED\"");
        } finally {
            System.setIn(originalSystemIn);
            Files.deleteIfExists(sessionFile);
        }
    }
}

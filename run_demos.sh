#!/usr/bin/env bash
# run_demos.sh – Execute all demos in strands-agents-examples
# Usage: ./run_demos.sh [demo-class-name]
# If no argument, runs all demos with mock model

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Source API keys for LLM access
source set_keys.sh 2>/dev/null || true

# Build the project first
echo "Building project..."
mvn compile -q -DskipTests 2>/dev/null

# Function to run a demo with exec:java
run_demo() {
    local main_class=$1
    echo ""
    echo "=========================================="
    echo "Running: $main_class"
    echo "=========================================="
    mvn -q exec:java -pl strands-agents-examples -Dexec.mainClass="$main_class" 2>&1 || true
}

# Main classes to run
DEMO_CLASSES=(
    "de.augmentia.strandsagents.examples.feature.MainMock"
    "de.augmentia.strandsagents.examples.feature.WorkflowDemo"
    "de.augmentia.strandsagents.examples.SwarmDemo"
    "de.augmentia.strandsagents.examples.domain.LibraryAgentDemo"
    "de.augmentia.strandsagents.examples.domain.MultiAgentEvaluationDemo"
    "de.augmentia.strandsagents.examples.domain.InvestmentAnalysisDemo"
    "de.augmentia.strandsagents.examples.StructuredOutputDemo"
)

if [ -n "$1" ]; then
    # Run specific demo
    run_demo "$1"
else
    # Run all demos (only MainMock uses mock, others need OPENAI_API_KEY)
    run_demo "de.augmentia.strandsagents.examples.feature.MainMock"
fi
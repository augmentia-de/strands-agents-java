#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- API-Keys laden ----
if [ -f "$SCRIPT_DIR/set_keys.sh" ]; then
    source "$SCRIPT_DIR/set_keys.sh"
fi

# ---- Config (Überschreibbar via env) ----
export STRANDS_SKILLS_DIR="${STRANDS_SKILLS_DIR:-$SCRIPT_DIR/skills}"
export STRANDS_SESSION_DIR="${STRANDS_SESSION_DIR:-$SCRIPT_DIR/.sessions}"
export STRANDS_LLM_LOG_ENABLED="${STRANDS_LLM_LOG_ENABLED:-true}"
export STRANDS_LLM_LOG_PATH="${STRANDS_LLM_LOG_PATH:-$SCRIPT_DIR/logs/llm-calls.log}"

echo ">>> Starte Main (OpenAI-kompatibel)"
echo "    Model:       ${LLM_CHAT_MODEL:-gpt-4o-mini}"
echo "    Skills:      $STRANDS_SKILLS_DIR"
echo "    Sessions:    $STRANDS_SESSION_DIR"
echo ""

cd "$SCRIPT_DIR"
mvn -q install -DskipTests
mvn -q -pl strands-agents-examples \
    exec:exec \
    -Dexec.executable="java" \
    -Dexec.args="--enable-preview -cp %classpath de.augmentia.strands-agents.examples.Main"

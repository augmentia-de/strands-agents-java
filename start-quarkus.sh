#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ---- Config (env/Modell, aber NICHT den API-Key) ----
if [ -f "$SCRIPT_DIR/set_keys.sh" ]; then
    source "$SCRIPT_DIR/set_keys.sh"
    # API-Key nur aus PBE – env-Variable entfernen
    unset OPENAI_API_KEY
fi

# ---- PBE-Key-Vault (wie native Docker) ----
export JSTRANDS_KEY_PATH="${JSTRANDS_KEY_PATH:-$SCRIPT_DIR/config/api-key.enc}"

# ---- Config (Überschreibbar via env) ----
export STRANDS_SKILLS_DIR="${STRANDS_SKILLS_DIR:-$SCRIPT_DIR/skills}"
export STRANDS_SESSION_DIR="${STRANDS_SESSION_DIR:-$SCRIPT_DIR/.sessions}"
export STRANDS_LLM_LOG_ENABLED="${STRANDS_LLM_LOG_ENABLED:-true}"
export STRANDS_LLM_LOG_PATH="${STRANDS_LLM_LOG_PATH:-$SCRIPT_DIR/logs/llm-calls.log}"
export STRANDS_AGENT_TOOLS="${STRANDS_AGENT_TOOLS:-de.augmentia.strands-agents.core.tools.CalculatorTool}"

usage() {
    cat <<EOF
Usage: ./start-quarkus.sh [mode]

Modes:
  dev          Quarkus Dev Mode (Hot Reload) – Standard
  prod         Produktions-Modus (build + jar)
  build-only   Nur bauen, nicht starten
  help         Diese Hilfe

EOF
}
cmd_dev() {
    echo ">>> Quarkus Dev Mode starten (Hot Reload)"
    echo "    Skills:      $STRANDS_SKILLS_DIR"
    echo "    Sessions:    $STRANDS_SESSION_DIR"
    echo "    LLM-Log:     $STRANDS_LLM_LOG_ENABLED"
    echo "    Model:       ${OPENAI_MODEL:-gpt-4o-mini}"
    echo "    PBE-Vault:   $JSTRANDS_KEY_PATH"
    echo ""
    echo "   ⚠ API-Key wird nur aus PBE-Vault geladen (kein env)."
    echo "     → http://localhost:8082/keys  (Key Vault)"
    echo ""
    cd "$SCRIPT_DIR"
    QUARKUS_VERSION=$(grep -oP '<quarkus\.platform\.version>\K[^<]+' strands-agents-quarkus/pom.xml)
    mvn "io.quarkus:quarkus-maven-plugin:${QUARKUS_VERSION}:dev" \
        -pl strands-agents-quarkus -am
}

cmd_prod() {
    echo ">>> Produktions-Build + Start"
    echo "    PBE-Vault:   $JSTRANDS_KEY_PATH"
    echo ""
    echo "   ⚠ API-Key wird nur aus PBE-Vault geladen (kein env)."
    echo "     → http://localhost:8082/keys  (Key Vault)"
    echo ""
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests -pl strands-agents-quarkus -am
    java --enable-preview \
        -jar strands-agents-quarkus/target/quarkus-app/quarkus-run.jar
}

cmd_build_only() {
    echo ">>> Nur bauen (production jar)"
    cd "$SCRIPT_DIR"
    mvn clean package -DskipTests -pl strands-agents-quarkus -am
    echo ""
    echo "✅ Jar liegt in strands-agents-quarkus/target/quarkus-app/"
}

case "${1:-dev}" in
    dev)        cmd_dev ;;
    prod)       cmd_prod ;;
    build-only) cmd_build_only ;;
    help|--help|-h) usage ;;
    *)
        echo "Unbekannter Modus: $1"
        usage
        exit 1
        ;;
esac

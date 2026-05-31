#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# ---- API-Keys aus set_keys.sh laden ----
if [ -f "$PROJECT_DIR/set_keys.sh" ]; then
    source "$PROJECT_DIR/set_keys.sh"
fi

# ---- Prüfen ----
if [ -z "${OPENAI_API_KEY:-}" ]; then
    echo "Fehler: OPENAI_API_KEY ist nicht gesetzt."
    echo "  Trage deinen Key in set_keys.sh ein oder exportiere die Variable."
    exit 1
fi

# ---- Image bauen ----
echo ">>> Docker-Image bauen (JVM) ..."
docker build -f "$PROJECT_DIR/strands-agents-quarkus/src/main/docker/Dockerfile.jvm" -t strands-agent:latest "$PROJECT_DIR"

# ---- Sicherstellen, dass die data/- und skills/-Verzeichnisse existieren ----
mkdir -p "$PROJECT_DIR/data"

# ---- Container starten ----
echo ">>> Container starten ..."
docker run -d \
    --name strands-agent \
    --restart unless-stopped \
    --add-host=host.docker.internal:host-gateway \
    -p 8084:8084 \
    -e OPENAI_API_KEY="${OPENAI_API_KEY}" \
    -e OPENAI_BASE_URL="${OPENAI_BASE_URL:-}" \
    -e OPENAI_MODEL="${OPENAI_MODEL:-gpt-4o}" \
    -e QUARKUS_HTTP_PORT=8084 \
    -e STRANDS_SKILLS_DIR=/app/skills \
    -e STRANDS_AGENT_TOOLS=de.augmentia.strandsagents.core.tools.CalculatorTool \
    -v "$PROJECT_DIR/data:/app/data" \
    -v "$PROJECT_DIR/skills:/app/skills" \
    -v "$PROJECT_DIR/config/MCP_SERVER_CONFIG.json:/app/config/MCP_SERVER_CONFIG.json:ro" \
    strands-agent:latest

echo ""
echo "✅ strands-agent läuft auf http://localhost:8084"
echo ""
echo "   Stop:  docker stop strands-agent"
echo "   Logs:  docker logs -f strands-agent"
echo "   Shell: docker exec -it strands-agent sh"

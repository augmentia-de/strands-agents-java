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
echo ">>> Docker-Image bauen ..."
docker build -f "$PROJECT_DIR/docker/Dockerfile" -t strands-agent:latest "$PROJECT_DIR"

# ---- Sicherstellen, dass das data/-Verzeichnis existiert ----
mkdir -p "$PROJECT_DIR/data"

# ---- Container starten ----
echo ">>> Container starten ..."
docker run -d \
    --name strands-agent \
    --restart unless-stopped \
    -p 8080:8080 \
    -e OPENAI_API_KEY="${OPENAI_API_KEY}" \
    -e OPENAI_BASE_URL="${OPENAI_BASE_URL:-}" \
    -e LLM_CHAT_MODEL="${LLM_CHAT_MODEL:-gpt-4o}" \
    -e LLM_TEMPERATURE="${LLM_TEMPERATURE:-0.7}" \
    -e LLM_MAX_RETRIES="${LLM_MAX_RETRIES:-3}" \
    -v "$PROJECT_DIR/data:/app/data" \
    strands-agent:latest

echo ""
echo "✅ strands-agent läuft auf http://localhost:8080"
echo ""
echo "   Stop:  docker stop strands-agent"
echo "   Logs:  docker logs -f strands-agent"
echo "   Shell: docker exec -it strands-agent sh"

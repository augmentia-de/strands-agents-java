#!/usr/bin/env bash
# ============================================================
# deploy.sh — Lokales Docker-Compose-Deployment
# ============================================================
# Usage:
#   ./deploy/local/deploy.sh                    # JVM (default)
#   ./deploy/local/deploy.sh --native           # Native Image
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

MODE="jvm"

case "${1:-}" in
    --native) MODE="native"; shift ;;
    --help|-h)
        echo "Usage: ./deploy/local/deploy.sh [--native]"
        exit 0
        ;;
esac

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }

# ---- Check Image ----
if [ "$MODE" = "native" ]; then
    IMAGE="strands-agents-quarkus:latest"
    COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.native.yml"
else
    IMAGE="strands-agent:latest"
    COMPOSE_FILE="$PROJECT_ROOT/docker/docker-compose.yml"
fi

if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    echo "Error: Image '$IMAGE' not found."
    echo "Please run ./scripts/build.sh [--native] first."
    exit 1
fi

# ---- Prepare .env ----
if [ ! -f "$SCRIPT_DIR/.env" ]; then
    cat > "$SCRIPT_DIR/.env" <<EOF
OPENAI_API_KEY=your_key_here
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_MODEL=openai/gpt-oss-120b:free
EOF
    echo "Created .env template — please edit $SCRIPT_DIR/.env and add your API key."
fi

# ---- Ensure directories ----
mkdir -p "$SCRIPT_DIR/data" "$SCRIPT_DIR/.sessions" "$SCRIPT_DIR/logs"
chmod 777 "$SCRIPT_DIR/.sessions" "$SCRIPT_DIR/logs" 2>/dev/null || true

# ---- Start ----
info "Starting containers ($MODE) …"
cd "$SCRIPT_DIR" && docker compose -f "$COMPOSE_FILE" up -d

ok "Containers started"
cd "$SCRIPT_DIR" && docker compose -f "$COMPOSE_FILE" ps

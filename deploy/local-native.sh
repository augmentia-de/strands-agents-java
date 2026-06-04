#!/usr/bin/env bash
# ============================================================
# local-native.sh  –  Native Docker lokal bauen & starten
# ============================================================
# Usage:
#   ./deploy/local-native.sh                   # Bauen + Starten
#   ./deploy/local-native.sh --no-build        # Nur Starten
#   ./deploy/local-native.sh --tag mytag       # Mit eigenem Tag
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

TAG="${TAG:-latest}"
IMAGE="strands-agents:$TAG"
CONTAINER="strands-agent"
BUILD=true
PORT="${PORT:-8082}"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-build) BUILD=false; shift ;;
        --tag)      TAG="$2"; IMAGE="strands-agents:$TAG"; shift 2 ;;
        --port)     PORT="$2"; shift 2 ;;
        *)          echo "Unbekannt: $1"; exit 1 ;;
    esac
done

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

cd "$PROJECT_ROOT"

if $BUILD; then
    info "Baue Native-Image …"
    docker build -f strands-agents-quarkus/src/main/docker/Dockerfile.native \
        -t "$IMAGE" .
    ok "Image '$IMAGE' erstellt"
fi

info "Stoppe alten Container …"
docker rm -f "$CONTAINER" 2>/dev/null || true

info "Starte Container auf Port $PORT …"
docker run -d \
    --name "$CONTAINER" \
    -p "$PORT:8082" \
    -e JSTRANDS_KEY_PATH=/work/api-key.enc \
    -v "$PROJECT_ROOT/config:/work/config:ro" \
    "$IMAGE"

sleep 2

if docker ps --format '{{.Names}}' | grep -q "^$CONTAINER$"; then
    ok "Container '$CONTAINER' läuft auf http://localhost:$PORT"
    echo ""
    echo "  Status:   curl -s http://localhost:$PORT/api/admin/status"
    echo "  Setup:    curl -s -X POST http://localhost:$PORT/api/admin/setup \\"
    echo "              -H 'Content-Type: application/json' \\"
    echo '              -d '"'"'{"apiKey":"sk-...","password":"meinpass"}'"'"''
    echo "  Aktiv:    curl -s -X POST http://localhost:$PORT/api/admin/activate \\"
    echo "              -H 'Content-Type: application/json' \\"
    echo '              -d '"'"'{"password":"meinpass"}'"'"''
    echo "  Logs:     docker logs -f $CONTAINER"
    echo "  Stop:     docker rm -f $CONTAINER"
    echo ""
    docker logs "$CONTAINER" 2>&1 | grep -E "(started|listening|error)" | tail -3 || true
else
    err "Container gestartet, läuft aber nicht!"
    docker logs "$CONTAINER" 2>&1 | tail -10
    exit 1
fi

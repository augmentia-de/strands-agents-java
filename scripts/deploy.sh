#!/usr/bin/env bash
# ============================================================
# deploy.sh — Unified deployment (JVM, Native, GCP)
# ============================================================
# Usage:
#   ./scripts/deploy.sh                       # JVM local (default)
#   ./scripts/deploy.sh --jvm                 # JVM local
#   ./scripts/deploy.sh --native              # Native local
#   ./scripts/deploy.sh --gcp                 # Native → GCP VM
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODE="jvm"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jvm)      MODE="jvm"; shift ;;
        --native)   MODE="native"; shift ;;
        --gcp)      MODE="gcp"; shift ;;
        --help|-h)
            sed -n '2,/^set -e/p' "$0" | head -n -1 | sed 's/^# //; s/^#$//'
            exit 0
            ;;
        *)          echo "Unbekannt: $1 (--help für Hilfe)"; exit 1 ;;
    esac
done

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

# ---- .env template ----
ensure_env() {
    local target="${1:-$PROJECT_ROOT/.env}"
    if [ ! -f "$target" ]; then
        cat > "$target" <<EOF
# API-Key only from PBE vault (http://localhost:8082/keys)
OPENAI_API_KEY=
OPENAI_BASE_URL=https://openrouter.ai/api/v1
OPENAI_MODEL=openai/gpt-oss-120b:free
EOF
        info ".env template created at $target — API key will be loaded from PBE vault"
    fi
}

# ---- Local: docker compose ----
cmd_local() {
    local mode_label="$1"     # "jvm" | "native"
    local compose_file
    local image

    if [ "$mode_label" = "native" ]; then
        compose_file="$PROJECT_ROOT/docker/docker-compose.native.yml"
        image="strands-agent:latest"
    else
        compose_file="$PROJECT_ROOT/docker/docker-compose.yml"
        image="strands-agent:latest"
    fi

    if ! docker image inspect "$image" >/dev/null 2>&1; then
        err "Image '$image' not found — run ./scripts/build.sh${mode_label: + --$mode_label} first"
        exit 1
    fi

    ensure_env "$PROJECT_ROOT/.env"

    info "Preparing directories …"
    mkdir -p "$PROJECT_ROOT/config" "$PROJECT_ROOT/docker/data" "$PROJECT_ROOT/.sessions" "$PROJECT_ROOT/logs" "$PROJECT_ROOT/workspace"
    # Container user (UID 1001 in native mode) needs write access to config
    chmod 777 "$PROJECT_ROOT/config" "$PROJECT_ROOT/.sessions" "$PROJECT_ROOT/logs" 2>/dev/null || true

    info "Starting containers ($mode_label) …"
    cd "$PROJECT_ROOT/docker" && docker compose -f "$compose_file" up -d
    ok "Containers started"
    docker compose -f "$compose_file" ps
}

# ---- GCP VM ----
cmd_gcp() {
    local image="strands-agent:latest"

    if ! docker image inspect "$image" >/dev/null 2>&1; then
        err "Image '$image' not found — run ./scripts/build.sh --native first"
        exit 1
    fi

    local vm_name="strands-agent-vm"
    local zone="us-central1-a"

    if ! gcloud compute instances describe "$vm_name" --zone="$zone" >/dev/null 2>&1; then
        err "VM '$vm_name' does not exist — run deploy/gcp-vm/deploy-vm.sh first"
        exit 1
    fi

    ensure_env "$PROJECT_ROOT/.env"
    cd "$PROJECT_ROOT/deploy/gcp-vm"

    info "Syncing files to VM …"
    gcloud compute ssh "$vm_name" --zone="$zone" --command='
        if [ -d ~/deploy-strands/config ]; then
            sudo chown -R $USER:$USER ~/deploy-strands/config
        fi
    '
    gcloud compute ssh "$vm_name" --zone="$zone" --command='mkdir -p ~/deploy-strands/config'

    gcloud compute scp --recurse \
        docker-compose.yml \
        "$PROJECT_ROOT/.env" \
        "$vm_name":~/deploy-strands/ --zone="$zone"

    gcloud compute scp \
        MCP_SERVER_CONFIG.json \
        "$PROJECT_ROOT/config/application.properties" \
        "$vm_name":~/deploy-strands/config/ --zone="$zone"

    info "Transferring native image (this may take a while) …"
    docker save "$image" | gzip | \
        gcloud compute ssh "$vm_name" --zone="$zone" --command 'sudo gunzip | sudo docker load'

    info "Restarting containers …"
    gcloud compute ssh "$vm_name" --zone="$zone" --command="
        cd ~/deploy-strands && \
        mkdir -p shared_data/workspace .sessions logs && \
        sudo chown -R 1001:1001 shared_data config .sessions logs && \
        sudo docker compose down && \
        sudo docker compose up -d
    "

    local external_ip
    external_ip=$(gcloud compute instances describe "$vm_name" --zone="$zone" \
        --format='get(networkInterfaces[0].accessConfigs[0].natIP)')

    ok "Redeployment finished"
    echo "  Strands Agent: http://$external_ip:8082"
    echo "  MCP Filesystem: http://$external_ip:3000"
    echo
    gcloud compute ssh "$vm_name" --zone="$zone" \
        --command='sudo docker compose logs strands-agent --tail=10'
}

# ---- Main ----
cd "$PROJECT_ROOT"

case "$MODE" in
    jvm)    cmd_local "jvm" ;;
    native) cmd_local "native" ;;
    gcp)    cmd_gcp ;;
esac

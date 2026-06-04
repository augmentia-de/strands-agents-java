#!/usr/bin/env bash
# ============================================================
# deploy-gcp.sh — Google Cloud Run (Serverless Container)
# ============================================================
# Baut Native-Image und deployt auf Cloud Run.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Zentrale Keys einbinden
[[ -f "$PROJECT_ROOT/set_keys.sh" ]] && source "$PROJECT_ROOT/set_keys.sh"

# ── Konfiguration ──────────────────────────────────────────
PROJECT_ID="${PROJECT_ID:-${GCP_PROJECT_ID:-CHANGEME}}"
REGION="${REGION:-${GCP_REGION:-europe-west1}}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICE_NAME="${SERVICE_NAME:-strands-agent}"
MEMORY="${MEMORY:-512Mi}"
CPU="${CPU:-1}"
MIN_INSTANCES="${MIN_INSTANCES:-0}"
MAX_INSTANCES="${MAX_INSTANCES:-10}"

IMAGE="gcr.io/$PROJECT_ID/$SERVICE_NAME:$IMAGE_TAG"

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

check_gcloud() {
    if ! command -v gcloud &>/dev/null; then err "gcloud nicht installiert."; exit 1; fi
    if ! gcloud auth print-access-token &>/dev/null; then
        err "Keine GCP-Credentials gefunden."
        err "Einrichten mit: gcloud auth login"
        exit 1
    fi
    gcloud config set project "$PROJECT_ID" --quiet &>/dev/null
    ok "GCP authentifiziert (Projekt: $PROJECT_ID)"
}

build_and_push() {
    info "Baue Native-Image …"
    docker build -f "$PROJECT_ROOT/strands-agents-quarkus/src/main/docker/Dockerfile.native" \
        -t "$IMAGE" "$PROJECT_ROOT"

    info "Push nach gcr.io …"
    docker push "$IMAGE"
    ok "Image gepusht: $IMAGE"
}

deploy_cloud_run() {
    info "Deploye auf Cloud Run …"

    gcloud run deploy "$SERVICE_NAME" \
        --image "$IMAGE" \
        --region "$REGION" \
        --memory "$MEMORY" \
        --cpu "$CPU" \
        --min-instances "$MIN_INSTANCES" \
        --max-instances "$MAX_INSTANCES" \
        --allow-unauthenticated \
        --set-env-vars "VAULT_ADDR=${VAULT_ADDR:-}" \
        --set-env-vars "OPENAI_BASE_URL=${OPENAI_BASE_URL:-}" \
        --set-env-vars "OPENAI_API_KEY=${OPENAI_API_KEY:-}" \
        --set-env-vars "OPENAI_MODEL=${OPENAI_MODEL:-}" \
        --port=8082 \
        --timeout="5m" \
        --quiet

    local url
    url=$(gcloud run services describe "$SERVICE_NAME" \
        --region "$REGION" --format='value(status.url)')
    ok "Cloud Run deployed: $url"
}

# ── Main ────────────────────────────────────────────────────
check_gcloud

if [[ "$PROJECT_ID" == "CHANGEME" ]]; then
    err "GCP_PROJECT_ID in set_keys.sh nicht gesetzt!"
    exit 1
fi

gcloud services enable run.googleapis.com containerregistry.googleapis.com --quiet

build_and_push
deploy_cloud_run

echo ""
ok "GCP-Cloud-Run-Deployment abgeschlossen!"

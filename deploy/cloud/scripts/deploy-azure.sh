#!/usr/bin/env bash
# ============================================================
# deploy-azure.sh — Azure Container Apps (Serverless Container)
# ============================================================
# Baut Native-Image und deployt auf Azure Container Apps.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Zentrale Keys einbinden
[[ -f "$PROJECT_ROOT/set_keys.sh" ]] && source "$PROJECT_ROOT/set_keys.sh"

# ── Konfiguration ──────────────────────────────────────────
RESOURCE_GROUP="${RESOURCE_GROUP:-${AZURE_RESOURCE_GROUP:-strands-agent-rg}}"
LOCATION="${LOCATION:-${AZURE_LOCATION:-westeurope}}"
SUBSCRIPTION_ID="${SUBSCRIPTION_ID:-${AZURE_SUBSCRIPTION_ID:-CHANGEME}}"
ACR_NAME="${ACR_NAME:-${AZURE_ACR_NAME:-CHANGEME}}"
CONTAINER_APP="${CONTAINER_APP:-${AZURE_CONTAINER_APP:-strands-agent}}"
IMAGE_TAG="${IMAGE_TAG:-latest}"

ACR_LOGIN_SERVER="$ACR_NAME.azurecr.io"
IMAGE="$ACR_LOGIN_SERVER/$CONTAINER_APP:$IMAGE_TAG"

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

check_az() {
    if ! command -v az &>/dev/null; then err "az CLI nicht installiert."; exit 1; fi
    if ! az account show &>/dev/null; then
        err "Keine Azure-Credentials gefunden."
        err "Einrichten mit: az login"
        exit 1
    fi
    ok "Azure authentifiziert"
}

ensure_rg() {
    if ! az group show --name "$RESOURCE_GROUP" --subscription "$SUBSCRIPTION_ID" &>/dev/null; then
        info "Erstelle Resource Group '$RESOURCE_GROUP' …"
        az group create --name "$RESOURCE_GROUP" --location "$LOCATION" \
            --subscription "$SUBSCRIPTION_ID" &>/dev/null
        ok "Resource Group erstellt"
    else
        ok "Resource Group '$RESOURCE_GROUP' existiert"
    fi
}

ensure_acr() {
    if ! az acr show --name "$ACR_NAME" --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        info "Erstelle ACR '$ACR_NAME' …"
        az acr create --name "$ACR_NAME" --resource-group "$RESOURCE_GROUP" --sku Basic &>/dev/null
        ok "ACR erstellt"
    else
        ok "ACR '$ACR_NAME' existiert"
    fi
}

build_and_push() {
    info "Login bei ACR …"
    az acr login --name "$ACR_NAME" &>/dev/null

    info "Baue Native-Image …"
    docker build -f "$PROJECT_ROOT/strands-agents-quarkus/src/main/docker/Dockerfile.native" \
        -t "$IMAGE" "$PROJECT_ROOT"

    info "Push nach ACR …"
    docker push "$IMAGE"
    ok "Image gepusht: $IMAGE"
}

ensure_env() {
    local env_name="${CONTAINER_APP}-env"
    if ! az containerapp env show --name "$env_name" \
        --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        info "Erstelle Container Apps Environment '$env_name' …"
        az containerapp env create \
            --name "$env_name" \
            --resource-group "$RESOURCE_GROUP" \
            --location "$LOCATION" &>/dev/null
        ok "Environment erstellt"
    else
        ok "Environment '$env_name' existiert"
    fi
    echo "$env_name"
}

deploy_container_app() {
    local env_name="$1"

    if az containerapp show --name "$CONTAINER_APP" \
        --resource-group "$RESOURCE_GROUP" &>/dev/null; then
        info "Aktualisiere Container App '$CONTAINER_APP' …"
        az containerapp update \
            --name "$CONTAINER_APP" \
            --resource-group "$RESOURCE_GROUP" \
            --image "$IMAGE" &>/dev/null
        ok "Container App aktualisiert"
    else
        info "Erstelle Container App '$CONTAINER_APP' …"
        az containerapp create \
            --name "$CONTAINER_APP" \
            --resource-group "$RESOURCE_GROUP" \
            --environment "$env_name" \
            --image "$IMAGE" \
            --registry-server "$ACR_LOGIN_SERVER" \
            --target-port 8082 \
            --ingress external \
            --min-replicas 0 \
            --max-replicas 10 \
            --memory "0.5Gi" \
            --cpu "0.5" \
            --secrets "vault-addr=${VAULT_ADDR:-}" "openai-api-key=${OPENAI_API_KEY:-}" \
            --env-vars "VAULT_ADDR=secretref:vault-addr" \
                "OPENAI_API_KEY=secretref:openai-api-key" \
                "OPENAI_BASE_URL=${OPENAI_BASE_URL:-}" \
                "OPENAI_MODEL=${OPENAI_MODEL:-}" &>/dev/null
        ok "Container App erstellt"
    fi

    local url
    url=$(az containerapp show --name "$CONTAINER_APP" \
        --resource-group "$RESOURCE_GROUP" \
        --query 'properties.configuration.ingress.fqdn' --output text)
    ok "Container App URL: https://$url"
}

# ── Main ────────────────────────────────────────────────────
check_az

if [[ "$ACR_NAME" == "CHANGEME" ]]; then
    err "AZURE_ACR_NAME in set_keys.sh nicht gesetzt!"
    exit 1
fi

ensure_rg
ensure_acr
build_and_push
env_name=$(ensure_env)
deploy_container_app "$env_name"

echo ""
ok "Azure-Container-Apps-Deployment abgeschlossen!"

#!/usr/bin/env bash
# ============================================================
# set_keys.sh — Zentrale Konfigurationsdatei für alle Skripte
# ============================================================
# Einbinden mit:  source ./set_keys.sh
#
# ALLE Platzhalter (CHANGEME) ersetzen, dann deployen.
#
# Zusätzlich benötigt jede Cloud Credentials:
#   AWS:  aws configure  oder  AWS_ACCESS_KEY_ID + AWS_SECRET_ACCESS_KEY
#   GCP:  gcloud auth login  oder  GOOGLE_APPLICATION_CREDENTIALS=/pfad/key.json
#   Azure: az login  oder  AZURE_CLIENT_ID + AZURE_TENANT_ID + AZURE_CLIENT_SECRET
# ============================================================

# ── Vault (Remote HashiCorp) ────────────────────────────────
export VAULT_ADDR="${VAULT_ADDR:-http:// ---}"  # Remote Vault URL (GCP)
export VAULT_TOKEN="${VAULT_TOKEN:-}"                                      # App-Token (optional, z.B. für initVault.sh)

# ── LangChain4j / OpenAI ───────────────────────────────────
export OPENAI_BASE_URL="${OPENAI_BASE_URL:-http://localhost:11434/v1}"
export OPENAI_API_KEY="${OPENAI_API_KEY:-demo}"
export OPENAI_MODEL="${OPENAI_MODEL:-openai/gpt-oss-120b:free}"

# ── Docker-Image ────────────────────────────────────────────
export IMAGE_TAG="${IMAGE_TAG:-latest}"
export BUILD_MODE="${BUILD_MODE:-jvm}"                      # jvm | native

# ── AWS Lambda / Container ─────────────────────────────────
export AWS_REGION="${AWS_REGION:-eu-central-1}"
export AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-000000000000}"          # 12 Ziffern, keine Bindestriche!
export AWS_ECR_REPO="${AWS_ECR_REPO:-cloud/cloud-quarkus}"
export LAMBDA_FUNCTION_NAME="${LAMBDA_FUNCTION_NAME:-cloud-quarkus}"
export LAMBDA_MEMORY="${LAMBDA_MEMORY:-512}"
export LAMBDA_TIMEOUT="${LAMBDA_TIMEOUT:-30}"

# ── GCP Cloud Run ──────────────────────────────────────────
export GCP_PROJECT_ID="${GCP_PROJECT_ID:-CHANGEME}"
export GCP_REGION="${GCP_REGION:-europe-west1}"
export GCP_IMAGE_REGISTRY="${GCP_IMAGE_REGISTRY:-gcr.io}"

# ── Azure Container Apps ───────────────────────────────────
export AZURE_RESOURCE_GROUP="${AZURE_RESOURCE_GROUP:-cloud-quarkus-rg}"
export AZURE_LOCATION="${AZURE_LOCATION:-westeurope}"
export AZURE_SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID}"
export AZURE_ACR_NAME="${AZURE_ACR_NAME}"
export AZURE_CONTAINER_APP="${AZURE_CONTAINER_APP:-cloud-quarkus}"

# ── Kubernetes / Helm (optional) ───────────────────────────
export K8S_NAMESPACE="${K8S_NAMESPACE:-default}"
export K8S_INGRESS_HOST="${K8S_INGRESS_HOST:-cloud-quarkus.example.com}"
export HELM_RELEASE="${HELM_RELEASE:-cloud-quarkus}"

# ── Prüfung ─────────────────────────────────────────────────
check_keys() {
    local missing=0
    for var in "$@"; do
        local val="${!var:-}"
        if [[ "$val" == "CHANGEME" || -z "$val" ]]; then
            echo -e "\033[0;31m[FEHLT]\033[0m $var ist nicht gesetzt (CHANGEME)"
            missing=1
        fi
    done
    return $missing
}

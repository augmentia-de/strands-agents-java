#!/usr/bin/env bash
# ============================================================
# deploy-helm.sh — Helm Chart Deployment
# ============================================================
# Voraussetzungen: helm, kubectl, Docker-Image in Registry
# ============================================================
set -euo pipefail

# Zentrale Keys einbinden (falls vorhanden)
[[ -f "$(dirname "$0")/../../../set_keys.sh" ]] && source "$(dirname "$0")/../../../set_keys.sh"

# ── Konfiguration ──────────────────────────────────────────
NAMESPACE="${NAMESPACE:-strands}"
IMAGE_REPO="${IMAGE_REPO:-strands-agent}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
INGRESS_HOST="${INGRESS_HOST:-strands-agent.example.com}"
HELM_RELEASE="${HELM_RELEASE:-strands-agent}"

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

check_helm() {
    if ! command -v helm &>/dev/null; then err "helm nicht installiert."; exit 1; fi
    ok "helm gefunden"
}

deploy() {
    local chart_dir="$(dirname "$0")/../../helm/strands-agent"

    helm upgrade --install "$HELM_RELEASE" "$chart_dir" \
        --namespace "$NAMESPACE" --create-namespace \
        --set image.repository="$IMAGE_REPO" \
        --set image.tag="$IMAGE_TAG" \
        --set ingress.hosts[0].host="$INGRESS_HOST" \
        --wait --timeout 5m

    ok "Helm-Deployment abgeschlossen"
}

check_helm
deploy

#!/usr/bin/env bash
# ============================================================
# deploy-k8s.sh — Kubernetes Raw Deployment
# ============================================================
# Voraussetzungen: kubectl, Docker-Image bereits in Registry
# ============================================================
set -euo pipefail

# Zentrale Keys einbinden (falls vorhanden)
[[ -f "$(dirname "$0")/../scripts/set_keys.sh" ]] && source "$(dirname "$0")/../scripts/set_keys.sh"

# ── Konfiguration ──────────────────────────────────────────
NAMESPACE="${NAMESPACE:-strands}"
IMAGE="${IMAGE:-strands-agent:latest}"
INGRESS_HOST="${INGRESS_HOST:-strands-agent.example.com}"

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

check_kubectl() {
    if ! command -v kubectl &>/dev/null; then err "kubectl nicht installiert."; exit 1; fi
    if ! kubectl cluster-info &>/dev/null; then err "Kein K8s-Cluster erreichbar."; exit 1; fi
    ok "kubectl verbunden"
}

deploy() {
    local k8s_dir="$(dirname "$0")/k8s"

    # Namespace anlegen
    kubectl create ns "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -

    # ConfigMap + Secret aus deploy/k8s/ übernehmen
    kubectl apply -n "$NAMESPACE" -f "$k8s_dir/configmap.yaml"

    # Image-Platzhalter im Deployment ersetzen
    sed "s|image:.*|image: $IMAGE|g" "$k8s_dir/deployment.yaml" \
        | kubectl apply -n "$NAMESPACE" -f -

    kubectl apply -n "$NAMESPACE" -f "$k8s_dir/service.yaml"
    kubectl apply -n "$NAMESPACE" -f "$k8s_dir/hpa.yaml"

    # Optional: Ingress
    if [[ -f "$k8s_dir/ingress.yaml" ]]; then
        sed "s|strands-agent.example.com|$INGRESS_HOST|g" "$k8s_dir/ingress.yaml" \
            | kubectl apply -n "$NAMESPACE" -f -
    fi

    # Optional: ServiceMonitor
    if [[ -f "$k8s_dir/service-monitor.yaml" ]]; then
        kubectl apply -n "$NAMESPACE" -f "$k8s_dir/service-monitor.yaml"
    fi

    kubectl rollout status deployment/strands-agent -n "$NAMESPACE" --timeout=120s
    ok "Deployment abgeschlossen"
}

check_kubectl
deploy

#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(realpath "$(dirname "${BASH_SOURCE[0]}")/../..")"
CLUSTER_NAME="${1:-strands-agent}"

echo "================================"
echo " Strands Agent – kind Deploy"
echo "================================"

# ---- 1. kind Cluster erstellen ----
echo ""
echo ">>> [1/6] kind Cluster '${CLUSTER_NAME}' erstellen ..."
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "  Cluster existiert bereits."
else
    cat <<EOF | kind create cluster --name "${CLUSTER_NAME}" --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
    extraPortMappings:
      - containerPort: 30080
        hostPort: 8088
EOF
fi

# ---- 2. API-Key laden ----
echo ""
echo ">>> [2/6] API-Key aus set_keys.sh laden ..."
if [ -f "${SCRIPT_DIR}/set_keys.sh" ]; then
    source "${SCRIPT_DIR}/set_keys.sh"
fi
if [ -z "${OPENAI_API_KEY:-}" ]; then
    echo "  Fehler: OPENAI_API_KEY nicht gesetzt."
    echo "  set_keys.sh eintragen oder exportieren."
    exit 1
fi

# ---- 3. Docker-Image bauen ----
echo ""
echo ">>> [3/6] Docker-Image bauen ..."
docker build -f "${SCRIPT_DIR}/docker/Dockerfile" -t strands-agent:latest "${SCRIPT_DIR}"
echo "  ✅ Image gebaut"

# ---- 4. Image in kind laden ----
echo ""
echo ">>> [4/6] Image in kind laden ..."
kind load docker-image strands-agent:latest --name "${CLUSTER_NAME}"
echo "  ✅ Image geladen"

# ---- 5. Namespace + Secrets + ConfigMap ----
echo ""
echo ">>> [5/6] K8s-Ressourcen anlegen ..."

kubectl create ns strands --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "${SCRIPT_DIR}/deploy/k8s/configmap.yaml" -n strands

kubectl create secret generic strands-agent-secret \
    --from-literal=OPENAI_API_KEY="${OPENAI_API_KEY}" \
    -n strands --dry-run=client -o yaml | kubectl apply -f -

kubectl apply -f "${SCRIPT_DIR}/deploy/k8s/deployment.yaml" -n strands
kubectl apply -f "${SCRIPT_DIR}/deploy/k8s/hpa.yaml" -n strands
kubectl apply -f "${SCRIPT_DIR}/deploy/k8s/service.yaml" -n strands

echo "  ✅ K8s-Ressourcen angelegt"

# ---- 6. Warten auf Ready + Info ----
echo ""
echo ">>> [6/6] Warten auf Pods (max 60s) ..."
kubectl wait --for=condition=Available -n strands deploy/strands-agent --timeout=60s >/dev/null 2>&1

echo ""
echo "================================"
echo " ✅ strands-agent läuft im kind-Cluster '${CLUSTER_NAME}'"
echo "================================"
echo ""
echo "   Browser: http://localhost:8088"
echo "   (Host Port 8088 → NodePort 30080 → Service 8080)"
echo ""
echo "   Pods:"
kubectl get pods -n strands -o wide

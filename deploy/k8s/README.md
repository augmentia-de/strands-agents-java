# Deploy Strands Agent to Kubernetes

## Prerequisites
- Kubernetes cluster (minikube, kind, or cloud)
- kubectl configured

## Quick Start

```bash
# Create namespace
kubectl create ns strands

# Apply manifests
kubectl apply -f deploy/k8s/configmap.yaml -n strands
kubectl apply -f deploy/k8s/secret.yaml -n strands
kubectl apply -f deploy/k8s/deployment.yaml -n strands
kubectl apply -f deploy/k8s/hpa.yaml -n strands
kubectl apply -f deploy/k8s/service.yaml -n strands

# Check status
kubectl get pods -n strands -w
```

## With Helm

```bash
helm upgrade --install strands-agent deploy/helm/strands-agent \
  --set env.OPENAI_API_KEY="sk-..." \
  --namespace strands --create-namespace
```

## With Vault Agent Injector

```bash
helm upgrade --install strands-agent deploy/helm/strands-agent \
  --set vault.enabled=true \
  --set vault.agentInject=true \
  --set env.VAULT_ADDR=http://vault:8200 \
  --namespace strands
```

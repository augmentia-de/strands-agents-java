#!/usr/bin/env bash
# ============================================================
# build.sh — Docker-Image-Build (JVM, Native, Lambda)
# ============================================================
# Usage:
#   ./scripts/build.sh                          # JVM :latest
#   ./scripts/build.sh --native                 # Native :latest
#   ./scripts/build.sh --lambda                 # Native + Lambda-Adapter
#   ./scripts/build.sh --native --tag v1.0      # Native mit Tag
#   ./scripts/build.sh --jvm --push gcr.io/my-project/agent
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

MODE="jvm"
TAG="latest"
PUSH=""
IMAGE_NAME=""
SERVICE="strands-agent"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --jvm)      MODE="jvm"; shift ;;
        --native)   MODE="native"; shift ;;
        --lambda)   MODE="lambda"; shift ;;
        --tag)      TAG="$2"; shift 2 ;;
        --push)     PUSH="yes"; IMAGE_NAME="$2"; shift 2 ;;
        --service)  SERVICE="$2"; shift 2 ;;
        --help|-h)  sed -n 's/^# //p; /^set -e/q' "$0"; exit 0 ;;
        *)          echo "Unbekannt: $1 (--help für Hilfe)"; exit 1 ;;
    esac
done

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }

cd "$PROJECT_ROOT"

case "$MODE" in
    native)
        DOCKERFILE="strands-agents-quarkus/src/main/docker/Dockerfile.native"
        LABEL="Native (GraalVM/Mandrel)"
        ;;
    lambda)
        info "Schritt 1/2: Native-Base-Image bauen …"
        docker build -f "strands-agents-quarkus/src/main/docker/Dockerfile.native" \
            -t "strands-agent-native:latest" .
        DOCKERFILE="strands-agents-quarkus/src/main/docker/Dockerfile.lambda"
        LABEL="Lambda Native + Web Adapter"
        ;;
    *)
        DOCKERFILE="strands-agents-quarkus/src/main/docker/Dockerfile.jvm"
        LABEL="JVM (Eclipse Temurin JRE)"
        ;;
esac

info "Baue Image ($LABEL) für Service '$SERVICE' …"
docker build -f "$DOCKERFILE" -t "${SERVICE}:${TAG}" .
ok "Image '${SERVICE}:${TAG}' erstellt"

if [[ -n "$PUSH" && -n "$IMAGE_NAME" ]]; then
    docker tag "${SERVICE}:${TAG}" "$IMAGE_NAME:$TAG"
    docker push "$IMAGE_NAME:$TAG"
    ok "Image gepusht: $IMAGE_NAME:$TAG"
fi

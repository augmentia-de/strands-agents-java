#!/usr/bin/env bash
# ============================================================
# build-image.sh — Docker-native Image-Build (JVM oder Native)
# ============================================================
# Usage:
#   ./build-image.sh                          # JVM :latest
#   ./build-image.sh --native                 # Native :latest
#   ./build-image.sh --tag v1.0 --push gcr.io/my-project/strands-agent
# ============================================================
set -euo pipefail

# Zentrale Keys einbinden (falls vorhanden)
[[ -f "$(dirname "$0")/set_keys.sh" ]] && source "$(dirname "$0")/set_keys.sh"

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

MODE="jvm"
TAG="latest"
PUSH=""
IMAGE_NAME=""
SERVICE="strands-agent"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --native)   MODE="native"; shift ;;
        --lambda)   MODE="lambda"; shift ;;
        --tag)      TAG="$2"; shift 2 ;;
        --push)     PUSH="yes"; IMAGE_NAME="$2"; shift 2 ;;
        --service)  SERVICE="$2"; shift 2 ;;
        *)          echo "Unbekannt: $1"; exit 1 ;;
    esac
done

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }

cd "$PROJECT_ROOT"

case "$MODE" in
    native)
        DOCKERFILE="strands-agents-quarkus/src/main/docker/Dockerfile.native"
        LABEL="Native (UBI Minimal)"
        ;;
    lambda)
        info "Schritt 1/2: Native-Base-Image …"
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

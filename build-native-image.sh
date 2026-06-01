#!/usr/bin/env bash
set -euo pipefail

IMAGE_TAG=${1:-strands-agents-quarkus:latest}

cd "$(dirname "$0")"

echo "=== Building Docker image: ${IMAGE_TAG} ==="
docker build \
  -f strands-agents-quarkus/src/main/docker/Dockerfile.native \
  -t "${IMAGE_TAG}" \
  .

echo "=== Done: ${IMAGE_TAG} ==="
docker images --filter "reference=${IMAGE_TAG}"

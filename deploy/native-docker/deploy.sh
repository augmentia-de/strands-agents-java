#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Checking Native Image ==="
if ! docker image inspect strands-agents-quarkus:latest >/dev/null 2>&1; then
  echo "Error: strands-agents-quarkus:latest image not found."
  echo "Please run ./build-native-image.sh first."
  exit 1
fi

echo "=== Preparing local .env ==="
if [ ! -f "$SCRIPT_DIR/.env" ]; then
  echo "OPENAI_API_KEY=your_key_here" > "$SCRIPT_DIR/.env"
  echo "OPENAI_BASE_URL=https://openrouter.ai/api/v1" >> "$SCRIPT_DIR/.env"
  echo "OPENAI_MODEL=openai/gpt-oss-120b:free" >> "$SCRIPT_DIR/.env"
  echo "Created .env template — please edit $SCRIPT_DIR/.env and add your API key."
fi

echo "=== Ensuring Directories ==="
mkdir -p "$SCRIPT_DIR/shared_data/workspace" "$SCRIPT_DIR/.sessions" "$SCRIPT_DIR/logs"

echo "=== Starting Containers ==="
cd "$SCRIPT_DIR" && docker compose up -d

echo "=== Deployment Status ==="
cd "$SCRIPT_DIR" && docker compose ps

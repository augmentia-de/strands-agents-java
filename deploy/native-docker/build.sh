#!/usr/bin/env bash
set -euo pipefail

# build.sh
# Triggers the native image build from the project root

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/../../"

echo "=== Starting Native Build in $(pwd) ==="
./build-native-image.sh strands-agents-quarkus:latest

echo "=== Build Complete ==="
docker images strands-agents-quarkus:latest

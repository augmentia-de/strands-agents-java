#!/usr/bin/env bash
set -euo pipefail

VM_NAME="strands-agent-vm"
ZONE="us-central1-a"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Preparing local .env ==="
if [ ! -f "$SCRIPT_DIR/.env" ]; then
  echo "OPENAI_API_KEY=your_key_here" > "$SCRIPT_DIR/.env"
  echo "OPENAI_BASE_URL=https://openrouter.ai/api/v1" >> "$SCRIPT_DIR/.env"
  echo "OPENAI_MODEL=openai/gpt-oss-120b:free" >> "$SCRIPT_DIR/.env"
  echo "Created .env template — please edit $SCRIPT_DIR/.env and add your API key."
fi

echo "=== 1. Neue Dateien auf die VM laden ==="
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='
    if [ -d ~/deploy-strands/config ]; then
        sudo chown -R $USER:$USER ~/deploy-strands/config
    fi
'
gcloud compute scp --recurse \
    "$SCRIPT_DIR/docker-compose.yml" \
    "$SCRIPT_DIR/config" \
    "$SCRIPT_DIR/.env" \
    "$VM_NAME":~/deploy-strands/ --zone="$ZONE"

echo "=== 2. Container sauber neu starten (Down -> Up) ==="
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command="
    cd ~/deploy-strands && \
    sudo chown -R 1001:1001 shared_data config .sessions logs && \
    sudo docker compose down && \
    sudo docker compose up -d
"

echo "=== 3. Logs zur Verifizierung anzeigen ==="
sleep 2
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command="sudo docker logs strands-agent --tail=20"
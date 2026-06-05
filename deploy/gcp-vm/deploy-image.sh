#!/usr/bin/env bash
set -euo pipefail

VM_NAME="strands-agent-vm"
ZONE="us-central1-a"
PROJECT_ID=$(gcloud config get-value project)

echo "=== Checking Native Image ==="
if ! docker image inspect strands-agents-quarkus:latest >/dev/null 2>&1; then
  echo "Error: strands-agents-quarkus:latest image not found."
  echo "Please run ./scripts/build.sh --native first."
  exit 1
fi

echo "=== Redeploying to existing GCP VM: $VM_NAME ==="

if ! gcloud compute instances describe "$VM_NAME" --zone="$ZONE" >/dev/null 2>&1; then
    echo "Error: VM '$VM_NAME' does not exist. Please run deploy-vm.sh first."
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Preparing local .env and config ==="
if [ ! -f "$SCRIPT_DIR/.env" ]; then
  echo "OPENAI_API_KEY=your_key_here" > "$SCRIPT_DIR/.env"
  echo "OPENAI_BASE_URL=https://openrouter.ai/api/v1" >> "$SCRIPT_DIR/.env"
  echo "OPENAI_MODEL=openai/gpt-oss-120b:free" >> "$SCRIPT_DIR/.env"
  echo "Created .env template — please edit $SCRIPT_DIR/.env and add your API key."
fi

TMP_CONFIG="/tmp/strands-deploy-config"
mkdir -p "$TMP_CONFIG"
cat > "$TMP_CONFIG/MCP_SERVER_CONFIG.json" <<EOF
{
  "mcpServers": {
    "filesystem": {
      "type": "sse",
      "url": "http://mcp-filesystem:8080/sse"
    }
  }
}
EOF
cp "$SCRIPT_DIR/config/application.properties" "$TMP_CONFIG/"
cp "$SCRIPT_DIR/config/set_key.sh" "$TMP_CONFIG/"

echo "=== Syncing Files to VM ==="
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='
    if [ -d ~/deploy-strands/config ]; then
        sudo chown -R $USER:$USER ~/deploy-strands/config
    fi
'
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='mkdir -p ~/deploy-strands/config'

gcloud compute scp --recurse \
    "$SCRIPT_DIR/docker-compose.yml" \
    "$SCRIPT_DIR/.env" \
    "$VM_NAME":~/deploy-strands/ --zone="$ZONE"
gcloud compute scp \
    "$TMP_CONFIG/MCP_SERVER_CONFIG.json" \
    "$TMP_CONFIG/application.properties" \
    "$TMP_CONFIG/set_key.sh" \
    "$VM_NAME":~/deploy-strands/config/ --zone="$ZONE"

echo "=== Transferring Native Image (this may take a while) ==="
docker save strands-agents-quarkus:latest | gzip | \
    gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command 'sudo gunzip | sudo docker load'

echo "=== Restarting Containers with New Image ==="
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='
    cd ~/deploy-strands && \
    mkdir -p shared_data/workspace .sessions logs && \
    sudo chown -R 1001:1001 shared_data config .sessions logs && \
    sudo docker compose down && \
    sudo docker compose up -d
'

echo "=== Redeployment on GCP Finished ==="
EXTERNAL_IP=$(gcloud compute instances describe "$VM_NAME" --zone="$ZONE" \
    --format='get(networkInterfaces[0].accessConfigs[0].natIP)')
echo "Strands Agent URL: http://$EXTERNAL_IP:8082"
echo "MCP Filesystem URL: http://$EXTERNAL_IP:3000"
echo
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='sudo docker compose logs strands-agent --tail=10'
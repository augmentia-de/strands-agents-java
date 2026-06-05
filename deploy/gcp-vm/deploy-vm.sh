#!/usr/bin/env bash
set -euo pipefail

VM_NAME="strands-agent-vm"
ZONE="us-central1-a"
MACHINE_TYPE="e2-micro"
PROJECT_ID=$(gcloud config get-value project)

if [ -z "$PROJECT_ID" ] || [ "$PROJECT_ID" = "(unset)" ]; then
  echo "Error: No GCP project set. Run: gcloud config set project <PROJECT_ID>"
  exit 1
fi

echo "=== Checking Native Image ==="
if ! docker image inspect strands-agents-quarkus:latest >/dev/null 2>&1; then
  echo "Error: strands-agents-quarkus:latest image not found."
  echo "Please run ./scripts/build.sh --native first."
  exit 1
fi

echo "=== Managing GCP VM: $VM_NAME ==="

if gcloud compute instances describe "$VM_NAME" --zone="$ZONE" >/dev/null 2>&1; then
    echo "VM already exists. Deleting to ensure a fresh replacement..."
    gcloud compute instances delete "$VM_NAME" --zone="$ZONE" --quiet
fi

echo "Creating new lightweight Debian 12 VM instance..."
gcloud compute instances create "$VM_NAME" \
    --zone="$ZONE" \
    --machine-type="e2-micro" \
    --image-family="debian-12" \
    --image-project="debian-cloud" \
    --tags=http-server \
    --metadata="google-logging-enabled=true"

echo "Waiting for VM to initialize..."
sleep 30

echo "Installing Docker and Docker Compose on Debian VM..."
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command="
    sudo apt-get update && \
    sudo apt-get install -y ca-certificates curl gnupg && \
    sudo install -m 0755 -d /etc/apt/keyrings && \
    curl -fsSL https://download.docker.com/linux/debian/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg && \
    sudo chmod a+r /etc/apt/keyrings/docker.gpg && \
    echo \"deb [arch=\$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/debian \$(. /etc/os-release && echo \$VERSION_CODENAME) stable\" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null && \
    sudo apt-get update && \
    sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin && \
    sudo usermod -aG docker \$USER
"

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

echo "=== Starting Containers on VM ==="
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='
    cd ~/deploy-strands && \
    mkdir -p shared_data/workspace .sessions logs && \
    sudo chown -R 1001:1001 shared_data config .sessions logs && \
    sudo docker compose up -d
'

echo "=== Deployment on GCP Finished ==="
EXTERNAL_IP=$(gcloud compute instances describe "$VM_NAME" --zone="$ZONE" \
    --format='get(networkInterfaces[0].accessConfigs[0].natIP)')
echo "Strands Agent URL: http://$EXTERNAL_IP:8082"
echo "MCP Filesystem URL: http://$EXTERNAL_IP:3000"
echo
gcloud compute ssh "$VM_NAME" --zone="$ZONE" --command='sudo docker compose logs strands-agent --tail=10'
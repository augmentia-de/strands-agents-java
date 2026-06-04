#!/bin/bash

# Configuration
VAULT_CONTAINER="strands-vault"
INIT_FILE="vault-init.json"

echo "--- Starting HashiCorp Vault Deployment ---"
docker compose -f docker-compose.yaml up -d

echo "Waiting for Vault to start..."
sleep 5

# Check if already initialized
INIT_STATUS=$(docker exec $VAULT_CONTAINER vault status -format=json | jq -r '.initialized')

if [ "$INIT_STATUS" == "false" ]; then
    echo "Initializing Vault..."
    docker exec $VAULT_CONTAINER vault operator init -key-shares=1 -key-threshold=1 -format=json > $INIT_FILE
    echo "Initialization complete. Keys saved to $INIT_FILE (PROTECT THIS FILE!)"
else
    echo "Vault is already initialized."
fi

# Extract keys
UNSEAL_KEY=$(jq -r '.unseal_keys_b64[0]' $INIT_FILE)
ROOT_TOKEN=$(jq -r '.root_token' $INIT_FILE)

# Unseal Vault
echo "Unsealing Vault..."
docker exec $VAULT_CONTAINER vault operator unseal $UNSEAL_KEY

# Enable KV engine if not already enabled
echo "Configuring Vault..."
docker exec -e VAULT_TOKEN=$ROOT_TOKEN $VAULT_CONTAINER vault login $ROOT_TOKEN > /dev/null
docker exec -e VAULT_TOKEN=$ROOT_TOKEN $VAULT_CONTAINER vault secrets enable -path=secret kv-v2 2>/dev/null || echo "KV engine already enabled."

# Seed secrets (optional – add your real keys here)
# docker exec -e VAULT_TOKEN=$ROOT_TOKEN $VAULT_CONTAINER vault kv put secret/openai api_key="sk-your-openai-key"
# docker exec -e VAULT_TOKEN=$ROOT_TOKEN $VAULT_CONTAINER vault kv put secret/tavily api_key="tvly-your-tavily-key"

echo "--- Setup Finished ---"
echo "Vault UI: http://localhost:8200"
echo "Root Token: $ROOT_TOKEN"
echo ""
echo "Next step: export VAULT_ADDR=http://localhost:8200 VAULT_TOKEN=$ROOT_TOKEN"
echo "Then store keys:"
echo "  vault kv put secret/openai api_key=sk-..."
echo "  vault kv put secret/tavily api_key=tvly-..."

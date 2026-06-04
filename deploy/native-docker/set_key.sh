#!/usr/bin/env bash
# set_key.sh
# This script sets the session environment variables.
# For Docker deployments, these are also written to the .env file.

export OPENAI_BASE_URL="https://openrouter.ai/api/v1"
export OPENAI_MODEL="openai/gpt-oss-120b:free"

echo "OpenRouter configuration set (Base URL and Model)."

if [ $# -eq 1 ]; then
    export OPENAI_API_KEY="$1"
    echo "API Key set from argument."
elif [ -z "${OPENAI_API_KEY:-}" ]; then
    read -sp "Enter OPENAI_API_KEY: " key
    echo
    export OPENAI_API_KEY="$key"
fi

# Update .env file for docker-compose consistency
cat > .env <<EOF
OPENAI_API_KEY=${OPENAI_API_KEY}
OPENAI_BASE_URL=${OPENAI_BASE_URL}
OPENAI_MODEL=${OPENAI_MODEL}
EOF

echo ".env file updated for Docker Compose."

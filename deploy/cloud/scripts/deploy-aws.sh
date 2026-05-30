#!/usr/bin/env bash
# ============================================================
# deploy-aws.sh — AWS Lambda / ECS (Container Image)
# ============================================================
# Baut Native-Image + Lambda-Adapter und deployt als Lambda.
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"

# Zentrale Keys einbinden
SET_KEYS="$SCRIPT_DIR/set_keys.sh"
echo $SCRIPT_DIR
[[ -f "$SET_KEYS" ]] && source "$SET_KEYS"

echo
# Cloud Credentials einbinden
CLOUD_CREDS="$SCRIPT_DIR/set_cloud_credentials.sh"
[[ -f "$CLOUD_CREDS" ]] && source "$CLOUD_CREDS"

# ── Konfiguration ──────────────────────────────────────────
AWS_REGION="${AWS_REGION:-eu-central-1}"
AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID:-}"
ECR_REPO="${ECR_REPO:-cloud/cloud-quarkus}"
IMAGE_TAG="${IMAGE_TAG:-latest}"
SERVICE_NAME="${SERVICE_NAME:-cloud-quarkus}"
MEMORY="${MEMORY:-512}"     # MB
TIMEOUT="${TIMEOUT:-30}"    # Sekunden
ENV_FILE="${ENV_FILE:-}"    # Optional: Pfad zu .env mit Lambda-Umgebungsvariablen

REGISTRY="$AWS_ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
FULL_IMAGE="$REGISTRY/$ECR_REPO:$IMAGE_TAG"

clean_account_id() {
    if [[ -z "$AWS_ACCOUNT_ID" ]]; then
        err "AWS_ACCOUNT_ID ist nicht gesetzt."
        err ""
        err "1) Deine Account-ID findest du unter:"
        err "   https://console.aws.amazon.com/billing/home?#/account"
        err "   Oder via: aws sts get-caller-identity --query Account --output text"
        err ""
        err "2) Setze sie in set_keys.sh:"
        err "   export AWS_ACCOUNT_ID=123456789012"
        exit 1
    fi
    AWS_ACCOUNT_ID="${AWS_ACCOUNT_ID//-/}"
    if ! [[ "$AWS_ACCOUNT_ID" =~ ^[0-9]{12}$ ]]; then
        err "AWS_ACCOUNT_ID '$AWS_ACCOUNT_ID' ist keine gültige 12-stellige Zahl."
        exit 1
    fi
    ok "AWS Account-ID: $AWS_ACCOUNT_ID"
}

info()  { echo -e "\033[0;36m[INFO]\033[0m  $*"; }
ok()    { echo -e "\033[0;32m[OK]\033[0m    $*"; }
err()   { echo -e "\033[0;31m[ERROR]\033[0m $*"; }

check_aws() {
    if ! command -v aws &>/dev/null; then err "aws CLI nicht installiert."; exit 1; fi
    if ! aws sts get-caller-identity &>/dev/null; then
        err "Keine AWS-Credentials gefunden."
        err "Einrichten mit: aws configure"
        exit 1
    fi
    ok "AWS authentifiziert (Account: $AWS_ACCOUNT_ID, Region: $AWS_REGION)"
}

ecr_login() {
    aws ecr get-login-password --region "$AWS_REGION" | \
        docker login --username AWS --password-stdin "$REGISTRY"
    ok "ECR-Login erfolgreich"
}

ensure_ecr() {
    if ! aws ecr describe-repositories --repository-names "$ECR_REPO" --region "$AWS_REGION" &>/dev/null; then
        info "Erstelle ECR-Repository '$ECR_REPO' …"
        aws ecr create-repository --repository-name "$ECR_REPO" --region "$AWS_REGION" &>/dev/null
        ok "ECR-Repository erstellt"
    else
        ok "ECR-Repository '$ECR_REPO' existiert"
    fi
}

build_and_push() {
    info "Baue Native-Image …"
    docker build -f "$PROJECT_ROOT/strands-agents-quarkus/src/main/docker/Dockerfile.native" \
        -t strands-agent-native "$PROJECT_ROOT"

    info "Baue Lambda-Adapter-Image …"
    docker build -f "$PROJECT_ROOT/strands-agents-quarkus/src/main/docker/Dockerfile.lambda" \
        -t "$FULL_IMAGE" "$PROJECT_ROOT"

    info "Push nach ECR: $FULL_IMAGE"
    docker push "$FULL_IMAGE"
    ok "Image gepusht"
}

deploy_lambda() {
    if aws lambda get-function --function-name "$SERVICE_NAME" --region "$AWS_REGION" &>/dev/null; then
        info "Aktualisiere Lambda '$SERVICE_NAME' …"
        aws lambda update-function-code \
            --function-name "$SERVICE_NAME" \
            --image-uri "$FULL_IMAGE" \
            --region "$AWS_REGION" \
            --publish &>/dev/null
        ok "Lambda-Funktion aktualisiert"
    else
        info "Erstelle Lambda '$SERVICE_NAME' …"
        local create_args=(
            --function-name "$SERVICE_NAME"
            --package-type Image
            --code ImageUri="$FULL_IMAGE"
            --role "arn:aws:iam::$AWS_ACCOUNT_ID:role/${SERVICE_NAME}-role"
            --region "$AWS_REGION"
            --memory-size "$MEMORY"
            --timeout "$TIMEOUT"
        )
        if ! aws lambda create-function "${create_args[@]}" 2>/dev/null; then
            err "Lambda-Erstellung fehlgeschlagen. IAM-Rolle '${SERVICE_NAME}-role' existiert?"
            err "Erstelle sie mit:"
            err "  aws iam create-role --role-name ${SERVICE_NAME}-role \\"
            err "    --assume-role-policy-document '{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"Service\":\"lambda.amazonaws.com\"},\"Action\":\"sts:AssumeRole\"}]}'"
            exit 1
        fi
        ok "Lambda-Funktion erstellt"
    fi
}

set_env_vars() {
    local vars=(
        "VAULT_ADDR=${VAULT_ADDR:-}"
        "OPENAI_BASE_URL=${OPENAI_BASE_URL:-}"
        "OPENAI_MODEL=${OPENAI_MODEL:-}"
        "OPENAI_API_KEY=${OPENAI_API_KEY:-}"
    )
    if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
        while IFS='=' read -r key value; do
            [[ -n "$key" && "$key" != \#* ]] && vars+=("$key=$value")
        done < "$ENV_FILE"
    fi
    local env_json="{"
    local first=true
    for var in "${vars[@]}"; do
        local key="${var%%=*}"
        local val="${var#*=}"
        if [[ -n "$val" ]]; then
            $first || env_json+=","
            env_json+="\"$key\":\"$val\""
            first=false
        fi
    done
    env_json+="}"
    if [[ "$env_json" != "{}" ]]; then
        aws lambda update-function-configuration \
            --function-name "$SERVICE_NAME" \
            --environment "Variables=${env_json}" \
            --region "$AWS_REGION" &>/dev/null
        ok "Umgebungsvariablen gesetzt"
    fi
}

create_api_gateway() {
    local api_name="${SERVICE_NAME}-api"
    local api_id
    api_id=$(aws apigateway create-rest-api \
        --name "$api_name" \
        --region "$AWS_REGION" \
        --query 'id' --output text 2>/dev/null || true)
    if [[ -z "$api_id" ]]; then
        info "API Gateway '$api_name' existiert bereits oder Fehler"
        return
    fi
    local root_id
    root_id=$(aws apigateway get-resources --rest-api-id "$api_id" \
        --region "$AWS_REGION" --query 'items[0].id' --output text)
    local proxy_id
    proxy_id=$(aws apigateway create-resource \
        --rest-api-id "$api_id" \
        --parent-id "$root_id" \
        --path-part "{proxy+}" \
        --region "$AWS_REGION" \
        --query 'id' --output text)
    local function_arn="arn:aws:lambda:$AWS_REGION:$AWS_ACCOUNT_ID:function:$SERVICE_NAME"
    for resource_id in "$root_id" "$proxy_id"; do
        aws apigateway put-method \
            --rest-api-id "$api_id" \
            --resource-id "$resource_id" \
            --http-method ANY \
            --authorization-type NONE \
            --region "$AWS_REGION" &>/dev/null || true
        aws apigateway put-integration \
            --rest-api-id "$api_id" \
            --resource-id "$resource_id" \
            --http-method ANY \
            --type AWS_PROXY \
            --integration-http-method POST \
            --uri "arn:aws:apigateway:$AWS_REGION:lambda:path/2015-03-31/functions/$function_arn/invocations" \
            --region "$AWS_REGION" &>/dev/null || true
    done
    aws apigateway create-deployment \
        --rest-api-id "$api_id" \
        --stage-name prod \
        --region "$AWS_REGION" &>/dev/null || true
    aws lambda add-permission \
        --function-name "$SERVICE_NAME" \
        --statement-id "apigateway-${api_id}" \
        --action lambda:InvokeFunction \
        --principal apigateway.amazonaws.com \
        --source-arn "arn:aws:execute-api:$AWS_REGION:$AWS_ACCOUNT_ID:$api_id/*/*" \
        --region "$AWS_REGION" &>/dev/null || true
    local api_url="https://${api_id}.execute-api.${AWS_REGION}.amazonaws.com/prod"
    ok "API Gateway erstellt: $api_url"
}

# ── Main ────────────────────────────────────────────────────
check_aws

if [[ "$AWS_ACCOUNT_ID" == "CHANGEME" ]]; then
    err "AWS_ACCOUNT_ID in set_keys.sh nicht gesetzt!"
    exit 1
fi

clean_account_id
ecr_login
ensure_ecr
build_and_push
deploy_lambda
set_env_vars
create_api_gateway

echo ""
ok "AWS-Lambda-Deployment abgeschlossen!"
info "API-URL: https://ID.execute-api.$AWS_REGION.amazonaws.com/prod/"

# Secret Management

## Resolution Chain

The `SecretService` (Quarkus) resolves API keys in the following priority order:

```
runtimeKey (Web UI / Admin API, in-memory)
  → Hashicorp Vault (VAULT_ADDR + VAULT_TOKEN)
    → CloudSecretProvider (AWS SSM / GCP Secret Manager / Azure KV)
      → Environment Variable (OPENAI_API_KEY)
        → MockChatModel (no key = dummy agent)
```

Each stage can fail (e.g. Vault unreachable, SSM parameter not found), in which case
the next stage is tried.

---

## Option A: Environment Variables (Dev / Simple)

```bash
export OPENAI_API_KEY=sk-...
```

Development only. Avoid in production since the key resides unencrypted in the
process environment.

---

## Option B: Cloud-Native Secret Stores

All three implementations use **plain HTTP calls** — no AWS SDK, no Azure SDK,
no GCP SDK. This keeps native images small and dependencies minimal.

### B1: AWS SSM Parameter Store (Lambda)

**Mechanism:**

```
Lambda process → localhost:2773 → Parameters & Secrets Extension → AWS SSM API
```

The **AWS Parameters & Secrets Lambda Extension** runs as a sidecar in the Lambda
container and caches the secret. It automatically signs requests with the Lambda
execution role (SigV4). No shared secret required.

**Prerequisites:**

- Lambda execution role needs `ssm:GetParameter` on the parameter (set up
  automatically by `deploy.sh`)
- SSM parameter exists (SecureString recommended):

  ```bash
  aws ssm put-parameter \
      --name "/cloud-quarkus/openai-api-key" \
      --value "sk-..." \
      --type SecureString \
      --overwrite
  ```

- The Dockerfile must include the extension (see Dockerfile.lambda)

**Config:**

```properties
strands.secret.cloud.provider=aws
strands.secret.aws.ssm.path=/cloud-quarkus/openai-api-key
```

**Dockerfile (already extended):**

```dockerfile
RUN case "$(uname -m)" in \
        aarch64) ARCH="-arm64"; ;; \
        *) ARCH=""; ;; \
    esac && \
    curl -sL "https://aws-parameters-and-secrets-lambda-extension.s3.amazonaws.com/parameters-and-secrets-lambda-extension${ARCH}.zip" \
        -o /tmp/params-ext.zip && \
    unzip -o /tmp/params-ext.zip -d /opt/extensions/
```

**Rotation:**

```bash
aws ssm put-parameter --name "/cloud-quarkus/openai-api-key" \
    --value "$NEW_KEY" --type SecureString --overwrite
```

The next Lambda cold start (approx. 30 min) or deployment picks up the new value.
The extension caches with a configurable TTL (default: 300s).

---

### B2: GCP Secret Manager (Cloud Run / Cloud Functions / GCE)

**Mechanism:**

```
Cloud Run → Metadata Server (169.254.169.254) → Access Token
  → Secret Manager REST API → Base64-decoded Secret
```

The GCP environment provides a **Metadata Server** at IP `169.254.169.254`.
It returns an OAuth2 access token for the runtime service account. This token
is used to call the Secret Manager REST API.

**Prerequisites:**

- Cloud Run / Cloud Functions / GCE has a runtime service account
- Service account has `roles/secretmanager.secretAccessor` on the secret:

  ```bash
  gcloud secrets add-iam-policy-binding openai-api-key \
      --member="serviceAccount:cloud-run-sa@project.iam.gserviceaccount.com" \
      --role="roles/secretmanager.secretAccessor"
  ```

- Secret exists:

  ```bash
  gcloud secrets create openai-api-key \
      --replication-policy="automatic"
  echo -n "sk-..." | gcloud secrets versions add openai-api-key --data-file=-
  ```

**Config:**

```properties
strands.secret.cloud.provider=gcp
strands.secret.gcp.secret-id=openai-api-key
# Project ID is auto-detected from Metadata Server:
# strands.secret.gcp.project-id=my-project
```

**Rotation:**

```bash
echo -n "$NEW_KEY" | gcloud secrets versions add openai-api-key --data-file=-
```

Cloud Run reads the latest `latest` version on each new instance startup.
The metadata provider caches the access token until expiry (max 1h).

---

### B3: Azure Key Vault (Azure Functions / App Service)

Coming soon. Functional principle mirrors GCP:

```
Azure Function → Managed Identity Endpoint (169.254.169.254)
  → OAuth2 Token → Key Vault REST API → Secret
```

**Planned config:**

```properties
strands.secret.cloud.provider=azure
strands.secret.azure.keyvault.url=https://myvault.vault.azure.net
strands.secret.azure.keyvault.secret-name=openai-api-key
```

---

## Option C: Remote Admin Service via REST

A **central Admin Service** on a GCP VM holds all API keys (encrypted via
Hashicorp Vault or GCP Secret Manager). Serverless functions from any cloud
call the Admin Service at startup via REST.

**Architecture:**

```
                         ┌──────────────────┐
                         │  OpenAI API       │
                         └────────┬─────────┘
                                  │
             ┌────────────────────┼────────────────────┐
             │                    │                    │
        AWS Lambda          GCP Cloud Run        Azure Functions
             │                    │                    │
             └────────┬───────────┼────────────────────┘
                      │           │
                      ▼           ▼
              ┌──────────────────────────────┐
              │  Admin Service (GCP VM)       │
              │  - Vault / GCP Secret Manager │
              │  - Authenticator per Cloud     │
              │  - Audit-Log (SOC 2)          │
              └──────────────────────────────┘
```

### Authentication (without Shared Secret)

Each cloud provides its workload with a **cryptographically signed identity**:

| Cloud | Method | Header | Verification |
|-------|---------|--------|--------------|
| AWS | SigV4 / IAM | `Authorization: AWS4-HMAC-SHA256 ...` | STS.GetCallerIdentity or signature |
| GCP | Identity Token (JWT) | `Authorization: Bearer <JWT>` | Google Public Keys (JWKS) |
| Azure | Managed Identity Token | `Authorization: Bearer <JWT>` | Azure AD Public Keys (JWKS) |

### Interface (Authenticator)

```java
public interface Authenticator {
    /** Validates the request and returns the identity string (e.g. SA email, Role ARN) */
    String authenticate(HttpRequest request);
}
```

Pluggable via config:

```properties
strands.secret.remote.url=https://admin.internal:8443/api/admin/key
strands.secret.remote.authenticators=gcp,aws,azure
```

### Audit-Log (SOC 2)

Every secret access is logged:

```json
{
  "ts": "2026-06-09T12:00:00Z",
  "identity": "cloud-run-sa@project.iam.gserviceaccount.com",
  "provider": "gcp",
  "action": "GET_SECRET",
  "resource": "openai/api_key",
  "success": true,
  "ip": "10.0.0.1"
}
```

---

## Option D: Hashicorp Vault (existing)

Runs on the GCP VM. `SecretService` reads via `VaultSecretProvider`.

```properties
VAULT_ADDR=http://localhost:8200
VAULT_TOKEN=s.your-token-here
VAULT_MOUNT_PATH=secret
```

Expected paths:
- `secret/openai/data` (KV v2) with key `api_key`
- `secret/tavily/data` (KV v2) with key `api_key`

---

## Option E: Runtime API Key (Web UI)

Via the admin UI at `/keys` or REST endpoints:

```bash
POST /api/admin/setup    { "apiKey": "sk-..." }   # stores in ApiKeyVault
POST /api/admin/activate { "apiKey": "sk-..." }   # activates in RAM (volatile)
```

For testing only — lost on restart.

---

## Key Rotation Matrix

| Method | Rotation Command | Rebuild Needed? | Audit |
|--------|-----------------|-----------------|-------|
| Env Var | `aws lambda update-function-configuration` | No | CloudWatch |
| AWS SSM | `aws ssm put-parameter --overwrite` | No | CloudTrail |
| GCP SM | `gcloud secrets versions add` | No | Cloud Audit Logs |
| Azure KV | `az keyvault secret set` | No | Azure Monitor |
| Remote Admin | REST call to Admin Service | No | Custom Audit Log |
| Hashicorp Vault | `vault kv put` | No | Vault Audit Log |
| Runtime API Key | POST /api/admin/activate | No | No audit |

---

## Deployment Matrix

| Method | AWS Lambda | GCP Cloud Run | GCP VM | Azure Functions | Local |
|--------|-----------|---------------|--------|-----------------|-------|
| Env Var | ✓ | ✓ | ✓ | ✓ | ✓ |
| AWS SSM | ✓ (Extension) | — | — | — | — |
| GCP SM | — | ✓ (Meta Server) | ✓ (Meta Server) | — | — |
| Azure KV | — | — | — | ✓ (Managed ID) | — |
| Remote Admin | ✓ | ✓ | — | ✓ | — |
| Vault | ✓ (if reachable) | ✓ (if reachable) | ✓ | ✓ (if reachable) | ✓ |
| Runtime Key | ✓ | ✓ | ✓ | ✓ | ✓ |

---

## Configuration Reference (application.properties)

```properties
# Cloud provider: aws | gcp | azure | (none)
strands.secret.cloud.provider=

# AWS SSM Parameter Store
strands.secret.aws.ssm.path=

# GCP Secret Manager
strands.secret.gcp.project-id=
strands.secret.gcp.secret-id=

# Azure Key Vault (coming)
strands.secret.azure.keyvault.url=
strands.secret.azure.keyvault.secret-name=

# Remote Admin Service (coming)
strands.secret.remote.url=
strands.secret.remote.authenticators=
strands.secret.remote.audit.sink=file
```

---

## Code Architecture

### New Files

```
strands-agents/src/main/java/de/augmentia/strandsagents/core/secret/
├── SecretProvider.java                # Interface (existing)
└── cloud/
    ├── AwsSsmProvider.java            # AWS SSM via Lambda Extension
    ├── GcpSecretManagerProvider.java  # GCP SM via Metadata Server
    ├── AzureKeyVaultProvider.java     # coming
    ├── CloudSecretProviderFactory.java# Factory
    └── remote/
        ├── RemoteSecretProvider.java  # coming
        └── Authenticator.java         # coming

strands-agents-quarkus/src/main/java/de/augmentia/strandsagents/quarkus/service/
└── SecretService.java                 # modified (CloudProvider integrated)

strands-agents-quarkus/src/main/docker/
└── Dockerfile.lambda                  # modified (SSM Extension integrated)

strands-agents-quarkus/src/main/resources/
└── application.properties             # modified (config keys added)
```

### Integration in SecretService

```java
@PostConstruct
void init() {
    // Vault (existing)
    if (VAULT_ADDR and VAULT_TOKEN set) {
        vault = new VaultSecretProvider(VaultConfig.fromEnv());
    }

    // Cloud Secret Provider (new)
    var cloudProviderType = getConfig("STRANDS_SECRET_CLOUD_PROVIDER");
    if (cloudProviderType != null) {
        cloudProvider = CloudSecretProviderFactory.create(
            cloudProviderType,
            getConfig("STRANDS_SECRET_AWS_SSM_PATH"),
            getConfig("STRANDS_SECRET_GCP_PROJECT_ID"),
            getConfig("STRANDS_SECRET_GCP_SECRET_ID")
        );
    }
}

public String getOpenAiApiKey() {
    if (runtimeApiKey != null) return runtimeApiKey;
    if (vault != null) { /* try vault */ }
    if (cloudProvider != null) { /* try cloud */ }
    return System.getenv("OPENAI_API_KEY");
}
```

# strands-secret-provider-gcp

GCP Secret Manager secret provider for Strands Agents.

Fetches API keys from Google Cloud Secret Manager using the GCP metadata server for authentication. No GCP SDK dependency — uses `java.net.http.HttpClient` and calls the Secret Manager REST API directly.

## How it works

The provider uses the GCP instance metadata server (`http://metadata.google.internal`) to:

1. Obtain an OAuth2 access token for the default service account
2. Auto-detect the project ID (if not explicitly configured)
3. Call the Secret Manager API to fetch the latest version of the requested secret
4. Base64-decode the secret payload

This works on all GCP compute platforms: Compute Engine, Cloud Run, GKE, Cloud Functions, and App Engine.

## Requirements

- **GCP environment**: The application must run on a GCP compute platform with metadata server access
- **IAM permissions**: The attached service account needs `secretmanager.versions.access` on the target secret
- **Secret Manager API**: Must be enabled on the project
- **No SDK needed**: Plain HTTP calls, no GCP SDK dependency

### IAM role example

Attach the `roles/secretmanager.secretAccessor` role to the compute service account:

```bash
gcloud projects add-iam-policy-binding PROJECT_ID \
    --member=serviceAccount:SERVICE_ACCOUNT_EMAIL \
    --role=roles/secretmanager.secretAccessor
```

Or use a custom role with the `secretmanager.versions.access` permission.

## Configuration

Set these environment variables:

| Variable | Required | Description |
|---|---|---|
| `STRANDS_SECRET_CLOUD_PROVIDER` | yes | Set to `gcp` |
| `STRANDS_SECRET_GCP_PROJECT_ID` | no | GCP project ID. Auto-detected from metadata server if omitted |
| `STRANDS_SECRET_GCP_SECRET_ID` | yes (or fallback) | The name of the secret in Secret Manager |

### Example

```bash
export STRANDS_SECRET_CLOUD_PROVIDER=gcp
export STRANDS_SECRET_GCP_SECRET_ID=openai-api-key
```

If `STRANDS_SECRET_GCP_PROJECT_ID` is not set, the provider auto-detects it from the metadata server.

## Secret resolution chain

When `SecretService.getOpenAiApiKey()` is called:

1. Runtime API key (set programmatically via Web UI)
2. HashiCorp Vault (if `VAULT_ADDR` / `VAULT_TOKEN` are set)
3. **GCP Secret Manager** (this provider)
4. Environment variable `OPENAI_API_KEY`

The provider returns `null` on any failure (network error, missing secret, invalid permissions), allowing the chain to continue gracefully.

## SPI registration

Registered via Java ServiceLoader — file `META-INF/services/de.augmentia.strandsagents.features.secrets.SecretProviderFactory`:

```
de.augmentia.strandsagents.secrets.gcp.GcpSecretProviderFactory
```

The factory matches the type string `"gcp"` (case-insensitive). No manual wiring needed — just having the module on the classpath is sufficient.

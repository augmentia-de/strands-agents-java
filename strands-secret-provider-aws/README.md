# strands-secret-provider-aws

AWS SSM Parameter Store secret provider for Strands Agents.

Fetches API keys from AWS Systems Manager Parameter Store using the local ECS/EC2 agent endpoint. No AWS SDK dependency — uses `java.net.http.HttpClient` and calls the SSM Parameter Store via `localhost:2773`.

## How it works

The provider connects to the AWS ECS task metadata endpoint (`http://localhost:2773`) which is available on:

- **ECS tasks** with `EnableExecuteCommand` or task IAM role configured
- **EC2 instances** with the SSM Agent and `AWS-ConfigureAWSPackage` installed
- **EKS/Fargate** via the task metadata endpoint

The path is URL-encoded and requested with `?withDecryption=true` so SecureString parameters are decrypted automatically.

## Requirements

- **AWS environment**: The application must run on AWS (ECS, EC2, EKS, Fargate)
- **IAM permissions**: The task/instance role needs `ssm:GetParameter` on the target parameter path
- **No SDK needed**: Plain HTTP calls, no AWS SDK dependency

### IAM policy example

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": "ssm:GetParameter",
            "Resource": "arn:aws:ssm:REGION:ACCOUNT:parameter/PATH/TO/YOUR/PARAMETER"
        }
    ]
}
```

## Configuration

Set these environment variables:

| Variable | Required | Description |
|---|---|---|
| `STRANDS_SECRET_CLOUD_PROVIDER` | yes | Set to `aws` |
| `STRANDS_SECRET_AWS_SSM_PATH` | no | SSM parameter path. If omitted, falls back to `path/key` from the lookup call |

### Example

```bash
export STRANDS_SECRET_CLOUD_PROVIDER=aws
export STRANDS_SECRET_AWS_SSM_PATH=/cloud-quarkus/openai-api-key
```

## Secret resolution chain

When `SecretService.getOpenAiApiKey()` is called:

1. Runtime API key (set programmatically via Web UI)
2. HashiCorp Vault (if `VAULT_ADDR` / `VAULT_TOKEN` are set)
3. **AWS SSM Parameter Store** (this provider)
4. Environment variable `OPENAI_API_KEY`

The provider returns `null` on any failure (network error, missing parameter, invalid permissions), allowing the chain to continue gracefully.

## SPI registration

Registered via Java ServiceLoader — file `META-INF/services/de.augmentia.strandsagents.config.secrets.SecretProviderFactory`:

```
de.augmentia.strandsagents.secrets.aws.AwsSecretProviderFactory
```

The factory matches the type string `"aws"` (case-insensitive). No manual wiring needed — just having the module on the classpath is sufficient.

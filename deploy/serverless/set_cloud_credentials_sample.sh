#!/usr/bin/env bash
# ============================================================
# set_cloud_credentials.sh — Cloud-Zugangsdaten (sicher)
# ============================================================
# Einbinden mit:  source ./deploy/scripts/set_cloud_credentials.sh
#
# ACHTUNG: Diese Datei enthält sensitive Schlüssel!
#   - NIEMALS in Git committen (in .gitignore eintragen)
#   - Berechtigungen auf 600 setzen: chmod 600 set_cloud_credentials.sh
#   - Alternativ: als .env-Datei auslagern und von hier sourcen
# ============================================================

# ── AWS ─────────────────────────────────────────────────────
# Access Key anlegen: IAM → Benutzer → Sicherheitsanmeldeinformationen
export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY}"
export AWS_REGION="${AWS_REGION:-eu-central-1}"

# ── GCP ─────────────────────────────────────────────────────
# Service-Account-Key: IAM → Dienstkonten → JSON-Key erstellen
# Alternativ: gcloud auth login (dann hier leer lassen)
export GOOGLE_APPLICATION_CREDENTIALS="${GOOGLE_APPLICATION_CREDENTIALS:-}"

# ── Azure ───────────────────────────────────────────────────
# Service Principal: Microsoft Entra ID → App-Registrierungen
export AZURE_CLIENT_ID="${AZURE_CLIENT_ID}"
export AZURE_TENANT_ID="${AZURE_TENANT_ID}"
export AZURE_CLIENT_SECRET="${AZURE_CLIENT_SECRET}"
export AZURE_SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID}"

# ── Prüfung ─────────────────────────────────────────────────
check_aws_creds() {
    if [[ "$AWS_ACCESS_KEY_ID" == "CHANGEME" || "$AWS_SECRET_ACCESS_KEY" == "CHANGEME" ]]; then
        echo -e "\033[0;31m[FEHLT]\033[0m AWS_ACCESS_KEY_ID oder AWS_SECRET_ACCESS_KEY nicht gesetzt"
        echo "  Setze sie in deploy/scripts/set_cloud_credentials.sh"
        return 1
    fi
    echo -e "\033[0;32m[OK]\033[0m    AWS-Credentials gesetzt"
}

check_gcp_creds() {
    if [[ -z "$GOOGLE_APPLICATION_CREDENTIALS" ]]; then
        echo -e "\033[0;33m[HINWEIS]\033[0m GOOGLE_APPLICATION_CREDENTIALS nicht gesetzt"
        echo "  Nutze:  gcloud auth login"
        echo "  Oder setze GOOGLE_APPLICATION_CREDENTIALS=/pfad/key.json"
        return 0  # nicht fatal, gcloud login reicht
    fi
    if [[ ! -f "$GOOGLE_APPLICATION_CREDENTIALS" ]]; then
        echo -e "\033[0;31m[FEHLER]\033[0m GCP-Key-Datei nicht gefunden: $GOOGLE_APPLICATION_CREDENTIALS"
        return 1
    fi
    echo -e "\033[0;32m[OK]\033[0m    GCP-Credentials gesetzt ($GOOGLE_APPLICATION_CREDENTIALS)"
}

check_azure_creds() {
    if [[ -n "$AZURE_CLIENT_ID" && -n "$AZURE_TENANT_ID" && -n "$AZURE_CLIENT_SECRET" ]]; then
        echo -e "\033[0;32m[OK]\033[0m    Azure-Service-Principal gesetzt"
    else
        echo -e "\033[0;33m[HINWEIS]\033[0m Azure-Service-Principal nicht gesetzt"
        echo "  Nutze:  az login"
        echo "  Oder setze AZURE_CLIENT_ID + AZURE_TENANT_ID + AZURE_CLIENT_SECRET"
    fi
}

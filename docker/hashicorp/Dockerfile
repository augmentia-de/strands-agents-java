FROM hashicorp/vault:1.18

# Install dependencies for the setup script
RUN apk add --no-cache bash curl jq

# Create config and data directories
RUN mkdir -p /vault/config /vault/file /vault/logs && \
    chown -R vault:vault /vault

EXPOSE 8200

# Default command remains the vault server
ENTRYPOINT ["docker-entrypoint.sh"]
CMD ["server", "-config=/vault/config/vault.json"]

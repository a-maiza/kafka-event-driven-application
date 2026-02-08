#!/usr/bin/env bash
#
# Deploy JDBC Sink connector to Kafka Connect.
# Usage: ./connect/deploy-connectors.sh
#
# Prerequisites:
#   docker compose up -d   (infrastructure must be running)
#
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Waiting for Kafka Connect to be ready..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  sleep 2
done
echo "Kafka Connect is ready."

echo ""
echo "Deploying JDBC Sink connector (order-status -> PostgreSQL)..."
curl -sf -X PUT \
  "${CONNECT_URL}/connectors/jdbc-sink-order-status/config" \
  -H "Content-Type: application/json" \
  -d "$(jq '.config' "${SCRIPT_DIR}/jdbc-sink-order-status.json")" \
  | jq .

echo ""
echo "Connector deployed. Verifying status..."
sleep 2
curl -sf "${CONNECT_URL}/connectors/jdbc-sink-order-status/status" | jq .

echo ""
echo "Done. Events on order-status.v1 will be written to the 'order_status' table in PostgreSQL."
echo "Verify with: docker exec postgres psql -U app -d orders -c 'SELECT * FROM order_status;'"

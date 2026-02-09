#!/usr/bin/env bash
#
# Deploy Kafka Connect connectors:
#   1. JDBC Sink  — order-status.v1 -> PostgreSQL order_status table
#   2. Debezium   — PostgreSQL order_status table -> cdc.public.order_status topic
#
# Usage: ./connect/deploy-connectors.sh
#
# Prerequisites:
#   docker compose up -d --build   (infrastructure must be running)
#
set -euo pipefail

CONNECT_URL="${CONNECT_URL:-http://localhost:8083}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "Waiting for Kafka Connect to be ready..."
until curl -sf "${CONNECT_URL}/connectors" > /dev/null 2>&1; do
  sleep 2
done
echo "Kafka Connect is ready."

# --- 1. JDBC Sink connector ---
echo ""
echo "Deploying JDBC Sink connector (order-status.v1 -> PostgreSQL)..."
curl -sf -X PUT \
  "${CONNECT_URL}/connectors/jdbc-sink-order-status/config" \
  -H "Content-Type: application/json" \
  -d "$(jq '.config' "${SCRIPT_DIR}/jdbc-sink-order-status.json")" \
  | jq .

sleep 2
curl -sf "${CONNECT_URL}/connectors/jdbc-sink-order-status/status" | jq .

# --- 2. Debezium PostgreSQL Source connector ---
echo ""
echo "Deploying Debezium Source connector (PostgreSQL order_status -> cdc.public.order_status)..."
curl -sf -X PUT \
  "${CONNECT_URL}/connectors/debezium-source-order-status/config" \
  -H "Content-Type: application/json" \
  -d "$(jq '.config' "${SCRIPT_DIR}/debezium-source-order-status.json")" \
  | jq .

sleep 2
curl -sf "${CONNECT_URL}/connectors/debezium-source-order-status/status" | jq .

# --- Summary ---
echo ""
echo "All connectors deployed."
echo ""
echo "JDBC Sink:  order-status.v1 -> PostgreSQL order_status table"
echo "  Verify: docker exec postgres psql -U app -d orders -c 'SELECT * FROM order_status;'"
echo ""
echo "Debezium:   PostgreSQL order_status -> cdc.public.order_status topic"
echo "  Verify: docker exec broker-1 kafka-console-consumer --bootstrap-server localhost:29091 --topic cdc.public.order_status --from-beginning --max-messages 1"

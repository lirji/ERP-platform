#!/usr/bin/env bash
set -Eeuo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TASK_URL="${ERP_BASE_URL:-http://127.0.0.1:8500}"
curl --fail --silent --show-error "${TASK_URL}/actuator/health"
python3 "${TASK_ROOT}/scripts/check-business-health.py" --url "${TASK_URL}"

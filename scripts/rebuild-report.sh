#!/usr/bin/env bash
# 重建快照；只读业务表、写报表投影。ERP_DB_* 显式环境变量优先于 .env 默认值。
set -euo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ "${1:-}" =~ ^[1-9][0-9]*$ ]] || { echo '用法: rebuild-report.sh TENANT INVENTORY|PURCHASE|SALES|AR|AP [JOB_ID]' >&2; exit 2; }
case "${2:-}" in INVENTORY|PURCHASE|SALES|AR|AP) ;; *) echo '未知报表类型' >&2; exit 2;; esac
[[ "${3:-0}" =~ ^[0-9]+$ ]] || { echo 'JOB_ID 必须为非负整数' >&2; exit 2; }
if [[ -f "${TASK_ROOT}/.env" ]]; then
  while IFS='=' read -r key value; do
    [[ "${key}" =~ ^ERP_[A-Z0-9_]+$ ]] || continue
    [[ -n "${!key:-}" ]] || export "${key}=${value}"
  done < "${TASK_ROOT}/.env"
fi
TASK_JAR="${TASK_ROOT}/erp-app/target/erp-app-0.1.0-SNAPSHOT.jar"
[[ -f "${TASK_JAR}" ]] || { echo '请先运行 mvn -DskipTests package' >&2; exit 2; }
exec java -jar "${TASK_JAR}" --spring.main.web-application-type=none \
  --erp.outbox.scheduling-enabled=false --erp.reporting.cli=true \
  "--erp.reporting.tenant=$1" "--erp.reporting.kind=$2" "--erp.reporting.job=${3:-0}"

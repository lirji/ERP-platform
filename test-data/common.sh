#!/usr/bin/env bash
set -euo pipefail
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
if [[ -f "${PROJECT_ROOT}/.env" ]]; then
  while IFS='=' read -r key value; do
    [[ "${key}" =~ ^ERP_[A-Z0-9_]+$ ]] || continue
    [[ -n "${!key:-}" ]] || export "${key}=${value}"
  done < "${PROJECT_ROOT}/.env"
fi
case "${ERP_ENV:-local}" in local|dev|test) ;; *) echo '演示工具仅允许 local/dev/test' >&2; exit 2;; esac
case "${ERP_DB_HOST:-127.0.0.1}" in 127.0.0.1|localhost|::1|postgres) ;; *) echo '演示工具仅允许已有本地 PostgreSQL' >&2; exit 2;; esac
case "${ERP_DB_NAME:-erp}" in *prod*|*production*|*online*) echo '拒绝在生产命名数据库运行演示工具' >&2; exit 2;; esac
: "${ERP_DB_PASSWORD:?请配置已有本地数据库 ERP_DB_PASSWORD}"
command -v mvn >/dev/null
run_seed() {
  (cd "${PROJECT_ROOT}" && ERP_SEED_ACTION="$1" mvn -q -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false \
    -Dit.test=DemoDataSeed -Dfailsafe.failIfNoSpecifiedTests=false verify)
}

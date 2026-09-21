#!/usr/bin/env bash
# 库存对账：验证 INV-01（余额 == 流水代数和）与非负约束。
#
# 为什么要有独立对账脚本，而不是只靠测试：
# 测试验证的是代码在受控场景下的行为；对账验证的是**真实数据**此刻是否自洽。
# 二者不可互相替代——历史数据可能由更早的版本写入，或被人工干预过。
#
# 退出码：0 一致；1 发现不一致（可直接用于 CI 门禁）。
#
# 用法：
#   ./scripts/reconcile-inventory.sh                # 对 .env 指定的库
#   ERP_DB_NAME=erp_it ./scripts/reconcile-inventory.sh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# .env 只作为**默认值**：调用方在环境里显式设置的变量优先。
# 用 `set -a; . .env` 会反过来让 .env 覆盖调用方的设置，
# 于是 `ERP_DB_NAME=erp_it ./reconcile.sh` 会静默连到 .env 里的那个库——
# 对账脚本连错库却报"通过"，是比报错危险得多的失败方式。
if [[ -f "${ROOT_DIR}/.env" ]]; then
  while IFS='=' read -r key value; do
    [[ "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]] || continue
    [[ -n "${!key:-}" ]] || export "${key}=${value}"
  done < <(grep -vE '^[[:space:]]*(#|$)' "${ROOT_DIR}/.env")
fi

DB_NAME="${ERP_DB_NAME:-erp}"
DB_USER="${ERP_DB_USER:-erp}"
DB_PASSWORD="${ERP_DB_PASSWORD:?未设置 ERP_DB_PASSWORD}"
DB_HOST="${ERP_DB_HOST:-127.0.0.1}"
DB_PORT="${ERP_DB_PORT:-45532}"

if command -v psql >/dev/null 2>&1; then
  run_sql() { PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -tA -v ON_ERROR_STOP=1 -c "$1"; }
elif docker ps --format '{{.Names}}' | grep -qx erp-postgres; then
  run_sql() { docker exec -i -e PGPASSWORD="${DB_PASSWORD}" erp-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -tA -v ON_ERROR_STOP=1 -c "$1"; }
else
  echo "错误：找不到 psql，也没有运行中的 erp-postgres 容器" >&2; exit 2
fi

echo "==> 库存对账 database=${DB_NAME}"
FAILED=0

# ---- INV-01：余额 == 该桶流水代数和 ----
MISMATCH=$(run_sql "
SELECT count(*) FROM inv_balance b
WHERE b.on_hand <> COALESCE((
    SELECT SUM(t.signed_quantity) FROM inv_transaction t
    WHERE t.tenant_id = b.tenant_id AND t.company_id = b.company_id
      AND t.warehouse_id = b.warehouse_id AND t.location_id = b.location_id
      AND t.sku_id = b.sku_id AND t.batch_no = b.batch_no), 0);")
echo "    INV-01 余额 != 流水代数和 的桶数：${MISMATCH}"
if [[ "${MISMATCH}" != "0" ]]; then
  FAILED=1
  echo "    --- 不一致明细（前 20 条）---"
  run_sql "
  SELECT b.tenant_id||' wh='||b.warehouse_id||' sku='||b.sku_id||' batch='||b.batch_no
         ||' on_hand='||b.on_hand||' ledger='||COALESCE((
      SELECT SUM(t.signed_quantity) FROM inv_transaction t
      WHERE t.tenant_id=b.tenant_id AND t.company_id=b.company_id
        AND t.warehouse_id=b.warehouse_id AND t.location_id=b.location_id
        AND t.sku_id=b.sku_id AND t.batch_no=b.batch_no),0)
  FROM inv_balance b
  WHERE b.on_hand <> COALESCE((
      SELECT SUM(t.signed_quantity) FROM inv_transaction t
      WHERE t.tenant_id=b.tenant_id AND t.company_id=b.company_id
        AND t.warehouse_id=b.warehouse_id AND t.location_id=b.location_id
        AND t.sku_id=b.sku_id AND t.batch_no=b.batch_no),0)
  LIMIT 20;" | sed 's/^/      /'
fi

# ---- 非负与预占上界 ----
NEG=$(run_sql "SELECT count(*) FROM inv_balance WHERE on_hand < 0 OR reserved < 0 OR locked < 0;")
echo "    负数量的桶数：${NEG}"
[[ "${NEG}" == "0" ]] || FAILED=1

OVER=$(run_sql "SELECT count(*) FROM inv_balance WHERE reserved + locked > on_hand;")
echo "    预占+锁定 超过在库 的桶数：${OVER}"
[[ "${OVER}" == "0" ]] || FAILED=1

# ---- 预占自洽：消耗+释放 不得超过预占总量 ----
RESV=$(run_sql "SELECT count(*) FROM inv_reservation WHERE consumed_qty + released_qty > reserved_qty;")
echo "    预占超额（消耗+释放 > 预占）的记录数：${RESV}"
[[ "${RESV}" == "0" ]] || FAILED=1

# ---- 流水必须有来源单据 ----
ORPHAN=$(run_sql "SELECT count(*) FROM inv_transaction WHERE source_doc_id IS NULL OR source_doc_id = '';")
echo "    无来源单据的流水条数：${ORPHAN}"
[[ "${ORPHAN}" == "0" ]] || FAILED=1

if [[ "${FAILED}" == "0" ]]; then
  echo "==> 对账通过"
else
  echo "==> 对账失败：存在不一致，见上方明细" >&2
fi
exit "${FAILED}"

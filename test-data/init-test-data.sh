#!/usr/bin/env bash
# 全链路测试数据初始化。
#
# 规范依据（全局开发规范 §三）：Mock / 演示数据必须通过种子脚本或 SQL 写入数据库，
# 前端一律从接口获取；禁止把 Mock 数据硬编码进页面或组件。
#
# 当前处于 P0：平台尚无业务表，因此本脚本只建立**可重复执行、可清理、可验证**的骨架，
# 各阶段在 seed/ 下追加自己的数据脚本。P11 时它需要覆盖：
#   租户→公司→组织→部门→员工→用户→角色→供应商→客户→商品→SKU→仓库→库位→库存
#   →采购→销售→应收→应付→付款→收款
#
# 用法：
#   ./test-data/init-test-data.sh seed     写入种子数据（可重复执行，幂等）
#   ./test-data/init-test-data.sh verify   校验数据是否符合预期
#   ./test-data/init-test-data.sh clean    清理种子数据
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
SEED_DIR="${SCRIPT_DIR}/seed"

# 读取 .env（不提交），缺省回退到 compose 默认值
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a; . "${ROOT_DIR}/.env"; set +a
fi
DB_HOST="${ERP_DB_HOST:-127.0.0.1}"
DB_PORT="${ERP_DB_PORT:-45532}"
DB_NAME="${ERP_DB_NAME:-erp}"
DB_USER="${ERP_DB_USER:-erp}"
DB_PASSWORD="${ERP_DB_PASSWORD:?未设置 ERP_DB_PASSWORD，请先从 .env.example 复制出 .env}"

# 优先用本机 psql；没有则借用 compose 中的 postgres 容器，避免强制安装客户端
if command -v psql >/dev/null 2>&1; then
  run_sql() { PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 "$@"; }
elif docker ps --format '{{.Names}}' | grep -qx erp-postgres; then
  run_sql() { docker exec -i -e PGPASSWORD="${DB_PASSWORD}" erp-postgres psql -U "${DB_USER}" -d "${DB_NAME}" -v ON_ERROR_STOP=1 "$@"; }
else
  echo "错误：既找不到 psql，也没有运行中的 erp-postgres 容器。" >&2
  echo "请先执行：docker compose -f deploy/compose.yaml --env-file .env up -d" >&2
  exit 1
fi

seed() {
  echo "==> 写入种子数据（可重复执行）"
  shopt -s nullglob
  local files=("${SEED_DIR}"/*.sql)
  if (( ${#files[@]} == 0 )); then
    echo "    P0：尚无业务表，seed/ 下暂无脚本——这是预期状态，不是失败。"
    return 0
  fi
  # 按文件名排序保证依赖顺序（主数据先于单据）
  for f in $(printf '%s\n' "${files[@]}" | sort); do
    echo "    - $(basename "$f")"
    run_sql -f "$f" >/dev/null
  done
  echo "==> 完成"
}

verify() {
  echo "==> 校验"
  # P0 唯一可验证的事实：迁移已执行且基线表存在
  local n
  n="$(run_sql -tAc "SELECT count(*) FROM flyway_schema_history WHERE success")"
  echo "    已成功执行的迁移数：${n}"
  [[ "${n}" -ge 1 ]] || { echo "    失败：没有任何迁移被执行" >&2; return 1; }
  run_sql -tAc "SELECT to_regclass('erp_outbox_message') IS NOT NULL" | grep -qx t \
    || { echo "    失败：基线表 erp_outbox_message 不存在" >&2; return 1; }
  echo "==> 校验通过"
}

clean() {
  echo "==> 清理种子数据"
  # 只清种子数据，绝不 DROP SCHEMA：误删开发者本地正在调试的数据代价太高
  shopt -s nullglob
  local files=("${SEED_DIR}"/*.down.sql)
  if (( ${#files[@]} == 0 )); then
    echo "    P0：无种子数据可清理。"
    return 0
  fi
  for f in $(printf '%s\n' "${files[@]}" | sort -r); do
    echo "    - $(basename "$f")"
    run_sql -f "$f" >/dev/null
  done
  echo "==> 完成"
}

case "${1:-seed}" in
  seed)   seed   ;;
  verify) verify ;;
  clean)  clean  ;;
  *) echo "用法：$0 {seed|verify|clean}" >&2; exit 2 ;;
esac

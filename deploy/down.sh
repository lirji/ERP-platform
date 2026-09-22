#!/usr/bin/env bash
# 停止本项目服务，保留数据库卷；不删除任何共享 dev_infra 组件。
set -Eeuo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
[[ $# -eq 0 ]] || { echo '本脚本不删除卷；如需清库请走单独的数据处置流程' >&2; exit 2; }
docker compose -f "${TASK_ROOT}/deploy/compose.yaml" --env-file "${TASK_ROOT}/.env" down

#!/usr/bin/env bash
# 构建并启动现有本地拓扑；等待真实数据库和应用健康，超时返回失败。
set -Eeuo pipefail
TASK_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null
[[ -f "${TASK_ROOT}/.env" ]] || { echo '请从 .env.example 创建本地 .env 并设置数据库凭据' >&2; exit 2; }
docker compose -f "${TASK_ROOT}/deploy/compose.yaml" --env-file "${TASK_ROOT}/.env" config --quiet
docker compose -f "${TASK_ROOT}/deploy/compose.yaml" --env-file "${TASK_ROOT}/.env" up -d --build --wait --wait-timeout 300

CREATE TABLE erp_http_command (
 tenant_id BIGINT NOT NULL,
 endpoint VARCHAR(200) NOT NULL,
 command_key UUID NOT NULL,
 actor_id BIGINT NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 response_json TEXT,
 http_status INTEGER NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (tenant_id, endpoint, command_key)
);
COMMENT ON TABLE erp_http_command IS 'HTTP命令去重记录，与业务效果同事务提交；不单独提交处理中状态';
COMMENT ON COLUMN erp_http_command.tenant_id IS '租户隔离与幂等作用域';
COMMENT ON COLUMN erp_http_command.endpoint IS '规范方法与资源路径';
COMMENT ON COLUMN erp_http_command.command_key IS '客户端UUID命令标识';
COMMENT ON COLUMN erp_http_command.actor_id IS '首次提交人，其他身份不得重放响应';
COMMENT ON COLUMN erp_http_command.request_hash IS '规范JSON的SHA256摘要，不记录原始输入';
COMMENT ON COLUMN erp_http_command.response_json IS '首次业务响应；与效果同事务提交';
COMMENT ON COLUMN erp_http_command.http_status IS '首次HTTP状态，重放保持';
COMMENT ON COLUMN erp_http_command.created_at IS '创建时间；保留策略变更前不得任意删除去重记录';

-- =====================================================================
-- V1 基线：仅建立平台级公共结构，不含任何业务表。
-- 业务表由各模块在 P1 起按 V2xx__<module>_*.sql 顺序加入。
--
-- 开发规范 §一：任何 CREATE TABLE 必须同时写表注释与每一个字段注释。
-- 本文件是该规范在本项目的第一个落地样例，后续迁移一律照此执行。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 事务性 Outbox：与业务写入同库同事务落地，投递由独立调度器完成。
-- 为什么需要它：MVP 不引入 MQ（TECH_SELECTION 否决 Kafka/RabbitMQ），
-- 但"提交后可靠触发下游"仍需保证。Outbox 让事件与业务数据共享同一个本地事务，
-- 避免"业务提交成功但事件丢失"或"事件已发但业务回滚"。
-- ---------------------------------------------------------------------
CREATE TABLE erp_outbox_message (
    id              BIGSERIAL   PRIMARY KEY,
    tenant_id       BIGINT       NOT NULL,
    aggregate_type  VARCHAR(64)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

COMMENT ON TABLE  erp_outbox_message IS '事务性 Outbox 消息表：与业务写入同事务落库，由调度器异步投递，保证提交后可靠触发下游';
COMMENT ON COLUMN erp_outbox_message.id             IS '主键，自增';
COMMENT ON COLUMN erp_outbox_message.tenant_id      IS '租户 ID；多租户共享库 + 强制过滤（假设 A-10）';
COMMENT ON COLUMN erp_outbox_message.aggregate_type IS '聚合类型，如 PurchaseReceipt / Shipment；用于消费方路由';
COMMENT ON COLUMN erp_outbox_message.aggregate_id   IS '聚合实例 ID；与 aggregate_type 共同定位来源业务对象';
COMMENT ON COLUMN erp_outbox_message.event_type     IS '事件类型，如 PurchaseReceiptPosted；对应 Event Catalog 中的契约名';
COMMENT ON COLUMN erp_outbox_message.payload        IS '事件载荷 JSON；用 JSONB 以便按字段检索与排障';
COMMENT ON COLUMN erp_outbox_message.status         IS '投递状态：PENDING 待投递 / PUBLISHED 已投递 / DEAD 进入死信';
COMMENT ON COLUMN erp_outbox_message.retry_count    IS '已重试次数；配合 next_retry_at 实现有上限的退避重试';
COMMENT ON COLUMN erp_outbox_message.next_retry_at  IS '下次重试时间；NULL 表示可立即投递';
COMMENT ON COLUMN erp_outbox_message.last_error     IS '最近一次投递失败的错误摘要，供排障';
COMMENT ON COLUMN erp_outbox_message.created_at     IS '创建时间（入库即业务提交时间）';
COMMENT ON COLUMN erp_outbox_message.published_at   IS '成功投递时间；未投递为 NULL';

-- 投递扫描的主路径：只关心待投递且到期的消息，按创建顺序取
CREATE INDEX idx_outbox_pending ON erp_outbox_message (status, next_retry_at, created_at)
    WHERE status = 'PENDING';

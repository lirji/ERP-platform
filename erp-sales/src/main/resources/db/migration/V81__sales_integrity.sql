-- P5 业务完整性补充；不修改可能已经执行的 V80。
ALTER TABLE sal_order ADD COLUMN credit_approval_id BIGINT;
COMMENT ON COLUMN sal_order.credit_approval_id IS '信用超限放行审批实例；租户内只能用于一张订单，取消后也不得复用';
CREATE UNIQUE INDEX uk_sal_credit_approval ON sal_order (tenant_id, credit_approval_id);
ALTER TABLE sal_order ADD CONSTRAINT ck_sal_amount CHECK (total_amount >= 0);
ALTER TABLE sal_order_line ADD CONSTRAINT ck_sal_price CHECK (unit_price >= 0);
ALTER TABLE sal_shipment ADD CONSTRAINT ck_sal_sign_after_delivery CHECK (signed_at IS NULL OR delivered_at IS NOT NULL);

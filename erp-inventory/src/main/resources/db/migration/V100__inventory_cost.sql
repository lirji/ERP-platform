-- 扩展迁移：既有非空库存没有可信金额，显式保留未知，不伪造历史成本。
ALTER TABLE inv_balance ADD COLUMN inventory_value numeric(24,6);
COMMENT ON COLUMN inv_balance.inventory_value IS '本位币账面成本；NULL 表示成本未知；以库存桶移动加权';
UPDATE inv_balance SET inventory_value = 0 WHERE on_hand = 0;
ALTER TABLE inv_balance ALTER COLUMN inventory_value SET DEFAULT 0;
ALTER TABLE inv_balance ADD CONSTRAINT ck_inv_cost_nonnegative CHECK (inventory_value >= 0);
ALTER TABLE inv_transaction ADD COLUMN signed_value numeric(24,6);
COMMENT ON COLUMN inv_transaction.signed_value IS '本次过账成本变动；入正出负；NULL 表示历史成本依据不足';

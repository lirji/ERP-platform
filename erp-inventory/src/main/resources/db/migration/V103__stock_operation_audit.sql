ALTER TABLE inv_stock_operation ADD COLUMN reason_code varchar(24);
ALTER TABLE inv_stock_operation ADD COLUMN created_by bigint;
COMMENT ON COLUMN inv_stock_operation.reason_code IS '作业原因码：调拨、盘点、损坏、盘盈或纠错';
COMMENT ON COLUMN inv_stock_operation.created_by IS '创建操作人；早期未记录数据保持未知';
UPDATE inv_stock_operation SET reason_code = CASE WHEN kind='ADJUST' THEN 'CORRECTION' ELSE kind END;
ALTER TABLE inv_stock_operation ALTER COLUMN reason_code SET NOT NULL;
ALTER TABLE inv_stock_operation ADD CONSTRAINT ck_inv_operation_reason CHECK
 ((kind='TRANSFER' AND reason_code='TRANSFER') OR (kind='COUNT' AND reason_code='COUNT')
 OR (kind='ADJUST' AND reason_code IN ('DAMAGE','FOUND','CORRECTION')));

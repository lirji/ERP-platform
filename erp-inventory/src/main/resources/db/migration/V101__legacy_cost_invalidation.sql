-- 新旧应用共存时，旧版只更新数量，不可让旧成本冒充新数量的完整成本。
-- 新版在同一事务内随后写回已计算成本；旧版写入则留为未知，清仓归零。
CREATE FUNCTION inv_invalidate_cost_on_quantity_change() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.on_hand IS DISTINCT FROM OLD.on_hand THEN
        NEW.inventory_value := CASE WHEN NEW.on_hand = 0 THEN 0 ELSE NULL END;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER inv_cost_legacy_guard BEFORE UPDATE OF on_hand ON inv_balance
FOR EACH ROW EXECUTE FUNCTION inv_invalidate_cost_on_quantity_change();

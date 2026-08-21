SET @sql := (SELECT IF(COUNT(*)=0,'ALTER TABLE contract ADD COLUMN tenant_id BIGINT UNSIGNED DEFAULT NULL AFTER order_id','SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='contract' AND COLUMN_NAME='tenant_id');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(COUNT(*)=0,'ALTER TABLE contract ADD COLUMN factory_read TINYINT NOT NULL DEFAULT 0 AFTER buyer_sign','SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='contract' AND COLUMN_NAME='factory_read');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @sql := (SELECT IF(COUNT(*)=0,'ALTER TABLE contract ADD COLUMN factory_sign TEXT DEFAULT NULL AFTER factory_read','SELECT 1') FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='contract' AND COLUMN_NAME='factory_sign');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
UPDATE contract c
  LEFT JOIN work_stage w ON w.order_id = c.order_id AND w.deleted = 0
  SET c.tenant_id = w.tenant_id
  WHERE c.tenant_id IS NULL;
UPDATE contract SET tenant_id = 0 WHERE tenant_id IS NULL;
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='contract' AND INDEX_NAME='uk_order_factory');
SET @sql := IF(@idx=0, 'ALTER TABLE contract ADD UNIQUE KEY uk_order_factory (order_id, tenant_id)', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

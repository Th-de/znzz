-- 阶段 A 校正：取消原因 + 重发来源。可重复执行。
SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN cancel_reason VARCHAR(512) DEFAULT NULL COMMENT ''买家取消原因'' AFTER return_reason',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'cancel_reason'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN source_demand_id BIGINT UNSIGNED DEFAULT NULL COMMENT ''取消后重发时的来源需求'' AFTER cancel_reason',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'source_demand_id'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

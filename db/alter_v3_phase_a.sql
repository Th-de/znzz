-- 阶段 A：退回原因 + 意向截止。可重复执行。
SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN return_reason VARCHAR(512) DEFAULT NULL COMMENT ''运营退回原因'' AFTER extra_json',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'return_reason'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(
    COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN intention_end_at DATETIME DEFAULT NULL COMMENT ''意向期截止'' AFTER return_reason',
    'SELECT 1'
  )
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'intention_end_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

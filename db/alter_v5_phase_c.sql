SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN thinking_end_at DATETIME DEFAULT NULL COMMENT ''思考期截止'' AFTER intention_end_at',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'thinking_end_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE demand ADD COLUMN review_end_at DATETIME DEFAULT NULL COMMENT ''审核期截止'' AFTER thinking_end_at',
    'SELECT 1')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'demand' AND COLUMN_NAME = 'review_end_at'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

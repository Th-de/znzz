ALTER TABLE solution ADD COLUMN rationale_json TEXT NULL COMMENT 'AI/方案总述理由';

CREATE TABLE IF NOT EXISTS stage_progress_log (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stage_id      BIGINT NOT NULL,
  done_qty      INT NOT NULL,
  progress      INT NOT NULL,
  remark        VARCHAR(500) NOT NULL,
  attachment_id BIGINT DEFAULT NULL,
  created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_stage (stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单进度上报日志';

-- v14: 记录各阶段实际开始时间，便于列表展示（不只看截止）
ALTER TABLE demand ADD COLUMN published_at DATETIME NULL COMMENT '审核通过/进入意向期时间' AFTER locking_end_at;
ALTER TABLE demand ADD COLUMN factory_thinking_at DATETIME NULL COMMENT '进入工厂思考期时间' AFTER published_at;
ALTER TABLE demand ADD COLUMN buyer_thinking_at DATETIME NULL COMMENT '进入买家思考期时间' AFTER factory_thinking_at;

-- 存量回填：用截止时间倒推开始时间
UPDATE demand
SET published_at = DATE_SUB(intention_end_at, INTERVAL IFNULL(intention_days, 5) DAY)
WHERE published_at IS NULL AND intention_end_at IS NOT NULL;

UPDATE demand
SET published_at = created_at
WHERE published_at IS NULL
  AND status NOT IN ('DRAFT', 'PENDING_AUDIT', 'RETURNED');

UPDATE demand
SET factory_thinking_at = DATE_SUB(factory_thinking_end_at, INTERVAL 12 HOUR)
WHERE factory_thinking_at IS NULL AND factory_thinking_end_at IS NOT NULL;

UPDATE demand
SET buyer_thinking_at = DATE_SUB(buyer_thinking_end_at, INTERVAL 24 HOUR)
WHERE buyer_thinking_at IS NULL AND buyer_thinking_end_at IS NOT NULL;

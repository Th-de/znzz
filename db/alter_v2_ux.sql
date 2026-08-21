-- 已有库升级：意向期不报价 + 需求/资质扩展字段

ALTER TABLE demand
  ADD COLUMN inspect_mode VARCHAR(16) DEFAULT NULL COMMENT 'FAI/AQL/FULL' AFTER remark,
  ADD COLUMN general_tolerance VARCHAR(32) DEFAULT NULL COMMENT '一般公差标准' AFTER inspect_mode,
  ADD COLUMN part_revision VARCHAR(64) DEFAULT NULL COMMENT '图号/版本' AFTER general_tolerance,
  ADD COLUMN extra_json JSON DEFAULT NULL COMMENT 'Ra/热处理/年用量等' AFTER part_revision;

ALTER TABLE quotation
  ADD COLUMN extra_json JSON DEFAULT NULL COMMENT '意向报名快照' AFTER valid_days,
  MODIFY COLUMN intention_price DECIMAL(18,2) DEFAULT NULL COMMENT '已停用-意向期不再报价';

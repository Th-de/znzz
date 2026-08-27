-- v12: device daily capacity for AI evaluation
ALTER TABLE device
  ADD COLUMN daily_capacity INT NULL COMMENT '日产能（件/天），AI 产能核算依据' AFTER materials;

UPDATE device SET daily_capacity = CASE
  WHEN name LIKE '%炉%' OR name LIKE '%热处理%' THEN 80
  WHEN name LIKE '%磨%' THEN 150
  WHEN name LIKE '%车%' THEN 200
  WHEN name LIKE '%铣%' OR name LIKE '%加工中心%' THEN 180
  ELSE 120
END
WHERE daily_capacity IS NULL;

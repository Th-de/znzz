-- v13: AI-driven flow redesign
-- demand: factory/buyer thinking stage deadlines, delivery phases, estimated total, buyer deposit
ALTER TABLE demand ADD COLUMN factory_thinking_end_at DATETIME NULL COMMENT '工厂思考期截止时间' AFTER intention_end_at;
ALTER TABLE demand ADD COLUMN buyer_thinking_end_at DATETIME NULL COMMENT '买家思考期截止时间' AFTER factory_thinking_end_at;
ALTER TABLE demand ADD COLUMN delivery_times INT NULL DEFAULT 1 COMMENT '分期交付次数（买家发布时定）' AFTER packaging;
ALTER TABLE demand ADD COLUMN delivery_plan_json TEXT NULL COMMENT '每期交付要求（买家填，JSON数组）' AFTER delivery_times;
ALTER TABLE demand ADD COLUMN estimated_total DECIMAL(14,2) NULL COMMENT '预估总价（买家保证金计费基数）' AFTER delivery_plan_json;
ALTER TABLE demand ADD COLUMN buyer_deposit_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '买家保证金状态 NONE/FROZEN/DEDUCTED/RELEASED' AFTER estimated_total;

-- quotation: factory thinking-period submission
ALTER TABLE quotation ADD COLUMN unit_price DECIMAL(12,2) NULL COMMENT '单件报价（工厂思考期填）' AFTER intention_price;
ALTER TABLE quotation ADD COLUMN plan_text TEXT NULL COMMENT '实施方案（工厂思考期填）' AFTER stage_curve_json;
ALTER TABLE quotation ADD COLUMN delivery_plan_json TEXT NULL COMMENT '每期交付内容（工厂填，期数由买家定）' AFTER plan_text;

-- device: applicable processes for capacity linkage
ALTER TABLE device ADD COLUMN process_names VARCHAR(255) NULL COMMENT '适用工序（逗号分隔）' AFTER materials;

-- enterprise: factory introduction
ALTER TABLE enterprise ADD COLUMN introduction TEXT NULL COMMENT '企业介绍（工厂自填）' AFTER capability_json;

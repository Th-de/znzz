-- 一次性清测试业务数据并写入历史履约样例（与 ShowcaseHistorySeeder 对齐）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM survey;
DELETE FROM credit_event;
DELETE FROM fund_flow;
DELETE FROM inspection;
DELETE FROM stage_progress_log;
DELETE FROM work_stage;
DELETE FROM contract;
DELETE FROM `order`;
DELETE FROM solution;
DELETE FROM quotation;
DELETE FROM attachment;
DELETE FROM process;
DELETE FROM demand;
DELETE FROM notify;
DELETE FROM audit_log;

DELETE FROM device WHERE tenant_id IN (SELECT tenant_id FROM (SELECT tenant_id FROM sys_user WHERE phone = '13000000006') t);
DELETE FROM account WHERE tenant_id IN (SELECT tenant_id FROM (SELECT tenant_id FROM sys_user WHERE phone = '13000000006') t);
DELETE FROM sys_user WHERE phone = '13000000006';
DELETE FROM enterprise WHERE id NOT IN (SELECT tenant_id FROM sys_user) AND type = 'FACTORY' AND credit_score = 60;

UPDATE account a JOIN enterprise e ON e.id = a.tenant_id
SET a.balance = 1000000.00, a.frozen = 0.00 WHERE e.type <> 'PLATFORM';
UPDATE account a JOIN enterprise e ON e.id = a.tenant_id
SET a.balance = 0.00, a.frozen = 0.00 WHERE e.type = 'PLATFORM';

SET FOREIGN_KEY_CHECKS = 1;

DROP PROCEDURE IF EXISTS seed_showcase_order;
DELIMITER //
CREATE PROCEDURE seed_showcase_order(
  IN p_title VARCHAR(128),
  IN p_product VARCHAR(128),
  IN p_category VARCHAR(64),
  IN p_material VARCHAR(128),
  IN p_qty INT,
  IN p_unit DECIMAL(12,2),
  IN p_factory_phone VARCHAR(32),
  IN p_factory_name VARCHAR(128),
  IN p_process_name VARCHAR(64),
  IN p_requirement VARCHAR(512),
  IN p_tolerance VARCHAR(64),
  IN p_inspect_mode VARCHAR(16),
  IN p_inspect_unit DECIMAL(18,2),
  IN p_t0 DATETIME,
  IN p_late TINYINT
)
BEGIN
  DECLARE v_buyer BIGINT;
  DECLARE v_factory BIGINT;
  DECLARE v_platform BIGINT;
  DECLARE v_inspector BIGINT;
  DECLARE v_demand BIGINT;
  DECLARE v_quote BIGINT;
  DECLARE v_solution BIGINT;
  DECLARE v_order BIGINT;
  DECLARE v_stage BIGINT;
  DECLARE v_insp BIGINT;
  DECLARE v_total DECIMAL(18,2);
  DECLARE v_deposit DECIMAL(18,2);
  DECLARE v_commission DECIMAL(18,2);
  DECLARE v_inspect_fee DECIMAL(18,2);
  DECLARE v_wages DECIMAL(18,2);
  DECLARE v_hard DATE;
  DECLARE v_think DATETIME;
  DECLARE v_buyer_think DATETIME;
  DECLARE v_signed DATETIME;
  DECLARE v_deliver DATETIME;
  DECLARE v_inspect DATETIME;
  DECLARE v_pay DATETIME;
  DECLARE v_done DATETIME;
  DECLARE v_combo JSON;
  DECLARE v_full_title VARCHAR(128);

  SET v_buyer = (SELECT tenant_id FROM sys_user WHERE phone = '13000000001' LIMIT 1);
  SET v_factory = (SELECT tenant_id FROM sys_user WHERE phone = p_factory_phone LIMIT 1);
  SET v_platform = (SELECT id FROM enterprise WHERE type = 'PLATFORM' LIMIT 1);
  SET v_inspector = (SELECT tenant_id FROM sys_user WHERE phone IN ('inspect01','123456') AND role = 'INSPECTION' ORDER BY id DESC LIMIT 1);
  SET v_total = ROUND(p_unit * p_qty, 2);
  SET v_deposit = ROUND(v_total * 0.05, 2);
  SET v_commission = ROUND(v_total * 0.01, 2);
  SET v_inspect_fee = ROUND(p_inspect_unit * p_qty, 2);
  SET v_wages = v_total - v_commission;
  SET v_hard = DATE(p_t0) + INTERVAL 35 DAY;
  SET v_think = p_t0 + INTERVAL 3 DAY;
  SET v_buyer_think = p_t0 + INTERVAL 4 DAY;
  SET v_signed = p_t0 + INTERVAL 6 DAY;
  SET v_deliver = IF(p_late = 1, p_t0 + INTERVAL 38 DAY, p_t0 + INTERVAL 28 DAY);
  SET v_inspect = v_deliver + INTERVAL 1 DAY;
  SET v_pay = v_inspect + INTERVAL 8 HOUR;
  SET v_done = v_pay + INTERVAL 2 HOUR;
  SET v_full_title = p_title;
  SET v_combo = JSON_ARRAY(JSON_OBJECT(
    'factoryId', v_factory, 'factoryName', p_factory_name, 'processNo', 1,
    'quantity', p_qty, 'unitPrice', p_unit, 'price', v_total));

  INSERT INTO demand (tenant_id, title, product_name, category, quantity, material, tolerance,
    surface_treatment, aql, certification, min_yield, min_credit_score, deadline_hard,
    delivery_address, packaging, multi_process, weight_json, intention_days, remark,
    inspect_mode, inspect_price, general_tolerance, part_revision, extra_json, delivery_times, estimated_total,
    buyer_deposit_status, status, published_at, factory_thinking_at, factory_thinking_end_at,
    buyer_thinking_at, buyer_thinking_end_at, created_at, updated_at, deleted)
  VALUES (v_buyer, v_full_title, p_product, p_category, p_qty, p_material, p_tolerance,
    '防锈油', '1.0', 'ISO9001,IATF16949', 0.9800, 65, v_hard,
    '杭州市余杭区仓前街道文一西路 1500 号 精工传动成品库', '防锈油封+隔层纸+木箱', 0,
    CAST('{"cost":0.34,"time":0.33,"quality":0.33}' AS JSON), 5, '按图纸一次交检，包装防锈后送精工传动成品库。',
    p_inspect_mode, p_inspect_unit, 'ISO 2768-m', CONCAT(p_product, '-A'), CAST('{"roughness":"Ra1.6"}' AS JSON), 1, v_total,
    'RELEASED', 'COMPLETED', p_t0, v_think, v_think + INTERVAL 24 HOUR,
    v_buyer_think, v_buyer_think + INTERVAL 24 HOUR, p_t0 - INTERVAL 1 DAY, v_done, 0);
  SET v_demand = LAST_INSERT_ID();

  INSERT INTO process (demand_id, process_no, process_name, requirement, created_at)
  VALUES (v_demand, 1, p_process_name, p_requirement, p_t0);

  INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
    min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
  VALUES (v_factory, v_demand, 1, p_unit, v_total, 0.9850, 25, FLOOR(p_qty/2), p_qty,
    '按图纸一次装夹完成，过程检验每 50 件抽 3 件，交期按硬节点倒排。', 'RELEASED', 'RELEASED', 'WIN', v_think, v_think, 0);
  SET v_quote = LAST_INSERT_ID();

  INSERT INTO solution (demand_id, type, suggested_combo_json, final_combo_json, source, is_final,
    score, status, rationale_json, created_at)
  VALUES (v_demand, 'AI1', v_combo, v_combo, 'FINAL', 1, 88.5, 'ACTIVE',
    '{"summary":"该厂工序匹配、交期与质量门槛均满足。"}', v_buyer_think);
  SET v_solution = LAST_INSERT_ID();

  INSERT INTO `order` (demand_id, solution_id, total_amount, commission_rate, commission_amount,
    status, created_at, updated_at, deleted)
  VALUES (v_demand, v_solution, v_total, 0.0100, v_commission, 'COMPLETED', v_signed, v_done, 0);
  SET v_order = LAST_INSERT_ID();

  INSERT INTO contract (order_id, tenant_id, version, status, buyer_read, factory_read, signed_at, created_at)
  VALUES (v_order, v_factory, 1, 'SIGNED', 1, 1, v_signed, v_signed - INTERVAL 1 DAY);

  INSERT INTO work_stage (order_id, tenant_id, process_no, process_name, quantity, promised_days,
    promised_date, amount, escrow_status, actual_progress, status, pay_amount, first_yield,
    inspect_round, first_quantity_ok, delivered_qty, period_no, period_start, period_end,
    inspect_fee_status, inspect_fee_amount, inspect_fee_payer, inspect_kind, fai_status,
    created_at, updated_at, deleted)
  VALUES (v_order, v_factory, 1, p_process_name, p_qty, 25, v_hard, v_total, 'SETTLED', 100, 'PASS',
    v_wages, 0.9850, 1, 1, p_qty, 1, v_signed + INTERVAL 1 DAY, v_deliver,
    'PAID', v_inspect_fee, 'BUYER', 'LOT', 'PASS', v_signed, v_done, 0);
  SET v_stage = LAST_INSERT_ID();

  INSERT INTO stage_progress_log (stage_id, done_qty, progress, remark, created_at)
  VALUES (v_stage, FLOOR(p_qty/2), 50, '中期进度：基准面与止口已完成', v_deliver - INTERVAL 10 DAY),
         (v_stage, p_qty, 100, '全部完工，已送检', v_deliver);

  INSERT INTO inspection (stage_id, inspector_tenant_id, result, report_json, status, created_at)
  VALUES (v_stage, v_inspector, 'PASS', JSON_OBJECT(
    'sampleCount', LEAST(80, p_qty), 'failCount', 0, 'criticalFailCount', 0, 'generalFailCount', 0,
    'deliveredQty', p_qty, 'quantityOk', TRUE, 'toleranceOk', TRUE, 'actualYield', 0.985,
    'inspectMode', p_inspect_mode, 'remark', '尺寸与外观均满足图纸'), 'VALID', v_inspect);
  SET v_insp = LAST_INSERT_ID();

  INSERT INTO fund_flow (order_id, demand_id, tenant_id, type, direction, amount, status, idempotent_no, created_at) VALUES
    (NULL, v_demand, v_factory, 'INTENTION', 'FREEZE', 1000.00, 'SUCCESS', CONCAT('SHOW-INT-F-', v_quote), v_think),
    (NULL, v_demand, v_factory, 'INTENTION', 'UNFREEZE', 1000.00, 'SUCCESS', CONCAT('SHOW-INT-U-', v_quote), v_think + INTERVAL 2 HOUR),
    (NULL, v_demand, v_factory, 'DEPOSIT', 'FREEZE', v_deposit, 'SUCCESS', CONCAT('SHOW-DEP-F-', v_quote), v_think + INTERVAL 2 HOUR),
    (NULL, v_demand, v_buyer, 'BUYER_DEPOSIT', 'FREEZE', v_deposit, 'SUCCESS', CONCAT('SHOW-BDEP-F-', v_demand), v_buyer_think),
    (v_order, v_demand, v_buyer, 'INSPECT_FEE', 'OUT', v_inspect_fee, 'SUCCESS', CONCAT('SHOW-INSP-OUT-', v_stage), v_inspect - INTERVAL 2 HOUR),
    (v_order, v_demand, v_platform, 'INSPECT_FEE', 'IN', v_inspect_fee, 'SUCCESS', CONCAT('SHOW-INSP-IN-', v_stage), v_inspect - INTERVAL 2 HOUR),
    (v_order, v_demand, v_buyer, 'ESCROW', 'OUT', v_total, 'SUCCESS', CONCAT('SHOW-ESC-BO-', v_stage), v_pay),
    (v_order, v_demand, v_platform, 'ESCROW', 'IN', v_total, 'SUCCESS', CONCAT('SHOW-ESC-PI-', v_stage), v_pay),
    (v_order, v_demand, v_platform, 'ESCROW', 'OUT', v_wages, 'SUCCESS', CONCAT('SHOW-ESC-PO-', v_order, '-', v_factory), v_done),
    (v_order, v_demand, v_factory, 'PAYMENT', 'IN', v_wages, 'SUCCESS', CONCAT('SHOW-PAY-', v_order, '-', v_factory), v_done),
    (v_order, v_demand, v_platform, 'COMMISSION', 'IN', v_commission, 'SUCCESS', CONCAT('SHOW-COM-', v_order), v_done),
    (v_order, v_demand, v_factory, 'DEPOSIT', 'UNFREEZE', v_deposit, 'SUCCESS', CONCAT('SHOW-DEP-U-', v_quote), v_done),
    (v_order, v_demand, v_buyer, 'BUYER_DEPOSIT', 'UNFREEZE', v_deposit, 'SUCCESS', CONCAT('SHOW-BDEP-U-', v_demand), v_done);

  INSERT INTO credit_event (tenant_id, type, score_change, ref_type, ref_id, remark, created_at) VALUES
    (v_factory, IF(p_late=1,'PUNCTUAL_LATE','PUNCTUAL_ON'), IF(p_late=1,-1,1), 'STAGE', v_stage, IF(p_late=1,'超过硬交期交付','按硬交期交付'), v_deliver),
    (v_factory, 'QUALITY_PASS', 2, 'INSPECTION', v_insp, '批次质检合格', v_inspect),
    (v_buyer, 'PAY_ON_TIME', 1, 'STAGE', v_stage, '质检通过后 48 小时内完成阶段托管', v_pay),
    (v_factory, 'ORDER_COMPLETE', 2, 'ORDER', v_order, '整单完工且无质量失信', v_done);

  UPDATE account SET balance = balance - v_inspect_fee - v_total WHERE tenant_id = v_buyer;
  UPDATE account SET balance = balance + v_wages WHERE tenant_id = v_factory;
  UPDATE account SET balance = balance + v_inspect_fee + v_commission WHERE tenant_id = v_platform;

  INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES
    (v_buyer, v_demand, CONCAT('订单已完成#', v_demand), CONCAT('需求「', v_full_title, '」已结算，工厂工钱已到账。'), 1, v_done),
    (v_factory, v_demand, CONCAT('工钱已入账#', v_demand), CONCAT('需求「', v_full_title, '」已结算，工钱 ', v_wages, ' 元已入账。'), 1, v_done);
  INSERT INTO audit_log (actor_id, action, target_type, target_id, after_json, created_at)
  VALUES (6, '买家完工确认', 'ORDER', v_order, JSON_QUOTE(CONCAT('需求#', v_demand, '「', v_full_title, '」已结算')), v_done);
END //
DELIMITER ;

CALL seed_showcase_order('RV减速机针齿壳精车外协','RV针齿壳','机器人减速机零件','20CrMnTi',800,85.00,
  '13000000002','宁波博锐精密机械有限公司','精车','内齿圈基准圆跳动 0.01，齿面粗糙度 Ra0.8','圆跳动 0.01 / Ra0.8',
  'AQL',5.00,'2026-05-08 09:20:00',0);
CALL seed_showcase_order('液压阀块深孔与安装面精铣','液压阀块','工程机械液压件','45# 调质钢',600,42.00,
  '13000000003','台州宏达机械加工厂','精铣','深孔直线度 0.05/100，安装面平面度 0.02','直线度 0.05 / 平面度 0.02',
  'FULL',3.00,'2026-06-02 10:15:00',1);
CALL seed_showcase_order('传动法兰渗碳淬火热处理','传动法兰','汽车传动件','20CrMnTi',1200,18.50,
  '13000000004','嘉兴金盾热处理有限公司','热处理','渗碳层 0.8-1.2mm，表面硬度 58-62HRC','渗碳 0.8-1.2mm / 58-62HRC',
  'AQL',5.00,'2026-06-20 14:00:00',0);
CALL seed_showcase_order('铝合金电机端盖精铣阳极氧化','电机端盖','新能源电机结构件','ADC12 压铸铝',500,36.00,
  '13000000005','苏州汇通智能制造有限公司','精铣','止口同轴度 0.03，氧化膜 8-12μm','同轴度 0.03 / 氧化 8-12μm',
  'AQL',5.00,'2026-07-10 11:30:00',0);

DROP PROCEDURE IF EXISTS seed_showcase_order;

UPDATE enterprise SET credit_score = 82 WHERE id = (SELECT tenant_id FROM sys_user WHERE phone='13000000001' LIMIT 1);
UPDATE enterprise SET credit_score = 87 WHERE id = (SELECT tenant_id FROM sys_user WHERE phone='13000000002' LIMIT 1);
UPDATE enterprise SET credit_score = 73 WHERE id = (SELECT tenant_id FROM sys_user WHERE phone='13000000003' LIMIT 1);
UPDATE enterprise SET credit_score = 81 WHERE id = (SELECT tenant_id FROM sys_user WHERE phone='13000000004' LIMIT 1);
UPDATE enterprise SET credit_score = 79 WHERE id = (SELECT tenant_id FROM sys_user WHERE phone='13000000005' LIMIT 1);

-- 两条活单，供当前流程演示（与 DemoDataSeeder 标题一致，重启后端会跳过重复插入）
INSERT INTO demand (tenant_id, title, product_name, category, quantity, material, tolerance,
  surface_treatment, aql, certification, min_yield, min_credit_score, deadline_hard,
  delivery_address, packaging, multi_process, weight_json, intention_days, remark,
  inspect_mode, general_tolerance, part_revision, extra_json, status, intention_end_at, created_at, updated_at, deleted)
SELECT tenant_id, '20CrMnTi齿轮轴粗车-热处理-精磨外协', '齿轮轴', '汽车传动件', 1000, '20CrMnTi',
  '轴径 φ32h6 ±0.013，键槽对称度 0.02', '发黑防锈', '1.0', 'ISO9001,IATF16949', 0.97, 65,
  DATE_ADD(CURDATE(), INTERVAL 40 DAY), '杭州市余杭区仓前街道文一西路 1500 号 精工传动成品库',
  '防锈油封+隔层纸+木箱，每箱 20 件', 1, CAST('{"cost":0.34,"time":0.33,"quality":0.33}' AS JSON), 5,
  '多工序外协：粗车→热处理→精磨，请按工序分别报名。', 'AQL', 'ISO 2768-m', 'GEAR-SHAFT-001 / A',
  CAST('{"roughness":"Ra1.6","heatTreatment":"渗碳淬火 58-62HRC"}' AS JSON), 'PUBLISHED',
  DATE_ADD(NOW(), INTERVAL 5 DAY), NOW(), NOW(), 0
FROM sys_user WHERE phone='13000000001' LIMIT 1;
SET @d1 = LAST_INSERT_ID();
INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES
  (@d1, 1, '粗车', '留磨量 0.3mm，同轴度 0.05', NOW()),
  (@d1, 2, '热处理', '渗碳层 0.8-1.2mm，硬度 58-62HRC', NOW()),
  (@d1, 3, '精磨', 'φ32h6，Ra1.6，圆度 0.008', NOW());

INSERT INTO demand (tenant_id, title, product_name, category, quantity, material, tolerance,
  surface_treatment, aql, certification, min_yield, min_credit_score, deadline_hard,
  delivery_address, packaging, multi_process, weight_json, intention_days, remark,
  inspect_mode, general_tolerance, part_revision, extra_json, status, created_at, updated_at, deleted)
SELECT tenant_id, '铝合金壳体铣削精加工（待审）', '铝合金壳体', '精密结构件', 500, '6061-T6 铝合金',
  '外形 ±0.05，安装孔位置度 0.03', '本色阳极氧化', '1.5', 'ISO9001', 0.98, 60,
  DATE_ADD(CURDATE(), INTERVAL 25 DAY), '杭州市余杭区仓前街道文一西路 1500 号',
  '气泡袋+纸箱，防磕碰', 0, CAST('{"cost":0.4,"time":0.3,"quality":0.3}' AS JSON), 3,
  '单工序铣削精加工，待运营审核通过后对外发布。', 'AQL', 'ISO 2768-f', 'ALU-HSG-12 / B',
  CAST('{"roughness":"Ra1.6"}' AS JSON), 'PENDING_AUDIT', NOW(), NOW(), 0
FROM sys_user WHERE phone='13000000001' LIMIT 1;
SET @d2 = LAST_INSERT_ID();
INSERT INTO process (demand_id, process_no, process_name, requirement, created_at)
VALUES (@d2, 1, '精铣', '外形轮廓与安装面一次装夹完成，去毛刺', NOW());

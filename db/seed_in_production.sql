-- 插入一条已开工的三厂履约仿真单（utf8mb4）
SET NAMES utf8mb4;

SET @title = CONVERT('转向节毛坯粗车-热处理-精磨外协（履约中）' USING utf8mb4) COLLATE utf8mb4_unicode_ci;

SET @buyer = (SELECT tenant_id FROM sys_user WHERE phone='13000000001' LIMIT 1);
SET @f_fine = (SELECT tenant_id FROM sys_user WHERE phone='13000000002' LIMIT 1);
SET @f_rough = (SELECT tenant_id FROM sys_user WHERE phone='13000000003' LIMIT 1);
SET @f_heat = (SELECT tenant_id FROM sys_user WHERE phone='13000000004' LIMIT 1);
SET @buyer_uid = (SELECT id FROM sys_user WHERE phone='13000000001' LIMIT 1);
SET @n_fine = (SELECT name FROM enterprise WHERE id=@f_fine);
SET @n_rough = (SELECT name FROM enterprise WHERE id=@f_rough);
SET @n_heat = (SELECT name FROM enterprise WHERE id=@f_heat);

SET @old_did = (SELECT id FROM demand WHERE title=@title AND deleted=0 LIMIT 1);
SET @old_oid = (SELECT id FROM `order` WHERE demand_id=@old_did LIMIT 1);

DELETE pl FROM stage_progress_log pl
  JOIN work_stage ws ON ws.id=pl.stage_id WHERE ws.order_id=@old_oid;
DELETE FROM inspection WHERE stage_id IN (SELECT id FROM work_stage WHERE order_id=@old_oid);
DELETE FROM survey WHERE order_id=@old_oid;
DELETE FROM work_stage WHERE order_id=@old_oid;
DELETE FROM contract WHERE order_id=@old_oid;
DELETE FROM attachment WHERE biz_type='CONTRACT' AND biz_id=@old_oid;
DELETE FROM `order` WHERE id=@old_oid;
DELETE FROM solution WHERE demand_id=@old_did;
DELETE FROM quotation WHERE demand_id=@old_did;
DELETE FROM process WHERE demand_id=@old_did;
DELETE FROM attachment WHERE biz_type='DEMAND' AND biz_id=@old_did;
DELETE FROM fund_flow WHERE demand_id=@old_did;
DELETE FROM notify WHERE title LIKE CONCAT('%#', IFNULL(@old_did,0), '%') OR title LIKE CONCAT('%#', IFNULL(@old_oid,0), '%');
DELETE FROM demand WHERE id=@old_did;

INSERT INTO demand (
  tenant_id, title, product_name, category, quantity, material, tolerance, surface_treatment,
  aql, certification, min_yield, min_credit_score, deadline_hard, deadline_flexible,
  delivery_address, packaging, multi_process, weight_json, intention_days, remark,
  inspect_mode, general_tolerance, part_revision, extra_json, return_reason, cancel_reason,
  status, created_at, updated_at, deleted
) VALUES (
  @buyer,
  @title,
  '汽车转向节',
  '汽车底盘件',
  800,
  '42CrMo',
  '轴颈 φ42h6，安装面平行度 0.01',
  '发黑防锈',
  '1.0',
  'ISO9001,IATF16949',
  0.9700,
  65,
  DATE_ADD(CURDATE(), INTERVAL 28 DAY),
  DATE_ADD(CURDATE(), INTERVAL 35 DAY),
  '杭州市余杭区仓前街道文一西路 1500 号 精工传动成品库',
  '防锈油+隔层纸+木箱，每箱 10 件',
  1,
  '{"cost":0.34,"time":0.33,"quality":0.33}',
  5,
  '三厂串联外协：粗车→热处理→精磨。当前已开工，可在三端查看进度、待检与资金冻结。',
  'AQL',
  'ISO 2768-m',
  'KN-STG-08 / C',
  '{"roughness":"Ra0.8","heatTreatment":"渗碳淬火 58-62HRC","annualQty":5000}',
  '',
  '',
  'IN_PRODUCTION',
  DATE_SUB(NOW(), INTERVAL 18 DAY),
  NOW(),
  0
);
SET @did = LAST_INSERT_ID();

INSERT INTO process (demand_id, process_no, process_name, quantity, requirement) VALUES
  (@did, 1, '粗车', 800, '留磨量 0.35mm，同轴度 0.06，去飞边'),
  (@did, 2, '热处理', 800, '渗碳层 0.8-1.2mm，硬度 58-62HRC'),
  (@did, 3, '精磨', 800, '轴颈 φ42h6，Ra0.8，平行度 0.01');

SET @dev_rough = (SELECT id FROM device WHERE tenant_id=@f_rough AND deleted=0 ORDER BY id LIMIT 1);
SET @dev_heat = (SELECT id FROM device WHERE tenant_id=@f_heat AND deleted=0 ORDER BY id LIMIT 1);
SET @dev_fine = (SELECT id FROM device WHERE tenant_id=@f_fine AND deleted=0 ORDER BY id LIMIT 1);

INSERT INTO quotation (
  tenant_id, demand_id, process_no, price, yield_rate, promised_days, min_qty, max_qty,
  extra_json, device_ids_json, intention_status, deposit_status, status, version, created_at, updated_at, deleted
) VALUES
  (@f_rough, @did, 1, 18000.00, 0.9600, 10, 800, 800,
   '{"processNo":1,"yieldRate":0.96}', CONCAT('[', @dev_rough, ']'),
   'FROZEN', 'FROZEN', 'WIN', 1, DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0),
  (@f_heat, @did, 2, 12000.00, 0.9800, 8, 800, 800,
   '{"processNo":2,"yieldRate":0.98}', CONCAT('[', @dev_heat, ']'),
   'FROZEN', 'FROZEN', 'WIN', 1, DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0),
  (@f_fine, @did, 3, 28000.00, 0.9900, 15, 800, 800,
   '{"processNo":3,"yieldRate":0.99}', CONCAT('[', @dev_fine, ']'),
   'FROZEN', 'FROZEN', 'WIN', 1, DATE_SUB(NOW(), INTERVAL 16 DAY), NOW(), 0);

SET @qid_rough = (SELECT id FROM quotation WHERE demand_id=@did AND process_no=1);
SET @qid_heat = (SELECT id FROM quotation WHERE demand_id=@did AND process_no=2);
SET @qid_fine = (SELECT id FROM quotation WHERE demand_id=@did AND process_no=3);

SET @combo = CONCAT(
  '[{"processNo":1,"processName":"粗车","factoryId":', @f_rough,
  ',"factoryName":"', @n_rough, '","quantity":800,"price":18000,"days":10,"yieldRate":0.96},',
  '{"processNo":2,"processName":"热处理","factoryId":', @f_heat,
  ',"factoryName":"', @n_heat, '","quantity":800,"price":12000,"days":8,"yieldRate":0.98},',
  '{"processNo":3,"processName":"精磨","factoryId":', @f_fine,
  ',"factoryName":"', @n_fine, '","quantity":800,"price":28000,"days":15,"yieldRate":0.99}]'
);

INSERT INTO solution (
  demand_id, type, suggested_combo_json, final_combo_json, source, is_final, score, status, rationale_json, created_at
) VALUES (
  @did, 'A', @combo, @combo, 'FINAL', 1, 86.50, 'ACTIVE',
  '按工序匹配：粗车台州宏达、热处理嘉兴金盾、精磨宁波博锐，交期与良率均可覆盖。',
  DATE_SUB(NOW(), INTERVAL 10 DAY)
);
SET @sid = LAST_INSERT_ID();

INSERT INTO `order` (demand_id, solution_id, total_amount, commission_rate, commission_amount, status, created_at, updated_at, deleted)
VALUES (@did, @sid, 58000.00, 0.0100, 580.00, 'IN_PRODUCTION', DATE_SUB(NOW(), INTERVAL 9 DAY), NOW(), 0);
SET @oid = LAST_INSERT_ID();

SET @base = 'd:/智能云端工厂物联项目/uploads/';
INSERT INTO attachment (biz_type, biz_id, file_name, file_path, file_size, file_type, uploader_id, created_at) VALUES
  ('CONTRACT', @oid, '转向节外协合同-台州宏达.md', CONCAT(@base, '转向节外协合同-台州宏达.md'), 420, 'md', @buyer_uid, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  ('CONTRACT', @oid, '转向节外协合同-嘉兴金盾.md', CONCAT(@base, '转向节外协合同-嘉兴金盾.md'), 380, 'md', @buyer_uid, DATE_SUB(NOW(), INTERVAL 8 DAY)),
  ('CONTRACT', @oid, '转向节外协合同-宁波博锐.md', CONCAT(@base, '转向节外协合同-宁波博锐.md'), 400, 'md', @buyer_uid, DATE_SUB(NOW(), INTERVAL 8 DAY));

SET @att_rough = (SELECT id FROM attachment WHERE biz_id=@oid AND file_name LIKE '%宏达%' LIMIT 1);
SET @att_heat = (SELECT id FROM attachment WHERE biz_id=@oid AND file_name LIKE '%金盾%' LIMIT 1);
SET @att_fine = (SELECT id FROM attachment WHERE biz_id=@oid AND file_name LIKE '%博锐%' LIMIT 1);

INSERT INTO contract (
  order_id, tenant_id, version, sampling_json, status, hash, attachment_id,
  buyer_read, buyer_sign, factory_read, factory_sign, signed_at, created_at
) VALUES
  (@oid, @f_rough, 1, '{"mode":"AQL","aql":"1.0","sample":13}', 'SIGNED', REPEAT('a', 64), @att_rough,
   1, 'demo-buyer-sign', 1, 'demo-factory-sign-hongda', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@oid, @f_heat, 1, '{"mode":"AQL","aql":"1.0","sample":13}', 'SIGNED', REPEAT('b', 64), @att_heat,
   1, 'demo-buyer-sign', 1, 'demo-factory-sign-jindun', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (@oid, @f_fine, 1, '{"mode":"AQL","aql":"1.0","sample":13}', 'SIGNED', REPEAT('c', 64), @att_fine,
   1, 'demo-buyer-sign', 1, 'demo-factory-sign-borui', DATE_SUB(NOW(), INTERVAL 7 DAY), DATE_SUB(NOW(), INTERVAL 8 DAY));

INSERT INTO work_stage (
  order_id, tenant_id, process_no, process_name, quantity, promised_days, promised_date,
  amount, escrow_status, actual_progress, status, created_at, updated_at, deleted
) VALUES
  (@oid, @f_rough, 1, '粗车', 800, 10, DATE_ADD(CURDATE(), INTERVAL 3 DAY),
   18000.00, 'NONE', 68, 'IN_PRODUCTION', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),
  (@oid, @f_heat, 2, '热处理', 800, 8, DATE_ADD(CURDATE(), INTERVAL 1 DAY),
   12000.00, 'NONE', 100, 'PENDING_INSPECTION', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0),
  (@oid, @f_fine, 3, '精磨', 800, 15, DATE_ADD(CURDATE(), INTERVAL 12 DAY),
   28000.00, 'NONE', 15, 'IN_PRODUCTION', DATE_SUB(NOW(), INTERVAL 7 DAY), NOW(), 0);

SET @ws_rough = (SELECT id FROM work_stage WHERE order_id=@oid AND process_no=1);
SET @ws_heat = (SELECT id FROM work_stage WHERE order_id=@oid AND process_no=2);
SET @ws_fine = (SELECT id FROM work_stage WHERE order_id=@oid AND process_no=3);

INSERT INTO stage_progress_log (stage_id, done_qty, progress, remark, created_at) VALUES
  (@ws_rough, 280, 35, '首批 280 件粗车完成，同轴度抽检合格，待转下一批。', DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (@ws_rough, 544, 68, '累计 544 件，刀补已微调，预计 3 日内完成剩余件。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@ws_heat, 400, 50, '井式炉第一炉 400 件出炉，硬度抽检 58-61HRC。', DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (@ws_heat, 800, 100, '800 件热处理全部完成，已装箱待平台质检。', DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@ws_fine, 120, 15, '已精磨 120 件安装面，φ42h6 抽检在公差内。', DATE_SUB(NOW(), INTERVAL 2 DAY));

UPDATE device SET status='IN_USE' WHERE id IN (@dev_rough, @dev_heat, @dev_fine);

INSERT INTO fund_flow (demand_id, tenant_id, type, direction, amount, status, idempotent_no, created_at) VALUES
  (@did, @f_rough, 'INTENTION', 'FREEZE', 1000.00, 'SUCCESS', CONCAT('INTENTION-FREEZE-', @qid_rough), DATE_SUB(NOW(), INTERVAL 16 DAY)),
  (@did, @f_heat, 'INTENTION', 'FREEZE', 1000.00, 'SUCCESS', CONCAT('INTENTION-FREEZE-', @qid_heat), DATE_SUB(NOW(), INTERVAL 16 DAY)),
  (@did, @f_fine, 'INTENTION', 'FREEZE', 1000.00, 'SUCCESS', CONCAT('INTENTION-FREEZE-', @qid_fine), DATE_SUB(NOW(), INTERVAL 16 DAY)),
  (@did, @f_rough, 'DEPOSIT', 'FREEZE', 180.00, 'SUCCESS', CONCAT('DEPOSIT-FREEZE-', @qid_rough), DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (@did, @f_heat, 'DEPOSIT', 'FREEZE', 120.00, 'SUCCESS', CONCAT('DEPOSIT-FREEZE-', @qid_heat), DATE_SUB(NOW(), INTERVAL 12 DAY)),
  (@did, @f_fine, 'DEPOSIT', 'FREEZE', 280.00, 'SUCCESS', CONCAT('DEPOSIT-FREEZE-', @qid_fine), DATE_SUB(NOW(), INTERVAL 12 DAY));

UPDATE account SET balance=1000000.00-1180.00, frozen=1180.00 WHERE tenant_id=@f_rough;
UPDATE account SET balance=1000000.00-1120.00, frozen=1120.00 WHERE tenant_id=@f_heat;
UPDATE account SET balance=1000000.00-1280.00, frozen=1280.00 WHERE tenant_id=@f_fine;

INSERT INTO notify (tenant_id, title, content, is_read, created_at) VALUES
  (@buyer, CONCAT('合同已审过#', @oid), '三厂合同均已确认签署，工单已拆分，工厂已开工。', 0, DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (@buyer, CONCAT('热处理已交付待检#', @did), '嘉兴金盾已交付热处理 800 件，请关注运营质检结果。', 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@f_rough, CONCAT('请按工单生产#', @oid), '粗车工单已开工，请按承诺交期继续上报进度。', 0, DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (@f_heat, CONCAT('请等待质检#', @oid), '热处理工单已交付，等待平台质检，合格后买家将支付本段托管。', 0, DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (@f_fine, CONCAT('请按工单生产#', @oid), '精磨工单已开工，请按件数上报进度。', 0, DATE_SUB(NOW(), INTERVAL 6 DAY));

SELECT @did AS demand_id, @oid AS order_id, @ws_rough AS stage_rough, @ws_heat AS stage_heat, @ws_fine AS stage_fine;

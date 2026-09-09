package com.dsh.platform.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 清掉测试业务数据，写入可展示的历史订单、资金流水与信用事件。
 * 账号企业保留；已有「RV减速机针齿壳精车外协」则跳过，避免每次启动清空业务表。
 */
@Slf4j
@Component
@Order(22)
@RequiredArgsConstructor
public class ShowcaseHistorySeeder implements CommandLineRunner {

    private static final String MARKER = "RV减速机针齿壳精车外协";

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("历史样例改由 OpsShowcaseSeeder 写入，本组件跳过");
    }

    @SuppressWarnings("unused")
    private void legacyDisabled() {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM demand WHERE title = ? AND deleted = 0",
                Integer.class, MARKER);
        if (n != null && n > 0) {
            return;
        }
        cleanup();
        long buyerId = tenantByPhone("13000000001");
        long factory2 = tenantByPhone("13000000002");
        long factory3 = tenantByPhone("13000000003");
        long factory4 = tenantByPhone("13000000004");
        long factory5 = tenantByPhone("13000000005");
        seedCompleted("RV减速机针齿壳精车外协", "RV针齿壳", "机器人减速机零件",
                "20CrMnTi", 800, new BigDecimal("85.00"), buyerId, factory2,
                "宁波博锐精密机械有限公司", 1, "精车",
                "内齿圈基准圆跳动 0.01，齿面粗糙度 Ra0.8", "圆跳动 0.01 / Ra0.8",
                "AQL", new BigDecimal("5"), LocalDateTime.of(2026, 5, 8, 9, 20), false);
        seedCompleted("液压阀块深孔与安装面精铣", "液压阀块", "工程机械液压件",
                "45# 调质钢", 600, new BigDecimal("42.00"), buyerId, factory3,
                "台州宏达机械加工厂", 1, "精铣",
                "深孔直线度 0.05/100，安装面平面度 0.02", "直线度 0.05 / 平面度 0.02",
                "FULL", new BigDecimal("3"), LocalDateTime.of(2026, 6, 2, 10, 15), true);
        seedCompleted("传动法兰渗碳淬火热处理", "传动法兰", "汽车传动件",
                "20CrMnTi", 1200, new BigDecimal("18.50"), buyerId, factory4,
                "嘉兴金盾热处理有限公司", 1, "热处理",
                "渗碳层 0.8-1.2mm，表面硬度 58-62HRC", "渗碳 0.8-1.2mm / 58-62HRC",
                "AQL", new BigDecimal("5"), LocalDateTime.of(2026, 6, 20, 14, 0), false);
        seedCompleted("铝合金电机端盖精铣阳极氧化", "电机端盖", "新能源电机结构件",
                "ADC12 压铸铝", 500, new BigDecimal("36.00"), buyerId, factory5,
                "苏州汇通智能制造有限公司", 1, "精铣",
                "止口同轴度 0.03，氧化膜 8-12μm", "同轴度 0.03 / 氧化 8-12μm",
                "AQL", new BigDecimal("5"), LocalDateTime.of(2026, 7, 10, 11, 30), false);
        refreshCredits(buyerId, factory2, factory3, factory4, factory5);
        log.info("已清理测试业务数据，并写入 4 笔已完成历史订单（流水与信用事件已对齐）");
    }

    private void cleanup() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of(
                "survey", "credit_event", "fund_flow", "inspection", "stage_progress_log",
                "work_stage", "contract", "`order`", "solution", "quotation",
                "attachment", "process", "demand", "notify", "audit_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        List<Long> testTenants = jdbc.query(
                "SELECT tenant_id FROM sys_user WHERE phone = '13000000006'",
                (rs, i) -> rs.getLong(1));
        for (Long id : testTenants) {
            jdbc.update("DELETE FROM device WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM account WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM sys_user WHERE tenant_id = ?", id);
            jdbc.update("DELETE FROM enterprise WHERE id = ?", id);
        }
        jdbc.update("""
                UPDATE account a JOIN enterprise e ON e.id = a.tenant_id
                SET a.balance = 1000000.00, a.frozen = 0.00
                WHERE e.type <> 'PLATFORM'
                """);
        jdbc.update("""
                UPDATE account a JOIN enterprise e ON e.id = a.tenant_id
                SET a.balance = 0.00, a.frozen = 0.00
                WHERE e.type = 'PLATFORM'
                """);
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 1");
        log.info("测试业务数据与注册测试厂已清除，账户已复位");
    }

    private void seedCompleted(String title, String product, String category, String material,
                               int qty, BigDecimal unit, long buyerId, long factoryId, String factoryName,
                               int processNo, String processName, String requirement, String tolerance,
                               String inspectMode, BigDecimal inspectUnit,
                               LocalDateTime t0, boolean lateDeliver) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal deposit = total.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = total.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inspectFee = inspectUnit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal wages = total.subtract(commission);
        long platformId = platformTenant();
        long inspectorId = inspectorTenant();
        LocalDate hard = t0.toLocalDate().plusDays(35);
        LocalDateTime pub = t0;
        LocalDateTime think = t0.plusDays(3);
        LocalDateTime buyerThink = t0.plusDays(4);
        LocalDateTime signed = t0.plusDays(6);
        LocalDateTime deliverAt = lateDeliver ? t0.plusDays(38) : t0.plusDays(28);
        LocalDateTime inspectAt = deliverAt.plusDays(1);
        LocalDateTime payAt = inspectAt.plusHours(8);
        LocalDateTime doneAt = payAt.plusHours(2);

        long demandId = insert("""
                INSERT INTO demand (tenant_id, title, product_name, category, quantity, material, tolerance,
                  surface_treatment, aql, certification, min_yield, min_credit_score, deadline_hard,
                  delivery_address, packaging, multi_process, weight_json, intention_days, remark,
                  inspect_mode, inspect_price, general_tolerance, part_revision, extra_json, delivery_times, estimated_total,
                  buyer_deposit_status, status, published_at, factory_thinking_at, factory_thinking_end_at,
                  buyer_thinking_at, buyer_thinking_end_at, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,0.9800,65,?,
                  '杭州市余杭区仓前街道文一西路 1500 号 精工传动成品库','防锈油封+隔层纸+木箱',0,
                  '{"cost":0.34,"time":0.33,"quality":0.33}',5,?,
                  ?, ?, 'ISO 2768-m', ?, '{"roughness":"Ra1.6"}', 1, ?,
                  'RELEASED', 'COMPLETED', ?, ?, ?, ?, ?, ?, ?, 0)
                """, buyerId, title, product, category, qty, material, tolerance,
                "防锈油", "1.0", "ISO9001,IATF16949", Date.valueOf(hard),
                "按图纸一次交检，包装防锈后送精工传动成品库。",
                inspectMode, inspectUnit, product + "-A", total,
                ts(pub), ts(think), ts(think.plusHours(24)), ts(buyerThink), ts(buyerThink.plusHours(24)),
                ts(t0.minusDays(1)), ts(doneAt));

        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES (?,?,?,?,?)",
                demandId, processNo, processName, requirement, ts(t0));

        long quoteId = insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,0.9850,25,?,?,?,'RELEASED','RELEASED','WIN',?,?,0)
                """, factoryId, demandId, processNo, unit, total, qty / 2, qty,
                "按图纸一次装夹完成，过程检验每 50 件抽 3 件，交期按硬节点倒排。",
                ts(think), ts(think));

        String combo = "[{\"factoryId\":" + factoryId
                + ",\"factoryName\":\"" + factoryName + "\""
                + ",\"processNo\":" + processNo
                + ",\"quantity\":" + qty
                + ",\"unitPrice\":" + unit.toPlainString()
                + ",\"price\":" + total.toPlainString() + "}]";
        long solutionId = insert("""
                INSERT INTO solution (demand_id, type, suggested_combo_json, final_combo_json, source, is_final,
                  score, status, rationale_json, created_at)
                VALUES (?,'AI1',?,?, 'FINAL', 1, 88.5, 'ACTIVE', '{"summary":"该厂工序匹配、交期与质量门槛均满足。"}', ?)
                """, demandId, combo, combo, ts(buyerThink));

        long orderId = insert("""
                INSERT INTO `order` (demand_id, solution_id, total_amount, commission_rate, commission_amount,
                  status, created_at, updated_at, deleted)
                VALUES (?,?,?,0.0100,?,'COMPLETED',?,?,0)
                """, demandId, solutionId, total, commission, ts(signed), ts(doneAt));

        jdbc.update("""
                INSERT INTO contract (order_id, tenant_id, version, status, buyer_read, factory_read, signed_at, created_at)
                VALUES (?, ?, 1, 'SIGNED', 1, 1, ?, ?)
                """, orderId, factoryId, ts(signed), ts(signed.minusDays(1)));

        long stageId = insert("""
                INSERT INTO work_stage (order_id, tenant_id, process_no, process_name, quantity, promised_days,
                  promised_date, amount, escrow_status, actual_progress, status, pay_amount, first_yield,
                  inspect_round, first_quantity_ok, delivered_qty, period_no, period_start, period_end,
                  inspect_fee_status, inspect_fee_amount, inspect_fee_payer, inspect_kind, fai_status,
                  created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,?,?,?, 'SETTLED', 100, 'PASS', ?, 0.9850, 1, 1, ?, 1, ?, ?,
                  'PAID', ?, 'BUYER', 'LOT', 'PASS', ?, ?, 0)
                """, orderId, factoryId, processNo, processName, qty, 25, Date.valueOf(hard), total, wages, qty,
                ts(signed.plusDays(1)), ts(deliverAt), inspectFee, ts(signed), ts(doneAt));

        jdbc.update("""
                INSERT INTO stage_progress_log (stage_id, done_qty, progress, remark, created_at)
                VALUES (?, ?, 50, '中期进度：基准面与止口已完成', ?),
                       (?, ?, 100, '全部完工，已送检', ?)
                """, stageId, qty / 2, ts(deliverAt.minusDays(10)), stageId, qty, ts(deliverAt));

        String report = "{\"sampleCount\":" + Math.min(80, qty)
                + ",\"failCount\":0,\"criticalFailCount\":0,\"generalFailCount\":0,\"deliveredQty\":" + qty
                + ",\"quantityOk\":true,\"toleranceOk\":true,\"actualYield\":0.985,\"inspectMode\":\""
                + inspectMode + "\",\"remark\":\"尺寸与外观均满足图纸\"}";
        long inspId = insert("""
                INSERT INTO inspection (stage_id, inspector_tenant_id, result, report_json, status, created_at)
                VALUES (?, ?, 'PASS', ?, 'VALID', ?)
                """, stageId, inspectorId, report, ts(inspectAt));

        flow("INTENTION", "FREEZE", new BigDecimal("1000.00"), demandId, factoryId, null,
                "SHOW-INT-F-" + quoteId, think);
        flow("INTENTION", "UNFREEZE", new BigDecimal("1000.00"), demandId, factoryId, null,
                "SHOW-INT-U-" + quoteId, think.plusHours(2));
        flow("DEPOSIT", "FREEZE", deposit, demandId, factoryId, null, "SHOW-DEP-F-" + quoteId, think.plusHours(2));
        flow("BUYER_DEPOSIT", "FREEZE", deposit, demandId, buyerId, null, "SHOW-BDEP-F-" + demandId, buyerThink);
        flow("INSPECT_FEE", "OUT", inspectFee, demandId, buyerId, orderId, "SHOW-INSP-OUT-" + stageId, inspectAt.minusHours(2));
        flow("INSPECT_FEE", "IN", inspectFee, demandId, platformId, orderId, "SHOW-INSP-IN-" + stageId, inspectAt.minusHours(2));
        flow("ESCROW", "OUT", total, demandId, buyerId, orderId, "SHOW-ESC-BO-" + stageId, payAt);
        flow("ESCROW", "IN", total, demandId, platformId, orderId, "SHOW-ESC-PI-" + stageId, payAt);
        flow("ESCROW", "OUT", wages, demandId, platformId, orderId, "SHOW-ESC-PO-" + orderId + "-" + factoryId, doneAt);
        flow("PAYMENT", "IN", wages, demandId, factoryId, orderId, "SHOW-PAY-" + orderId + "-" + factoryId, doneAt);
        flow("COMMISSION", "IN", commission, demandId, platformId, orderId, "SHOW-COM-" + orderId, doneAt);
        flow("DEPOSIT", "UNFREEZE", deposit, demandId, factoryId, orderId, "SHOW-DEP-U-" + quoteId, doneAt);
        flow("BUYER_DEPOSIT", "UNFREEZE", deposit, demandId, buyerId, orderId, "SHOW-BDEP-U-" + demandId, doneAt);

        credit(factoryId, lateDeliver ? "PUNCTUAL_LATE" : "PUNCTUAL_ON", lateDeliver ? -1 : 1, "STAGE", stageId,
                lateDeliver ? "超过硬交期交付" : "按硬交期交付", deliverAt);
        credit(factoryId, "QUALITY_PASS", 2, "INSPECTION", inspId, "批次质检合格", inspectAt);
        credit(buyerId, "PAY_ON_TIME", 1, "STAGE", stageId, "质检通过后 48 小时内完成阶段托管", payAt);
        credit(factoryId, "ORDER_COMPLETE", 2, "ORDER", orderId, "整单完工且无质量失信", doneAt);

        applyMoney(buyerId, inspectFee.add(total).negate());
        applyMoney(factoryId, wages);
        applyMoney(platformId, inspectFee.add(commission));

        jdbc.update("""
                INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at)
                VALUES (?, ?, ?, ?, 1, ?)
                """, buyerId, demandId, "订单已完成#" + demandId,
                "需求「" + title + "」已结算，工厂工钱已到账。", ts(doneAt));
        jdbc.update("""
                INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at)
                VALUES (?, ?, ?, ?, 1, ?)
                """, factoryId, demandId, "工钱已入账#" + demandId,
                "需求「" + title + "」已结算，工钱 " + wages.toPlainString() + " 元已入账。", ts(doneAt));
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, target_type, target_id, after_json, created_at)
                VALUES (6, '买家完工确认', 'ORDER', ?, JSON_QUOTE(?), ?)
                """, orderId, "需求#" + demandId + "「" + title + "」已结算", ts(doneAt));
    }

    private void refreshCredits(long buyerId, long factory2, long factory3, long factory4, long factory5) {
        jdbc.update("UPDATE enterprise SET credit_score = 82 WHERE id = ?", buyerId);
        jdbc.update("UPDATE enterprise SET credit_score = 87 WHERE id = ?", factory2);
        jdbc.update("UPDATE enterprise SET credit_score = 73 WHERE id = ?", factory3);
        jdbc.update("UPDATE enterprise SET credit_score = 81 WHERE id = ?", factory4);
        jdbc.update("UPDATE enterprise SET credit_score = 79 WHERE id = ?", factory5);
    }

    private long tenantByPhone(String phone) {
        Long id = jdbc.queryForObject("SELECT tenant_id FROM sys_user WHERE phone = ? LIMIT 1", Long.class, phone);
        if (id == null) {
            throw new IllegalStateException("未找到账号 " + phone);
        }
        return id;
    }

    private long platformTenant() {
        Long id = jdbc.queryForObject("SELECT id FROM enterprise WHERE type = 'PLATFORM' LIMIT 1", Long.class);
        if (id == null) {
            throw new IllegalStateException("未找到平台企业");
        }
        return id;
    }

    private long inspectorTenant() {
        List<Long> ids = jdbc.query(
                "SELECT tenant_id FROM sys_user WHERE phone IN ('inspect01','123456') AND role = 'INSPECTION' ORDER BY id DESC",
                (rs, i) -> rs.getLong(1));
        if (ids.isEmpty()) {
            throw new IllegalStateException("未找到质检账号");
        }
        return ids.get(0);
    }

    private void applyMoney(Long tenantId, BigDecimal delta) {
        jdbc.update("UPDATE account SET balance = balance + ? WHERE tenant_id = ?", delta, tenantId);
    }

    private void flow(String type, String dir, BigDecimal amount, Long demandId, Long tenantId,
                      Long orderId, String key, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO fund_flow (order_id, demand_id, tenant_id, type, direction, amount, status, idempotent_no, created_at)
                VALUES (?, ?, ?, ?, ?, ?, 'SUCCESS', ?, ?)
                """, orderId, demandId, tenantId, type, dir, amount, key, ts(at));
    }

    private void credit(Long tenantId, String type, int change, String refType, Long refId,
                        String remark, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO credit_event (tenant_id, type, score_change, ref_type, ref_id, remark, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, tenantId, type, change, refType, refId, remark, ts(at));
    }

    private long insert(String sql, Object... args) {
        KeyHolder kh = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, kh);
        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("未获得自增主键");
        }
        return key.longValue();
    }

    private static Timestamp ts(LocalDateTime t) {
        return Timestamp.valueOf(t);
    }
}

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
import java.util.Map;

/**
 * 清空旧需求及相关履约数据，写入近一个月覆盖各阶段的运营样例（幂等：标题标记存在则跳过）。
 */
@Slf4j
@Component
@Order(32)
@RequiredArgsConstructor
public class OpsShowcaseSeeder implements CommandLineRunner {

    static final String MARKER = "运营样例·RV减速机针齿壳精车外协";

    private final JdbcTemplate jdbc;

    @Override
    @Transactional
    public void run(String... args) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM demand WHERE title = ? AND deleted = 0",
                Integer.class, MARKER);
        if (n != null && n > 0) {
            seedAuditsIfEmpty();
            log.info("运营样例已存在，跳过清空与写入");
            return;
        }
        cleanup();
        long[] buyers = phones("13000000001", "13000000021", "13000000022", "13000000023",
                "13000000024", "13000000025", "13000000026", "13000000027", "13000000028", "13000000029");
        long[] factories = phones("13000000002", "13000000003", "13000000004", "13000000005",
                "13000000006", "13000000007", "13000000008", "13000000009", "13000000010", "13000000011");
        String[] factoryNames = {
                "宁波博锐精密机械有限公司", "台州宏达机械加工厂", "嘉兴金盾热处理有限公司",
                "苏州汇通智能制造有限公司", "温州瓯海精密铸造有限公司", "常州武进数控机床协作厂",
                "无锡惠山模具制造有限公司", "杭州萧山表面处理有限公司", "绍兴柯桥齿轮传动加工厂",
                "湖州德清钣金智造有限公司"
        };
        LocalDateTime now = LocalDateTime.now().withNano(0);

        seedCompleted(MARKER, "RV针齿壳", "机器人减速机零件", "20CrMnTi", 800,
                new BigDecimal("85.00"), buyers[0], factories[0], factoryNames[0],
                1, "精车", "内齿圈基准圆跳动 0.01", "圆跳动 0.01", "AQL", new BigDecimal("5"),
                now.minusDays(38), false);
        seedCompleted("液压阀块深孔与安装面精铣", "液压阀块", "工程机械液压件", "45# 调质钢", 600,
                new BigDecimal("42.00"), buyers[6], factories[5], factoryNames[5],
                1, "精铣", "深孔直线度 0.05/100", "直线度 0.05", "FULL", new BigDecimal("3"),
                now.minusDays(36), true);
        seedCompleted("传动法兰渗碳淬火热处理", "传动法兰", "汽车传动件", "20CrMnTi", 1200,
                new BigDecimal("18.50"), buyers[2], factories[2], factoryNames[2],
                1, "热处理", "渗碳层 0.8-1.2mm", "58-62HRC", "AQL", new BigDecimal("5"),
                now.minusDays(34), false);
        seedCompleted("铝合金电机端盖精铣", "电机端盖", "新能源电机结构件", "ADC12", 500,
                new BigDecimal("36.00"), buyers[1], factories[3], factoryNames[3],
                1, "精铣", "止口同轴度 0.03", "同轴度 0.03", "AQL", new BigDecimal("5"),
                now.minusDays(32), false);
        seedCompleted("谐波柔轮齿圈滚齿", "柔轮齿圈", "机器人减速机零件", "30CrMo", 300,
                new BigDecimal("128.00"), buyers[3], factories[8], factoryNames[8],
                1, "滚齿", "齿形误差 7 级", "7 级精度", "FULL", new BigDecimal("8"),
                now.minusDays(30), false);
        seedCompleted("船用法兰精车钻孔", "船用法兰", "船舶配套件", "Q345", 220,
                new BigDecimal("55.00"), buyers[5], factories[6], factoryNames[5],
                1, "精车", "密封槽尺寸 ±0.02", "φ密封槽 ±0.02", "AQL", new BigDecimal("4"),
                now.minusDays(28), false);
        seedCompleted("轨道车辆销轴调质", "销轴", "轨道交通零件", "40Cr", 900,
                new BigDecimal("12.80"), buyers[4], factories[2], factoryNames[2],
                1, "热处理", "调质 28-32HRC", "硬度 28-32HRC", "AQL", new BigDecimal("4"),
                now.minusDays(26), false);
        seedCompleted("消费电子散热壳体精铣氧化", "散热壳体", "消费电子结构件", "6063 铝", 2000,
                new BigDecimal("9.60"), buyers[9], factories[3], factoryNames[3],
                1, "精铣", "齿片厚度 ±0.05", "±0.05", "AQL", new BigDecimal("2"),
                now.minusDays(24), false);
        seedCompleted("机床主轴套精车磨削", "主轴套", "机床配套件", "GCr15", 180,
                new BigDecimal("96.00"), buyers[8], factories[1], factoryNames[6],
                1, "精磨", "内孔圆度 0.005", "圆度 0.005", "FULL", new BigDecimal("6"),
                now.minusDays(22), false);
        seedCompleted("工程机械护罩钣金", "护罩", "工程机械结构件", "Q235", 80,
                new BigDecimal("210.00"), buyers[7], factories[9], factoryNames[9],
                1, "激光切割", "外形 ±1.0", "外形 ±1", "AQL", new BigDecimal("3"),
                now.minusDays(20), false);

        seedCancelled("买家取消·转向节臂粗车", buyers[2], now.minusDays(12), "意向期后产能调整，买家主动取消");
        seedCancelled("买家取消·泵盖小批量试制", buyers[5], now.minusDays(8), "设计改版，暂停外协");
        seedFlowFailed("流单·阀体深孔无人承接", buyers[7], now.minusDays(18));
        seedFlowFailed("流单·超短交期钛合金接头", buyers[6], now.minusDays(15));

        seedSimple("待审·航空接头壳体精铣", "接头壳体", "航空附件", "TC4", 40, buyers[6],
                "PENDING_AUDIT", now.minusDays(1), null, "全检，需可追溯批次。", 1, "精铣");
        seedSimple("待审·新能源逆变器箱体", "逆变器箱体", "新能源结构件", "6061-T6", 120, buyers[1],
                "PENDING_AUDIT", now.minusHours(10), null, "铣削+攻牙，待运营审核。", 1, "精铣");
        seedReturned("退回·齿轮轴图纸版本冲突", "齿轮轴", buyers[0], now.minusDays(3));

        long pub1 = seedSimple("意向中·减速机输出法兰精车", "输出法兰", "机器人减速机零件", "45#", 400, buyers[0],
                "PUBLISHED", now.minusDays(4), now.minusDays(3), "单工序精车，5 天意向。", 1, "精车");
        long pub2 = seedSimple("意向中·液压阀块多工序外协", "液压阀块", "工程机械液压件", "45#", 350, buyers[7],
                "PUBLISHED", now.minusDays(5), now.minusDays(4), "粗铣→精铣，请按工序报名。", 2, "精铣");
        long pub3 = seedSimple("意向中·铝合金端盖阳极氧化", "端盖", "新能源电机结构件", "ADC12", 800, buyers[1],
                "PUBLISHED", now.minusDays(2), now.minusDays(2), "机加完成后氧化。", 1, "表面处理");
        bid(pub1, factories[0], 1, new BigDecimal("48"), 400, now.minusDays(2));
        bid(pub1, factories[5], 1, new BigDecimal("51"), 400, now.minusDays(2));
        bid(pub1, factories[6], 1, new BigDecimal("49.5"), 360, now.minusDays(1));
        freezeIntention(factories[0], pub1, now.minusDays(2));
        freezeIntention(factories[5], pub1, now.minusDays(2));
        freezeIntention(factories[6], pub1, now.minusDays(1));
        bid(pub2, factories[3], 1, new BigDecimal("38"), 350, now.minusDays(3));
        bid(pub2, factories[5], 2, new BigDecimal("41"), 350, now.minusDays(3));
        freezeIntention(factories[3], pub2, now.minusDays(3));
        freezeIntention(factories[5], pub2, now.minusDays(3));
        bid(pub3, factories[7], 1, new BigDecimal("6.8"), 800, now.minusDays(1));
        freezeIntention(factories[7], pub3, now.minusDays(1));

        long ft = seedSimple("工厂思考期·齿轮轴粗车热处理精磨", "齿轮轴", "汽车传动件", "20CrMnTi", 1000, buyers[2],
                "FACTORY_THINKING", now.minusDays(9), now.minusDays(8), "多工序，工厂填方案并交保证金。", 3, "粗车");
        jdbc.update("UPDATE demand SET factory_thinking_at=?, factory_thinking_end_at=? WHERE id=?",
                ts(now.minusHours(10)), ts(now.plusHours(14)), ft);
        bidWinPrep(ft, factories[1], 1, new BigDecimal("22"), 1000, now.minusDays(7));
        bidWinPrep(ft, factories[2], 2, new BigDecimal("16"), 1000, now.minusDays(7));
        bidWinPrep(ft, factories[0], 3, new BigDecimal("28"), 1000, now.minusDays(7));

        long bt = seedSimple("买家思考期·电机端盖精铣", "电机端盖", "新能源电机结构件", "ADC12", 450, buyers[1],
                "BUYER_THINKING", now.minusDays(11), now.minusDays(10), "买家决定是否交 5% 保证金继续。", 1, "精铣");
        jdbc.update("UPDATE demand SET factory_thinking_at=?, factory_thinking_end_at=?, buyer_thinking_at=?, buyer_thinking_end_at=?, estimated_total=? WHERE id=?",
                ts(now.minusDays(6)), ts(now.minusDays(5)), ts(now.minusHours(8)), ts(now.plusHours(16)),
                new BigDecimal("16200.00"), bt);
        bidCommitted(bt, factories[3], 1, new BigDecimal("36"), 450, now.minusDays(6));
        freezeDeposit(factories[3], bt, new BigDecimal("810.00"), now.minusDays(5));

        long sg = seedSimple("方案待审·阀块深孔精铣", "液压阀块", "工程机械液压件", "45#", 280, buyers[7],
                "SOLUTION_GENERATED", now.minusDays(14), now.minusDays(13), "AI 方案待运营审核下发。", 1, "精铣");
        bidCommitted(sg, factories[5], 1, new BigDecimal("44"), 280, now.minusDays(10));
        insertSolution(sg, factories[5], factoryNames[5], 280, new BigDecimal("44"), "PENDING_REVIEW", now.minusDays(1));

        long ss = seedSimple("已选方案·销轴精车", "销轴", "工程机械结构件", "40Cr", 640, buyers[7],
                "SOLUTION_SELECTED", now.minusDays(13), now.minusDays(12), "方案已选定，待签合同。", 1, "精车");
        bidCommitted(ss, factories[6], 1, new BigDecimal("14.5"), 640, now.minusDays(9));
        insertSolution(ss, factories[6], factoryNames[6], 640, new BigDecimal("14.5"), "ACTIVE", now.minusDays(2));

        seedFulfill("签约中·泵体精铣", "泵体", "船舶配套件", "HT250", 90,
                new BigDecimal("188.00"), buyers[5], factories[4], factoryNames[4],
                "精铣", "CONTRACTED", "NONE", "PENDING", 0, now.minusDays(10), false, false);
        seedFulfill("生产中·减速机针齿壳精车", "针齿壳", "机器人减速机零件", "20CrMnTi", 360,
                new BigDecimal("72.00"), buyers[0], factories[0], factoryNames[0],
                "精车", "IN_PRODUCTION", "NONE", "IN_PRODUCTION", 55, now.minusDays(16), false, false);
        seedFulfill("待质检·主轴法兰精车", "主轴法兰", "机床配套件", "45#", 140,
                new BigDecimal("68.00"), buyers[8], factories[5], factoryNames[5],
                "精车", "IN_PRODUCTION", "PENDING_PAY", "PENDING_INSPECTION", 100, now.minusDays(14), true, false);
        seedFulfill("不合格待处理·阀盖平面度超差", "阀盖", "工程机械液压件", "45#", 200,
                new BigDecimal("39.00"), buyers[7], factories[3], factoryNames[3],
                "精铣", "IN_PRODUCTION", "NONE", "FAIL", 100, now.minusDays(12), true, true);
        seedFulfill("已关闭后续期·端盖二期", "电机端盖", "新能源电机结构件", "ADC12", 500,
                new BigDecimal("33.00"), buyers[1], factories[3], factoryNames[3],
                "精铣", "IN_PRODUCTION", "SETTLED", "CLOSED", 100, now.minusDays(15), true, false);

        refreshCredits(buyers, factories);
        try {
            seedAudits();
        } catch (Exception e) {
            log.warn("写入操作日志失败: {}", e.getMessage());
        }
        log.info("已清空旧需求并写入近一个月运营样例：10 买家 / 10 工厂，覆盖审核、撮合、方案、履约、结算、取消与流单");
    }

    private void seedAuditsIfEmpty() {
        try {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
            if (n != null && n > 0) {
                return;
            }
            seedAudits();
        } catch (Exception e) {
            log.warn("补写操作日志失败: {}", e.getMessage());
        }
    }

    private void seedAudits() {
        long adminId = userIdByRoles("SUPER_ADMIN", "OPERATOR");
        long inspectorId = userIdByRoles("INSPECTION", "INSPECTOR");
        if (inspectorId == 0) {
            inspectorId = adminId;
        }
        List<Map<String, Object>> demands = jdbc.queryForList("""
                SELECT id, tenant_id, title, status, created_at, published_at, updated_at
                FROM demand WHERE deleted = 0
                """);
        int rows = 0;
        for (Map<String, Object> row : demands) {
            long demandId = ((Number) row.get("id")).longValue();
            long tenantId = ((Number) row.get("tenant_id")).longValue();
            String title = String.valueOf(row.get("title"));
            String status = String.valueOf(row.get("status"));
            LocalDateTime created = toLdt(row.get("created_at"));
            LocalDateTime published = toLdt(row.get("published_at"));
            LocalDateTime updated = toLdt(row.get("updated_at"));
            long buyerUser = userIdByTenant(tenantId);
            if (created == null) {
                created = LocalDateTime.now().minusDays(7);
            }
            audit(buyerUser, "买家申请发布", "DEMAND", demandId, "「" + title + "」提交审核", created);
            rows++;
            if ("PENDING_AUDIT".equals(status)) {
                continue;
            }
            if ("RETURNED".equals(status)) {
                audit(adminId, "需求退回", "DEMAND", demandId, "图纸版次与零件号不一致，请修改后重提。", created.plusHours(3));
                rows++;
                continue;
            }
            LocalDateTime pub = published == null ? created.plusHours(2) : published;
            audit(adminId, "需求审核通过", "DEMAND", demandId, "「" + title + "」已发布", pub);
            rows++;
            if ("CANCELLED".equals(status)) {
                audit(buyerUser, "买家取消需求", "DEMAND", demandId, "「" + title + "」买家主动取消", created.plusDays(1));
                rows++;
                continue;
            }
            if ("FLOW_FAILED".equals(status)) {
                audit(adminId, "意向期流单", "DEMAND", demandId, "无有效报名", (updated == null ? pub.plusDays(5) : updated));
                rows++;
                continue;
            }
            List<Long> factoryTenants = jdbc.query(
                    "SELECT DISTINCT tenant_id FROM quotation WHERE demand_id = ? AND deleted = 0",
                    (rs, i) -> rs.getLong(1), demandId);
            LocalDateTime t = pub.plusHours(6);
            for (Long ft : factoryTenants) {
                audit(userIdByTenant(ft), "工厂报名", "DEMAND", demandId, "「" + title + "」意向金已冻结", t);
                rows++;
                t = t.plusHours(2);
            }
            if ("PUBLISHED".equals(status)) {
                continue;
            }
            audit(adminId, "结束意向期", "DEMAND", demandId, "进入工厂思考期", pub.plusDays(5));
            rows++;
            if ("FACTORY_THINKING".equals(status)) {
                continue;
            }
            for (Long ft : factoryTenants) {
                audit(userIdByTenant(ft), "工厂提交报价", "DEMAND", demandId, "「" + title + "」保证金已冻结，意向金已退回", pub.plusDays(5).plusHours(8));
                rows++;
            }
            audit(adminId, "结束工厂思考期", "DEMAND", demandId, "进入买家思考期", pub.plusDays(6));
            rows++;
            if ("BUYER_THINKING".equals(status)) {
                continue;
            }
            audit(buyerUser, "买家思考期继续", "DEMAND", demandId, "「" + title + "」保证金已冻结，生成方案", pub.plusDays(6).plusHours(10));
            rows++;
            if ("SOLUTION_GENERATED".equals(status)) {
                continue;
            }
            audit(adminId, "下发方案", "DEMAND", demandId, "「" + title + "」方案已下发", pub.plusDays(7));
            rows++;
            if ("SOLUTION_SELECTED".equals(status) || "SOLUTION_CONFIRMED".equals(status)) {
                audit(buyerUser, "买家确认方案", "SOLUTION", demandId, "需求#" + demandId + "「" + title + "」", pub.plusDays(8));
                rows++;
                continue;
            }
            if ("CONTRACTED".equals(status) || "IN_PRODUCTION".equals(status) || "COMPLETED".equals(status)) {
                audit(buyerUser, "买家确认方案", "SOLUTION", demandId, "需求#" + demandId + "「" + title + "」", pub.plusDays(7));
                audit(adminId, "进入合同签署", "DEMAND", demandId, "「" + title + "」进入签约", pub.plusDays(7).plusHours(2));
                rows += 2;
                List<Map<String, Object>> contracts = jdbc.queryForList(
                        "SELECT c.id, c.tenant_id, c.order_id FROM contract c JOIN `order` o ON o.id = c.order_id WHERE o.demand_id = ?",
                        demandId);
                for (Map<String, Object> c : contracts) {
                    long cid = ((Number) c.get("id")).longValue();
                    long ft = ((Number) c.get("tenant_id")).longValue();
                    audit(buyerUser, "买家上传合同", "CONTRACT", cid, "订单#" + c.get("order_id") + " 工厂#" + ft, pub.plusDays(8));
                    rows++;
                    if (!"CONTRACTED".equals(status)) {
                        audit(userIdByTenant(ft), "工厂签署合同", "CONTRACT", cid, "订单#" + c.get("order_id"), pub.plusDays(8).plusHours(6));
                        rows++;
                    }
                }
                if ("CONTRACTED".equals(status)) {
                    continue;
                }
                audit(buyerUser, "买家确认签署并派单", "ORDER", demandId, "需求#" + demandId + " 开始派单", pub.plusDays(9));
                rows++;
                List<Map<String, Object>> stages = jdbc.queryForList(
                        "SELECT w.id, w.tenant_id, w.process_name, w.status FROM work_stage w JOIN `order` o ON o.id = w.order_id WHERE o.demand_id = ?",
                        demandId);
                for (Map<String, Object> s : stages) {
                    long sid = ((Number) s.get("id")).longValue();
                    long ft = ((Number) s.get("tenant_id")).longValue();
                    String pname = String.valueOf(s.get("process_name"));
                    String st = String.valueOf(s.get("status"));
                    audit(userIdByTenant(ft), "工厂开工", "STAGE", sid, "工序「" + pname + "」", pub.plusDays(10));
                    rows++;
                    if ("PENDING".equals(st)) {
                        continue;
                    }
                    audit(userIdByTenant(ft), "工厂交付", "STAGE", sid, "工序「" + pname + "」已交付", pub.plusDays(18));
                    rows++;
                    if ("PENDING_INSPECTION".equals(st)) {
                        audit(inspectorId, "提交质检单", "STAGE", sid, "工序「" + pname + "」待运营审核", pub.plusDays(19));
                        rows++;
                    } else if ("FAIL".equals(st)) {
                        audit(inspectorId, "提交质检单", "STAGE", sid, "工序「" + pname + "」待运营审核", pub.plusDays(19));
                        audit(adminId, "审核质检单", "STAGE", sid, "不合格", pub.plusDays(19).plusHours(4));
                        rows += 2;
                    } else if ("CLOSED".equals(st)) {
                        audit(inspectorId, "提交质检单", "STAGE", sid, "工序「" + pname + "」待运营审核", pub.plusDays(19));
                        audit(adminId, "审核质检单", "STAGE", sid, "不合格", pub.plusDays(19).plusHours(2));
                        audit(buyerUser, "关闭连锁", "STAGE", sid, "买家关闭本阶段及后续工期", pub.plusDays(20));
                        rows += 3;
                    } else if ("PASS".equals(st) || "COMPLETED".equals(status)) {
                        audit(inspectorId, "提交质检单", "STAGE", sid, "工序「" + pname + "」待运营审核", pub.plusDays(19));
                        audit(adminId, "审核质检单", "STAGE", sid, "合格", pub.plusDays(19).plusHours(4));
                        rows += 2;
                    }
                }
                if ("COMPLETED".equals(status)) {
                    Number oid = jdbc.queryForObject(
                            "SELECT id FROM `order` WHERE demand_id = ? AND deleted = 0 LIMIT 1", Number.class, demandId);
                    if (oid != null) {
                        audit(buyerUser, "买家完工确认", "ORDER", oid.longValue(),
                                "需求#" + demandId + "「" + title + "」已结算", updated == null ? pub.plusDays(22) : updated);
                        rows++;
                    }
                }
            }
        }
        log.info("已写入操作日志 {} 条", rows);
    }

    private void audit(long actorId, String action, String type, Long targetId, String detail, LocalDateTime at) {
        String json = "{\"detail\":\"" + (detail == null ? "" : detail.replace("\\", "\\\\").replace("\"", "\\\"")) + "\"}";
        jdbc.update("""
                INSERT INTO audit_log (actor_id, action, target_type, target_id, after_json, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, actorId, action, type, targetId, json, ts(at == null ? LocalDateTime.now() : at));
    }

    private long userIdByTenant(long tenantId) {
        List<Long> ids = jdbc.query("SELECT id FROM sys_user WHERE tenant_id = ? ORDER BY id LIMIT 1",
                (rs, i) -> rs.getLong(1), tenantId);
        return ids.isEmpty() ? 0L : ids.get(0);
    }

    private long userIdByRoles(String... roles) {
        String in = String.join(",", java.util.Collections.nCopies(roles.length, "?"));
        List<Long> ids = jdbc.query("SELECT id FROM sys_user WHERE role IN (" + in + ") ORDER BY id LIMIT 1",
                (rs, i) -> rs.getLong(1), (Object[]) roles);
        return ids.isEmpty() ? 0L : ids.get(0);
    }

    private static LocalDateTime toLdt(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof LocalDateTime t) {
            return t;
        }
        if (v instanceof Timestamp t) {
            return t.toLocalDateTime();
        }
        return null;
    }

    private void cleanup() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS = 0");
        for (String table : List.of(
                "todo_ack", "survey", "credit_event", "fund_flow", "inspection", "stage_progress_log",
                "work_stage", "contract", "`order`", "solution", "quotation",
                "attachment", "process", "demand", "notify", "audit_log")) {
            try {
                jdbc.update("DELETE FROM " + table);
            } catch (Exception e) {
                log.warn("清理 {} 失败: {}", table, e.getMessage());
            }
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
        log.info("旧需求及相关履约/资金/信用数据已清除，账户已复位");
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
        LocalDateTime deliverAt = lateDeliver ? t0.plusDays(32) : t0.plusDays(22);
        LocalDateTime inspectAt = deliverAt.plusDays(1);
        LocalDateTime payAt = inspectAt.plusHours(8);
        LocalDateTime doneAt = payAt.plusHours(2);

        long demandId = insertDemand(buyerId, title, product, category, material, qty, tolerance, requirement,
                inspectMode, inspectUnit, total, "COMPLETED", t0.minusDays(1), pub, think, buyerThink, doneAt, hard,
                "RELEASED", "按图纸一次交检。");
        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES (?,?,?,?,?)",
                demandId, processNo, processName, requirement, ts(t0));
        long quoteId = insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,0.9850,25,?,?,?,'RELEASED','RELEASED','WIN',?,?,0)
                """, factoryId, demandId, processNo, unit, total, qty / 2, qty,
                "按图纸一次装夹完成，过程检验每 50 件抽 3 件。", ts(think), ts(think));
        String combo = comboJson(factoryId, factoryName, processNo, qty, unit, total);
        long solutionId = insert("""
                INSERT INTO solution (demand_id, type, suggested_combo_json, final_combo_json, source, is_final,
                  score, status, rationale_json, created_at)
                VALUES (?,'AI1',?,?, 'FINAL', 1, 88.5, 'ACTIVE', '{"summary":"工序匹配、交期与质量门槛均满足。"}', ?)
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
                VALUES (?, ?, 50, '中期进度', ?), (?, ?, 100, '全部完工已送检', ?)
                """, stageId, qty / 2, ts(deliverAt.minusDays(8)), stageId, qty, ts(deliverAt));
        String report = "{\"sampleCount\":" + Math.min(80, qty)
                + ",\"failCount\":0,\"criticalFailCount\":0,\"generalFailCount\":0,\"deliveredQty\":" + qty
                + ",\"quantityOk\":true,\"toleranceOk\":true,\"actualYield\":0.985,\"inspectMode\":\""
                + inspectMode + "\",\"remark\":\"尺寸与外观均满足图纸\"}";
        long inspId = insert("""
                INSERT INTO inspection (stage_id, inspector_tenant_id, result, report_json, status, created_at)
                VALUES (?, ?, 'PASS', ?, 'VALID', ?)
                """, stageId, inspectorId, report, ts(inspectAt));
        flow("INTENTION", "FREEZE", new BigDecimal("1000.00"), demandId, factoryId, null, "OPS-INT-F-" + quoteId, think);
        flow("INTENTION", "UNFREEZE", new BigDecimal("1000.00"), demandId, factoryId, null, "OPS-INT-U-" + quoteId, think.plusHours(2));
        flow("DEPOSIT", "FREEZE", deposit, demandId, factoryId, null, "OPS-DEP-F-" + quoteId, think.plusHours(2));
        flow("BUYER_DEPOSIT", "FREEZE", deposit, demandId, buyerId, null, "OPS-BDEP-F-" + demandId, buyerThink);
        flow("INSPECT_FEE", "OUT", inspectFee, demandId, buyerId, orderId, "OPS-INSP-OUT-" + stageId, inspectAt.minusHours(2));
        flow("INSPECT_FEE", "IN", inspectFee, demandId, platformId, orderId, "OPS-INSP-IN-" + stageId, inspectAt.minusHours(2));
        flow("ESCROW", "OUT", total, demandId, buyerId, orderId, "OPS-ESC-BO-" + stageId, payAt);
        flow("ESCROW", "IN", total, demandId, platformId, orderId, "OPS-ESC-PI-" + stageId, payAt);
        flow("ESCROW", "OUT", wages, demandId, platformId, orderId, "OPS-ESC-PO-" + orderId + "-" + factoryId, doneAt);
        flow("PAYMENT", "IN", wages, demandId, factoryId, orderId, "OPS-PAY-" + orderId + "-" + factoryId, doneAt);
        flow("COMMISSION", "IN", commission, demandId, platformId, orderId, "OPS-COM-" + orderId, doneAt);
        flow("DEPOSIT", "UNFREEZE", deposit, demandId, factoryId, orderId, "OPS-DEP-U-" + quoteId, doneAt);
        flow("BUYER_DEPOSIT", "UNFREEZE", deposit, demandId, buyerId, orderId, "OPS-BDEP-U-" + demandId, doneAt);
        credit(factoryId, lateDeliver ? "PUNCTUAL_LATE" : "PUNCTUAL_ON", lateDeliver ? -1 : 1, "STAGE", stageId,
                lateDeliver ? "超过硬交期交付" : "按硬交期交付", deliverAt);
        credit(factoryId, "QUALITY_PASS", 2, "INSPECTION", inspId, "批次质检合格", inspectAt);
        credit(buyerId, "PAY_ON_TIME", 1, "STAGE", stageId, "质检通过后完成阶段托管", payAt);
        credit(factoryId, "ORDER_COMPLETE", 2, "ORDER", orderId, "整单完工", doneAt);
        applyMoney(buyerId, inspectFee.add(total).negate());
        applyMoney(factoryId, wages);
        applyMoney(platformId, inspectFee.add(commission));
        jdbc.update("INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES (?,?,?,?,1,?)",
                buyerId, demandId, "订单已完成#" + demandId, "需求「" + title + "」已结算。", ts(doneAt));
        jdbc.update("INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES (?,?,?,?,1,?)",
                factoryId, demandId, "工钱已入账#" + demandId, "工钱 " + wages.toPlainString() + " 元已入账。", ts(doneAt));
    }

    private void seedCancelled(String title, long buyerId, LocalDateTime t0, String reason) {
        insertDemand(buyerId, title, "试制件", "通用零件", "45#", 80, "±0.05", "取消样例",
                "AQL", new BigDecimal("3"), new BigDecimal("4000"), "CANCELLED",
                t0, t0.plusHours(6), null, null, t0.plusDays(2), t0.toLocalDate().plusDays(20),
                "NONE", reason);
        jdbc.update("UPDATE demand SET cancel_reason=? WHERE title=?", reason, title);
        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES ((SELECT id FROM demand WHERE title=? LIMIT 1),1,'粗车','取消',?)",
                title, ts(t0));
    }

    private void seedFlowFailed(String title, long buyerId, LocalDateTime t0) {
        insertDemand(buyerId, title, "特殊件", "难加工件", "钛合金", 20, "±0.02", "流单样例",
                "FULL", new BigDecimal("12"), new BigDecimal("18000"), "FLOW_FAILED",
                t0, t0.plusHours(4), null, null, t0.plusDays(6), t0.toLocalDate().plusDays(12),
                "NONE", "意向期结束无有效报名");
        jdbc.update("UPDATE demand SET cancel_reason='意向期结束无有效报名' WHERE title=?", title);
        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES ((SELECT id FROM demand WHERE title=? LIMIT 1),1,'精铣','流单',?)",
                title, ts(t0));
    }

    private void seedReturned(String title, String product, long buyerId, LocalDateTime t0) {
        long id = insertDemand(buyerId, title, product, "汽车传动件", "20CrMnTi", 500, "版本冲突", "请核对图纸版次",
                "AQL", new BigDecimal("4"), new BigDecimal("22000"), "RETURNED",
                t0, null, null, null, t0, t0.toLocalDate().plusDays(30),
                "NONE", "图纸版次与零件号不一致，请修改后重提。");
        jdbc.update("UPDATE demand SET return_reason='图纸版次与零件号不一致，请修改后重提。' WHERE id=?", id);
        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES (?,?,?,?,?)",
                id, 1, "精磨", "待补充关键尺寸", ts(t0));
    }

    private long seedSimple(String title, String product, String category, String material, int qty, long buyerId,
                            String status, LocalDateTime created, LocalDateTime published, String remark,
                            int processCount, String processName) {
        LocalDateTime pub = published;
        LocalDateTime intentionEnd = pub == null ? null : pub.plusDays(5);
        BigDecimal est = BigDecimal.valueOf(qty * 40L).setScale(2, RoundingMode.HALF_UP);
        long id = insertDemand(buyerId, title, product, category, material, qty, "按图纸", remark,
                "AQL", new BigDecimal("4"), est, status, created, pub, null, null, created.plusHours(1),
                (pub == null ? created.toLocalDate() : pub.toLocalDate()).plusDays(35),
                "NONE", remark);
        if (intentionEnd != null) {
            jdbc.update("UPDATE demand SET intention_end_at=? WHERE id=?", ts(intentionEnd), id);
        }
        jdbc.update("UPDATE demand SET multi_process=? WHERE id=?", processCount > 1 ? 1 : 0, id);
        for (int i = 1; i <= processCount; i++) {
            String pn = processCount == 1 ? processName : (i == 1 ? "粗加工" : processName);
            jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES (?,?,?,?,?)",
                    id, i, pn, "按图纸执行第 " + i + " 序", ts(created));
        }
        return id;
    }

    private long bid(long demandId, long factoryId, int processNo, BigDecimal unit, int qty, LocalDateTime at) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        return insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,0.9800,20,?,?,?,'FROZEN','NONE','INTENTION',?,?,0)
                """, factoryId, demandId, processNo, unit, total, qty / 3, qty, "可承接，意向金已冻结。", ts(at), ts(at));
    }

    private long bidWinPrep(long demandId, long factoryId, int processNo, BigDecimal unit, int qty, LocalDateTime at) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        return insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,0.9800,22,?,?,?,'FROZEN','NONE','INTENTION',?,?,0)
                """, factoryId, demandId, processNo, unit, total, qty / 2, qty, "思考期填报中。", ts(at), ts(at));
    }

    private long bidCommitted(long demandId, long factoryId, int processNo, BigDecimal unit, int qty, LocalDateTime at) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        return insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,0.9850,20,?,?,?,'RELEASED','FROZEN','LOCKED',?,?,0)
                """, factoryId, demandId, processNo, unit, total, qty / 2, qty, "实施方案已提交，保证金已冻结。", ts(at), ts(at));
    }

    private void freezeIntention(long factoryId, long demandId, LocalDateTime at) {
        BigDecimal amt = new BigDecimal("1000.00");
        flow("INTENTION", "FREEZE", amt, demandId, factoryId, null, "OPS-LIVE-INT-" + demandId + "-" + factoryId, at);
        jdbc.update("UPDATE account SET balance = balance - ?, frozen = frozen + ? WHERE tenant_id = ?", amt, amt, factoryId);
    }

    private void freezeDeposit(long factoryId, long demandId, BigDecimal amt, LocalDateTime at) {
        flow("DEPOSIT", "FREEZE", amt, demandId, factoryId, null, "OPS-LIVE-DEP-" + demandId + "-" + factoryId, at);
        jdbc.update("UPDATE account SET balance = balance - ?, frozen = frozen + ? WHERE tenant_id = ?", amt, amt, factoryId);
    }

    private void insertSolution(long demandId, long factoryId, String factoryName, int qty, BigDecimal unit,
                                 String status, LocalDateTime at) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        String combo = comboJson(factoryId, factoryName, 1, qty, unit, total);
        insert("""
                INSERT INTO solution (demand_id, type, suggested_combo_json, final_combo_json, source, is_final,
                  score, status, rationale_json, created_at)
                VALUES (?,'AI1',?,?, 'AI', ?, 86.0, ?, '{"summary":"按报价与产能匹配生成。"}', ?)
                """, demandId, combo, combo, "ACTIVE".equals(status) ? 1 : 0, status, ts(at));
    }

    private void seedFulfill(String title, String product, String category, String material, int qty,
                             BigDecimal unit, long buyerId, long factoryId, String factoryName, String processName,
                             String demandStatus, String escrow, String stageStatus, int progress,
                             LocalDateTime t0, boolean delivered, boolean fail) {
        BigDecimal total = unit.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal commission = total.multiply(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal inspectFee = new BigDecimal("4").multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
        LocalDateTime signed = t0.plusDays(6);
        LocalDateTime start = signed.plusDays(1);
        LocalDateTime end = start.plusDays(18);
        long demandId = insertDemand(buyerId, title, product, category, material, qty, "按图纸", title,
                "AQL", new BigDecimal("4"), total, demandStatus, t0, t0.plusDays(1), t0.plusDays(3), t0.plusDays(4),
                signed, t0.toLocalDate().plusDays(40), "RELEASED", "履约样例");
        jdbc.update("INSERT INTO process (demand_id, process_no, process_name, requirement, created_at) VALUES (?,?,?,?,?)",
                demandId, 1, processName, "按图纸", ts(t0));
        insert("""
                INSERT INTO quotation (tenant_id, demand_id, process_no, unit_price, price, yield_rate, promised_days,
                  min_qty, max_qty, plan_text, intention_status, deposit_status, status, created_at, updated_at, deleted)
                VALUES (?,?,1,?,?,0.98,20,?,?,?,'RELEASED','RELEASED','WIN',?,?,0)
                """, factoryId, demandId, unit, total, qty / 2, qty, "已中标履约。", ts(t0.plusDays(3)), ts(signed));
        String combo = comboJson(factoryId, factoryName, 1, qty, unit, total);
        long solutionId = insert("""
                INSERT INTO solution (demand_id, type, suggested_combo_json, final_combo_json, source, is_final,
                  score, status, rationale_json, created_at)
                VALUES (?,'AI1',?,?, 'FINAL', 1, 87.0, 'ACTIVE', '{"summary":"已选定该厂。"}', ?)
                """, demandId, combo, combo, ts(t0.plusDays(5)));
        String orderStatus = "CONTRACTED".equals(demandStatus) ? "CONTRACTED" : "IN_PRODUCTION";
        long orderId = insert("""
                INSERT INTO `order` (demand_id, solution_id, total_amount, commission_rate, commission_amount,
                  status, created_at, updated_at, deleted)
                VALUES (?,?,?,0.0100,?,?,?,?,0)
                """, demandId, solutionId, total, commission, orderStatus, ts(signed), ts(LocalDateTime.now()));
        String contractStatus = "CONTRACTED".equals(demandStatus) ? "DRAFT" : "SIGNED";
        jdbc.update("""
                INSERT INTO contract (order_id, tenant_id, version, status, buyer_read, factory_read, signed_at, created_at)
                VALUES (?, ?, 1, ?, 1, ?, ?, ?)
                """, orderId, factoryId, contractStatus,
                "SIGNED".equals(contractStatus) ? 1 : 0, "SIGNED".equals(contractStatus) ? ts(signed) : null, ts(signed.minusHours(6)));
        String feeStatus = "PENDING_INSPECTION".equals(stageStatus) || "FAIL".equals(stageStatus) || "CLOSED".equals(stageStatus)
                ? "PAID" : "NONE";
        int deliveredQty = delivered ? qty : Math.max(1, qty * progress / 100);
        long stageId = insert("""
                INSERT INTO work_stage (order_id, tenant_id, process_no, process_name, quantity, promised_days,
                  promised_date, amount, escrow_status, actual_progress, status, pay_amount, delivered_qty, period_no,
                  period_start, period_end, inspect_fee_status, inspect_fee_amount, inspect_fee_payer, inspect_kind,
                  fai_status, created_at, updated_at, deleted)
                VALUES (?,?,1,?,?,20,?,?,?,?,?,?,?,1,?,?,?,?, 'BUYER', 'LOT', 'NONE', ?, ?, 0)
                """, orderId, factoryId, processName, qty, Date.valueOf(end.toLocalDate()), total, escrow, progress,
                stageStatus, "CLOSED".equals(stageStatus) ? BigDecimal.ZERO : total, deliveredQty,
                ts(start), ts(end), feeStatus, inspectFee, ts(signed), ts(LocalDateTime.now()));
        if (progress > 0) {
            jdbc.update("INSERT INTO stage_progress_log (stage_id, done_qty, progress, remark, created_at) VALUES (?,?,?,?,?)",
                    stageId, deliveredQty, progress, "生产进度上报", ts(start.plusDays(5)));
        }
        if ("FAIL".equals(stageStatus)) {
            String report = "{\"sampleCount\":32,\"failCount\":6,\"criticalFailCount\":0,\"generalFailCount\":6,\"deliveredQty\":"
                    + qty + ",\"quantityOk\":true,\"toleranceOk\":false,\"actualYield\":0.91,\"inspectMode\":\"AQL\",\"remark\":\"平面度超差\"}";
            insert("INSERT INTO inspection (stage_id, inspector_tenant_id, result, report_json, status, created_at) VALUES (?,?, 'FAIL', ?, 'VALID', ?)",
                    stageId, inspectorTenant(), report, ts(end.minusDays(1)));
            jdbc.update("INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES (?,?,?,?,0,?)",
                    buyerId, demandId, "请处理质检结果#" + stageId, "有不合格工单待选择让步、返工或关闭。", ts(end.minusDays(1)));
        }
        if ("CLOSED".equals(stageStatus)) {
            insert("""
                    INSERT INTO work_stage (order_id, tenant_id, process_no, process_name, quantity, promised_days,
                      promised_date, amount, escrow_status, actual_progress, status, pay_amount, delivered_qty, period_no,
                      period_start, period_end, inspect_fee_status, inspect_fee_amount, inspect_fee_payer, created_at, updated_at, deleted)
                    VALUES (?,?,1,?,?,20,?,?, 'NONE', 0, 'CLOSED', 0, 0, 2, ?, ?, 'NONE', 0, 'BUYER', ?, ?, 0)
                    """, orderId, factoryId, processName, qty / 2, Date.valueOf(end.plusDays(15).toLocalDate()),
                    total.divide(new BigDecimal("2"), 2, RoundingMode.HALF_UP), ts(end), ts(end.plusDays(15)),
                    ts(end.minusDays(2)), ts(LocalDateTime.now()));
            jdbc.update("INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES (?,?,?,?,0,?)",
                    factoryId, demandId, "本段已关闭#" + stageId, "买家已关闭本阶段及后续工期。", ts(end.minusDays(2)));
        }
        if ("PENDING_INSPECTION".equals(stageStatus)) {
            jdbc.update("INSERT INTO notify (tenant_id, demand_id, title, content, is_read, created_at) VALUES (?,?,?,?,0,?)",
                    inspectorTenant(), demandId, "待审核质检单#" + stageId, "有工单待检验。", ts(end.minusHours(6)));
        }
    }

    private long insertDemand(long buyerId, String title, String product, String category, String material, int qty,
                              String tolerance, String remark, String inspectMode, BigDecimal inspectUnit,
                              BigDecimal estimated, String status, LocalDateTime created, LocalDateTime published,
                              LocalDateTime factoryThink, LocalDateTime buyerThink, LocalDateTime updated,
                              LocalDate hard, String buyerDeposit, String remark2) {
        return insert("""
                INSERT INTO demand (tenant_id, title, product_name, category, quantity, material, tolerance,
                  surface_treatment, aql, certification, min_yield, min_credit_score, deadline_hard,
                  delivery_address, packaging, multi_process, weight_json, intention_days, remark,
                  inspect_mode, inspect_price, general_tolerance, part_revision, extra_json, delivery_times, estimated_total,
                  buyer_deposit_status, status, published_at, factory_thinking_at, factory_thinking_end_at,
                  buyer_thinking_at, buyer_thinking_end_at, created_at, updated_at, deleted)
                VALUES (?,?,?,?,?,?,?,?,?,?,0.9800,65,?,
                  '杭州市余杭区仓前街道文一西路 1500 号 成品库','防锈油封+隔层纸+木箱',0,
                  '{"cost":0.34,"time":0.33,"quality":0.33}',5,?,
                  ?, ?, 'ISO 2768-m', ?, '{"roughness":"Ra1.6"}', 1, ?,
                  ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, buyerId, title, product, category, qty, material, tolerance,
                "防锈油", "1.0", "ISO9001,IATF16949", Date.valueOf(hard),
                remark2 == null ? remark : remark2,
                inspectMode, inspectUnit, product + "-A", estimated,
                buyerDeposit, status,
                ts(published), ts(factoryThink), factoryThink == null ? null : ts(factoryThink.plusHours(24)),
                ts(buyerThink), buyerThink == null ? null : ts(buyerThink.plusHours(24)),
                ts(created), ts(updated));
    }

    private String comboJson(long factoryId, String factoryName, int processNo, int qty, BigDecimal unit, BigDecimal total) {
        return "[{\"factoryId\":" + factoryId + ",\"factoryName\":\"" + factoryName + "\",\"processNo\":"
                + processNo + ",\"quantity\":" + qty + ",\"unitPrice\":" + unit.toPlainString()
                + ",\"price\":" + total.toPlainString() + "}]";
    }

    private void refreshCredits(long[] buyers, long[] factories) {
        int[] bs = {82, 80, 78, 76, 84, 75, 88, 77, 74, 79};
        int[] fs = {87, 73, 81, 79, 76, 82, 80, 74, 85, 72};
        for (int i = 0; i < buyers.length; i++) {
            jdbc.update("UPDATE enterprise SET credit_score = ? WHERE id = ?", bs[i], buyers[i]);
        }
        for (int i = 0; i < factories.length; i++) {
            jdbc.update("UPDATE enterprise SET credit_score = ? WHERE id = ?", fs[i], factories[i]);
        }
    }

    private long[] phones(String... phones) {
        long[] ids = new long[phones.length];
        for (int i = 0; i < phones.length; i++) {
            ids[i] = tenantByPhone(phones[i]);
        }
        return ids;
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
                "SELECT tenant_id FROM sys_user WHERE role = 'INSPECTION' ORDER BY id DESC",
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
        return t == null ? null : Timestamp.valueOf(t);
    }
}

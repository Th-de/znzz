package com.dsh.platform.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 必须在 {@code @Scheduled} 和工单查询之前补列。
 * 用 {@link PostConstruct} 而不是 CommandLineRunner：调度任务会在 Runner 之前第一次执行。
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class SchemaPatcher {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void patch() {
        patchColumn("sys_user", "password_plain",
                "ALTER TABLE sys_user ADD COLUMN password_plain VARCHAR(64) NULL COMMENT '运营端可见登录密码' AFTER password");
        patchColumn("sys_user", "avatar_id",
                "ALTER TABLE sys_user ADD COLUMN avatar_id BIGINT UNSIGNED NULL COMMENT '头像附件id' AFTER real_name");
        patchColumn("work_stage", "rework_count",
                "ALTER TABLE work_stage ADD COLUMN rework_count INT DEFAULT 0 COMMENT '返工次数'");
        patchColumn("work_stage", "rework_deadline_at",
                "ALTER TABLE work_stage ADD COLUMN rework_deadline_at DATETIME NULL COMMENT '返工截止'");
        patchColumn("work_stage", "pay_amount",
                "ALTER TABLE work_stage ADD COLUMN pay_amount DECIMAL(18,2) NULL COMMENT '本段应付托管额'");
        patchColumn("work_stage", "first_yield",
                "ALTER TABLE work_stage ADD COLUMN first_yield DECIMAL(10,4) NULL COMMENT '首次检验合格比例'");
        patchColumn("work_stage", "inspect_round",
                "ALTER TABLE work_stage ADD COLUMN inspect_round INT DEFAULT 0 COMMENT '已审核质检轮次'");
        patchColumn("work_stage", "first_quantity_ok",
                "ALTER TABLE work_stage ADD COLUMN first_quantity_ok TINYINT NULL COMMENT '首次数量是否达标'");
        patchColumn("work_stage", "delivered_qty",
                "ALTER TABLE work_stage ADD COLUMN delivered_qty INT NULL COMMENT '工厂实交件数'");
        patchColumn("work_stage", "rework_reason",
                "ALTER TABLE work_stage ADD COLUMN rework_reason VARCHAR(1000) NULL COMMENT '返工原因'");
        patchColumn("work_stage", "period_no",
                "ALTER TABLE work_stage ADD COLUMN period_no INT NULL COMMENT '交付期次'");
        patchColumn("work_stage", "period_start",
                "ALTER TABLE work_stage ADD COLUMN period_start DATETIME NULL COMMENT '本期开始'");
        patchColumn("work_stage", "period_end",
                "ALTER TABLE work_stage ADD COLUMN period_end DATETIME NULL COMMENT '本期截止'");
        patchColumn("work_stage", "inspect_fee_status",
                "ALTER TABLE work_stage ADD COLUMN inspect_fee_status VARCHAR(32) NULL COMMENT '质检费 NONE/PENDING_PAY/PAID'");
        patchColumn("work_stage", "inspect_fee_amount",
                "ALTER TABLE work_stage ADD COLUMN inspect_fee_amount DECIMAL(18,2) NULL COMMENT '质检费金额'");
        patchColumn("work_stage", "inspect_fee_payer",
                "ALTER TABLE work_stage ADD COLUMN inspect_fee_payer VARCHAR(16) NULL COMMENT '质检费付款方 BUYER/FACTORY'");
        patchColumn("work_stage", "parent_stage_id",
                "ALTER TABLE work_stage ADD COLUMN parent_stage_id BIGINT NULL COMMENT '数量返工父工单'");
        patchColumn("work_stage", "rework_kind",
                "ALTER TABLE work_stage ADD COLUMN rework_kind VARCHAR(16) NULL COMMENT 'QUALITY/QTY'");
        patchColumn("demand", "inspect_price",
                "ALTER TABLE demand ADD COLUMN inspect_price DECIMAL(18,2) NULL COMMENT '质检单价元/件（已停用，费用按方式拆开算）'");
        patchColumn("work_stage", "inspect_kind",
                "ALTER TABLE work_stage ADD COLUMN inspect_kind VARCHAR(16) NULL COMMENT '本轮检验 FAI/LOT'");
        patchColumn("work_stage", "fai_status",
                "ALTER TABLE work_stage ADD COLUMN fai_status VARCHAR(16) NULL COMMENT '首件 NONE/PASS/FAIL'");
        patchColumn("notify", "demand_id",
                "ALTER TABLE notify ADD COLUMN demand_id BIGINT UNSIGNED NULL COMMENT '关联需求' AFTER tenant_id");
        patchColumn("order", "contract_issue_end_at",
                "ALTER TABLE `order` ADD COLUMN contract_issue_end_at DATETIME NULL COMMENT '合同发布截止'");
        patchColumn("order", "contract_sign_end_at",
                "ALTER TABLE `order` ADD COLUMN contract_sign_end_at DATETIME NULL COMMENT '合同签署截止'");
        widenVarchar("credit_event", "type", 32,
                "ALTER TABLE credit_event MODIFY COLUMN type VARCHAR(32) NOT NULL COMMENT '信用事件类型'");
        dropColumn("process", "quantity");
        backfillDeliveredQty();
        patchUniqueIndex("sys_user", "uk_phone",
                "ALTER TABLE sys_user ADD UNIQUE KEY uk_phone (phone)");
        patchTable("todo_ack", """
                CREATE TABLE todo_ack (
                  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
                  tenant_id BIGINT UNSIGNED NOT NULL,
                  type VARCHAR(32) NOT NULL,
                  biz_id BIGINT UNSIGNED NOT NULL,
                  created_at DATETIME NOT NULL,
                  PRIMARY KEY (id),
                  UNIQUE KEY uk_todo_ack (tenant_id, type, biz_id)
                ) COMMENT='待办已读'
                """);
        relaxAuditJsonColumns();
    }

    private void dropColumn(String table, String column) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (n == null || n == 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE `" + table + "` DROP COLUMN `" + column + "`");
            log.info("已删除 {}.{}", table, column);
        } catch (Exception e) {
            log.warn("删列 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }

    private void backfillDeliveredQty() {
        try {
            int n = jdbcTemplate.update("""
                    UPDATE work_stage w
                    JOIN (
                      SELECT l.stage_id, l.done_qty
                      FROM stage_progress_log l
                      JOIN (SELECT stage_id, MAX(id) id FROM stage_progress_log GROUP BY stage_id) t
                        ON t.id = l.id
                    ) p ON p.stage_id = w.id
                    SET w.delivered_qty = p.done_qty
                    WHERE w.delivered_qty IS NULL
                    """);
            if (n > 0) {
                log.info("已按进度日志回填实交件数 {} 条", n);
            }
        } catch (Exception e) {
            log.warn("回填实交件数失败: {}", e.getMessage());
        }
    }

    private void patchUniqueIndex(String table, String index, String sql) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*) FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?
                    """,
                    Integer.class, table, index);
            if (n != null && n > 0) {
                return;
            }
            jdbcTemplate.execute(sql);
            log.info("已为 {}.{} 补唯一索引", table, index);
        } catch (Exception e) {
            log.warn("补唯一索引 {}.{} 失败: {}", table, index, e.getMessage());
        }
    }

    private void widenVarchar(String table, String column, int minLength, String sql) {
        try {
            Integer length = jdbcTemplate.queryForObject(
                    """
                    SELECT CHARACTER_MAXIMUM_LENGTH
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?
                    """,
                    Integer.class, table, column);
            if (length != null && length >= minLength) {
                return;
            }
            jdbcTemplate.execute(sql);
            log.info("已扩展 {}.{} 至 VARCHAR({})", table, column, minLength);
        } catch (Exception e) {
            log.warn("扩展 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }

    private void patchTable(String table, String sql) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                    Integer.class, table);
            if (n != null && n > 0) {
                return;
            }
            jdbcTemplate.execute(sql);
            log.info("已建表 {}", table);
        } catch (Exception e) {
            log.warn("建表 {} 失败: {}", table, e.getMessage());
        }
    }

    /** after_json 原为 JSON 类型，中文详情不是合法 JSON，插入会静默失败导致操作日志为空。 */
    private void relaxAuditJsonColumns() {
        for (String column : new String[]{"after_json", "before_json"}) {
            try {
                String type = jdbcTemplate.queryForObject(
                        """
                        SELECT DATA_TYPE FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'audit_log' AND COLUMN_NAME = ?
                        """,
                        String.class, column);
                if (type != null && "json".equalsIgnoreCase(type)) {
                    jdbcTemplate.execute("ALTER TABLE audit_log MODIFY COLUMN `" + column + "` TEXT");
                    log.info("已将 audit_log.{} 改为 TEXT，允许写入操作详情", column);
                }
            } catch (Exception e) {
                log.warn("调整 audit_log.{} 失败: {}", column, e.getMessage());
            }
        }
    }

    private void patchColumn(String table, String column, String sql) {
        try {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                    Integer.class, table, column);
            if (n != null && n > 0) {
                return;
            }
            jdbcTemplate.execute(sql);
            log.info("已为 {}.{} 补列", table, column);
        } catch (Exception e) {
            log.warn("补列 {}.{} 失败: {}", table, column, e.getMessage());
        }
    }
}

-- ===================== 平台数据库建表脚本（v2 两阶段竞标） =====================
-- MySQL 8.x / utf8mb4 / InnoDB

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS enterprise (
  id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键=tenant_id',
  type           VARCHAR(16) NOT NULL COMMENT 'BUYER/FACTORY/INSPECTION/OPERATOR/PLATFORM',
  name           VARCHAR(128) NOT NULL COMMENT '企业名称',
  credit_code    VARCHAR(32) DEFAULT NULL COMMENT '统一社会信用代码',
  credit_score   INT NOT NULL DEFAULT 60 COMMENT '信用分',
  auth_status    VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/APPROVED/REJECTED',
  contact_name   VARCHAR(64) DEFAULT NULL COMMENT '联系人(脱敏)',
  contact_phone  VARCHAR(32) DEFAULT NULL COMMENT '电话(脱敏)',
  legal_person   VARCHAR(64) DEFAULT NULL COMMENT '法人(脱敏)',
  bank_account   VARCHAR(64) DEFAULT NULL COMMENT '银行账户(脱敏)',
  capability_json JSON DEFAULT NULL COMMENT '能力档案JSON',
  introduction   TEXT DEFAULT NULL COMMENT '企业介绍（工厂自填）',
  address        VARCHAR(255) DEFAULT NULL COMMENT '企业地址',
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted        TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_credit_code (credit_code),
  KEY idx_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表：买家/工厂/质检/平台等租户主体';

CREATE TABLE IF NOT EXISTS account (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id  BIGINT UNSIGNED NOT NULL COMMENT '企业id',
  balance    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '可用余额',
  frozen     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '冻结中',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业账户表：可用余额与冻结余额，与资金流水同步';

CREATE TABLE IF NOT EXISTS device (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id   BIGINT UNSIGNED NOT NULL COMMENT '工厂企业id',
  name        VARCHAR(64) NOT NULL COMMENT '设备名',
  model       VARCHAR(64) DEFAULT NULL COMMENT '型号',
  precision_text VARCHAR(64) DEFAULT NULL COMMENT '精度',
  parts       VARCHAR(255) DEFAULT NULL COMMENT '可加工零件',
  materials   VARCHAR(255) DEFAULT NULL COMMENT '可加工材料',
  process_names VARCHAR(255) DEFAULT NULL COMMENT '适用工序（逗号分隔）',
  daily_capacity INT DEFAULT NULL COMMENT '日产能（件/天），AI 产能核算依据',
  status      VARCHAR(16) NOT NULL DEFAULT 'GOOD' COMMENT 'GOOD良好/FAULT故障',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工厂设备表：能力设备清单，报名勾选，开工/完工状态流转';

CREATE TABLE IF NOT EXISTS sys_user (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id   BIGINT UNSIGNED NOT NULL COMMENT '所属企业id',
  phone       VARCHAR(20) NOT NULL,
  password    VARCHAR(128) NOT NULL COMMENT 'BCrypt',
  password_plain VARCHAR(64) DEFAULT NULL COMMENT '运营端可见登录密码',
  role        VARCHAR(16) NOT NULL COMMENT 'SUPER_ADMIN/OPERATOR/INSPECTION/BUYER/FACTORY',
  real_name   VARCHAR(64) DEFAULT NULL,
  status      VARCHAR(16) NOT NULL DEFAULT 'ENABLED',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_phone (phone),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表：登录账号，关联企业 tenant_id';

CREATE TABLE IF NOT EXISTS role_permission (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  role        VARCHAR(16) NOT NULL,
  permission  VARCHAR(64) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_role_perm (role, permission)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限表：角色与权限点映射';

CREATE TABLE IF NOT EXISTS demand (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id        BIGINT UNSIGNED NOT NULL COMMENT '买家企业id',
  title            VARCHAR(128) NOT NULL,
  product_name     VARCHAR(128) NOT NULL,
  category         VARCHAR(64) DEFAULT NULL,
  quantity         INT NOT NULL,
  material         VARCHAR(128) DEFAULT NULL,
  tolerance        VARCHAR(64) DEFAULT NULL,
  surface_treatment VARCHAR(128) DEFAULT NULL,
  aql              VARCHAR(32) DEFAULT NULL COMMENT '质量门槛',
  certification    VARCHAR(128) DEFAULT NULL COMMENT '质量门槛',
  min_yield        DECIMAL(5,4) DEFAULT NULL COMMENT '质量门槛-最低良率',
  min_credit_score INT DEFAULT NULL COMMENT '质量门槛-最低信用分',
  deadline_hard    DATE DEFAULT NULL COMMENT '硬交期',
  deadline_flexible DATE DEFAULT NULL,
  delivery_address VARCHAR(256) DEFAULT NULL,
  packaging        VARCHAR(128) DEFAULT NULL,
  multi_process    TINYINT NOT NULL DEFAULT 0,
  weight_json      JSON DEFAULT NULL COMMENT '{cost,time,quality}',
  intention_days   INT NOT NULL DEFAULT 5 COMMENT '意向期天数(买家定)',
  remark           VARCHAR(512) DEFAULT NULL,
  inspect_mode     VARCHAR(16) DEFAULT NULL COMMENT 'FAI/AQL/FULL',
  general_tolerance VARCHAR(32) DEFAULT NULL COMMENT '一般公差标准如 ISO 2768-m',
  part_revision    VARCHAR(64) DEFAULT NULL COMMENT '图号/版本',
  extra_json       JSON DEFAULT NULL COMMENT 'Ra/热处理等扩展',
  return_reason    VARCHAR(512) DEFAULT NULL COMMENT '运营退回原因',
  cancel_reason    VARCHAR(512) DEFAULT NULL COMMENT '买家取消原因',
  source_demand_id BIGINT UNSIGNED DEFAULT NULL COMMENT '取消后重发时的来源需求',
  intention_end_at DATETIME DEFAULT NULL COMMENT '意向期截止',
  factory_thinking_end_at DATETIME DEFAULT NULL COMMENT '工厂思考期截止',
  buyer_thinking_end_at   DATETIME DEFAULT NULL COMMENT '买家思考期截止',
  thinking_end_at  DATETIME DEFAULT NULL COMMENT '旧流程思考期截止',
  review_end_at    DATETIME DEFAULT NULL COMMENT '旧流程审核期截止',
  locking_end_at   DATETIME DEFAULT NULL COMMENT '旧流程保证金期截止',
  published_at     DATETIME DEFAULT NULL COMMENT '审核通过/进入意向期时间',
  factory_thinking_at DATETIME DEFAULT NULL COMMENT '进入工厂思考期时间',
  buyer_thinking_at   DATETIME DEFAULT NULL COMMENT '进入买家思考期时间',
  delivery_times   INT DEFAULT 1 COMMENT '分期交付次数（买家发布时定）',
  delivery_plan_json TEXT DEFAULT NULL COMMENT '每期交付要求（买家填，JSON数组）',
  estimated_total  DECIMAL(14,2) DEFAULT NULL COMMENT '预估总价（买家保证金计费基数）',
  buyer_deposit_status VARCHAR(20) NOT NULL DEFAULT 'NONE' COMMENT '买家保证金状态 NONE/FROZEN/DEDUCTED/RELEASED',
  status           VARCHAR(24) NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PENDING_AUDIT/PUBLISHED/RETURNED/FACTORY_THINKING/BUYER_THINKING/SOLUTION_GENERATED/SOLUTION_CONFIRMED/SOLUTION_SELECTED/CONTRACTED/IN_PRODUCTION/COMPLETED/CANCELLED/FLOW_FAILED',
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted          TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表：买家发布的加工需求及状态机阶段';

CREATE TABLE IF NOT EXISTS process (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  demand_id    BIGINT UNSIGNED NOT NULL,
  process_no   INT NOT NULL,
  process_name VARCHAR(64) NOT NULL,
  quantity     INT NOT NULL,
  requirement  VARCHAR(512) DEFAULT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工序表：需求拆分的加工工序及数量要求';

CREATE TABLE IF NOT EXISTS attachment (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  biz_type    VARCHAR(32) NOT NULL,
  biz_id      BIGINT UNSIGNED NOT NULL,
  file_name   VARCHAR(256) NOT NULL,
  file_path   VARCHAR(512) NOT NULL,
  file_size   BIGINT DEFAULT NULL,
  file_type   VARCHAR(32) DEFAULT NULL,
  uploader_id BIGINT UNSIGNED DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_biz (biz_type, biz_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='附件表：需求图纸、合同文件等上传附件';

CREATE TABLE IF NOT EXISTS quotation (
  id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id        BIGINT UNSIGNED NOT NULL COMMENT '工厂企业id',
  demand_id        BIGINT UNSIGNED NOT NULL,
  process_no       INT NOT NULL,
  intention_price  DECIMAL(18,2) DEFAULT NULL COMMENT '意向报价(非绑定)',
  unit_price       DECIMAL(12,2) DEFAULT NULL COMMENT '单件报价（工厂思考期填）',
  price            DECIMAL(18,2) DEFAULT NULL COMMENT '总报价=单价×承接量(绑定)',
  yield_rate       DECIMAL(5,4) DEFAULT NULL COMMENT '良率承诺',
  promised_days    INT DEFAULT NULL COMMENT '总工期(天)',
  min_qty          INT DEFAULT NULL COMMENT '最小承接量',
  max_qty          INT DEFAULT NULL COMMENT '最大承接量',
  stage_curve_json JSON DEFAULT NULL COMMENT '分阶段工期曲线',
  plan_text        TEXT DEFAULT NULL COMMENT '实施方案（工厂思考期填）',
  delivery_plan_json TEXT DEFAULT NULL COMMENT '每期交付内容（工厂填，期数由买家定）',
  valid_days       INT DEFAULT 7 COMMENT '报价有效期(意向期已停用)',
  extra_json       JSON DEFAULT NULL COMMENT '意向报名快照:产能/设备/认证',
  device_ids_json  VARCHAR(512) DEFAULT NULL COMMENT '报名勾选的设备id列表JSON',
  intention_status VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING_PAY/FROZEN/RELEASED/FORFEITED',
  deposit_status   VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/FROZEN/RELEASED/FORFEITED',
  status           VARCHAR(16) NOT NULL DEFAULT 'INTENTION' COMMENT 'INTENTION/LOCKED/WIN/LOSE/INVALID',
  version          INT NOT NULL DEFAULT 1,
  created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted          TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_demand (demand_id),
  KEY idx_tenant (tenant_id),
  KEY idx_demand_process (demand_id, process_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='报价/报名表：工厂意向报名、锁价与保证金状态';

CREATE TABLE IF NOT EXISTS solution (
  id                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  demand_id            BIGINT UNSIGNED NOT NULL,
  type                 VARCHAR(16) NOT NULL COMMENT 'A/B/C/AI1/AI2/AI3',
  suggested_combo_json JSON DEFAULT NULL,
  final_combo_json     JSON DEFAULT NULL,
  edited_fields_json   JSON DEFAULT NULL,
  source               VARCHAR(16) NOT NULL DEFAULT 'SUGGESTED',
  is_final             TINYINT NOT NULL DEFAULT 0,
  score                DECIMAL(10,2) DEFAULT NULL,
  edited_by            BIGINT UNSIGNED DEFAULT NULL,
  edited_at            DATETIME DEFAULT NULL,
  status               VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE可见 / PENDING_REVIEW仅运营',
  rationale_json       TEXT DEFAULT NULL COMMENT 'AI/方案总述理由',
  created_at           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_demand (demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='方案表：规则/AI 编排的工厂组合方案';

CREATE TABLE IF NOT EXISTS `order` (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  demand_id         BIGINT UNSIGNED NOT NULL,
  solution_id       BIGINT UNSIGNED NOT NULL,
  total_amount      DECIMAL(18,2) NOT NULL,
  commission_rate   DECIMAL(5,4) NOT NULL DEFAULT 0.0100,
  commission_amount DECIMAL(18,2) NOT NULL,
  status            VARCHAR(24) NOT NULL DEFAULT 'CREATED',
  created_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted           TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_demand (demand_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表：方案选定后的履约主单';

CREATE TABLE IF NOT EXISTS contract (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id            BIGINT UNSIGNED NOT NULL,
  tenant_id           BIGINT UNSIGNED NOT NULL COMMENT '中标工厂企业id，一厂一份',
  version             INT NOT NULL DEFAULT 1,
  sampling_json       JSON DEFAULT NULL,
  responsibility_json JSON DEFAULT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  hash                CHAR(64) DEFAULT NULL,
  attachment_id       BIGINT UNSIGNED DEFAULT NULL COMMENT '买家自拟合同附件',
  buyer_read          TINYINT NOT NULL DEFAULT 0,
  buyer_sign          TEXT DEFAULT NULL COMMENT '买家签名图',
  factory_read        TINYINT NOT NULL DEFAULT 0,
  factory_sign        TEXT DEFAULT NULL COMMENT '工厂签名图',
  factory_signs_json  JSON DEFAULT NULL,
  signed_at           DATETIME DEFAULT NULL,
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_order_factory (order_id, tenant_id),
  KEY idx_order (order_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合同表：一厂一份，买家上传正文，双方签名与平台审核';

CREATE TABLE IF NOT EXISTS work_stage (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id        BIGINT UNSIGNED NOT NULL,
  tenant_id       BIGINT UNSIGNED NOT NULL COMMENT '工厂企业id',
  process_no      INT NOT NULL,
  process_name    VARCHAR(64) NOT NULL,
  quantity        INT NOT NULL,
  promised_days   INT NOT NULL,
  promised_date   DATE DEFAULT NULL,
  amount          DECIMAL(18,2) DEFAULT NULL COMMENT '本段工钱',
  escrow_status   VARCHAR(16) NOT NULL DEFAULT 'NONE' COMMENT 'NONE/PENDING_PAY/HELD/SETTLED',
  actual_progress INT NOT NULL DEFAULT 0,
  status          VARCHAR(24) NOT NULL DEFAULT 'PENDING',
  created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_order (order_id),
  KEY idx_tenant (tenant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单表：按工序/分段拆出的履约执行单元';

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单进度上报日志：工厂按件上报进度与说明';

CREATE TABLE IF NOT EXISTS inspection (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stage_id            BIGINT UNSIGNED NOT NULL,
  inspector_tenant_id BIGINT UNSIGNED NOT NULL,
  result              VARCHAR(16) NOT NULL COMMENT 'PASS/FAIL',
  report_json         JSON DEFAULT NULL,
  hash                CHAR(64) DEFAULT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'VALID',
  created_at          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_stage (stage_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质检表：工单质检结果与抽样记录';

CREATE TABLE IF NOT EXISTS fund_flow (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  order_id      BIGINT UNSIGNED DEFAULT NULL,
  demand_id     BIGINT UNSIGNED DEFAULT NULL,
  tenant_id     BIGINT UNSIGNED NOT NULL COMMENT '相关方企业id',
  type          VARCHAR(16) NOT NULL COMMENT 'INTENTION/DEPOSIT/PAYMENT/REFUND/PENALTY/COMMISSION/ESCROW',
  direction     VARCHAR(16) NOT NULL COMMENT 'IN/OUT/FREEZE/UNFREEZE',
  amount        DECIMAL(18,2) NOT NULL,
  status        VARCHAR(16) NOT NULL DEFAULT 'SUCCESS',
  idempotent_no VARCHAR(64) NOT NULL,
  created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_idempotent (idempotent_no),
  KEY idx_order (order_id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资金流水表：意向金、保证金、托管、罚没、佣金等唯一账本';

CREATE TABLE IF NOT EXISTS credit_event (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    BIGINT UNSIGNED NOT NULL,
  type         VARCHAR(16) NOT NULL,
  score_change INT NOT NULL,
  ref_type     VARCHAR(32) DEFAULT NULL,
  ref_id       BIGINT UNSIGNED DEFAULT NULL,
  remark       VARCHAR(256) DEFAULT NULL,
  created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='信用事件表：加分/扣分记录';

CREATE TABLE IF NOT EXISTS survey (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  stage_id    BIGINT UNSIGNED NOT NULL,
  order_id    BIGINT UNSIGNED NOT NULL,
  tenant_id   BIGINT UNSIGNED NOT NULL,
  role        VARCHAR(16) NOT NULL COMMENT 'BUYER/FACTORY',
  scores_json JSON NOT NULL COMMENT '{q1,q2,q3,q4} 1-5',
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_stage_role (stage_id, role, tenant_id),
  KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阶段问卷表：买家与工厂互评打分';

CREATE TABLE IF NOT EXISTS audit_log (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  actor_id    BIGINT UNSIGNED NOT NULL,
  action      VARCHAR(64) NOT NULL,
  target_type VARCHAR(32) DEFAULT NULL,
  target_id   BIGINT UNSIGNED DEFAULT NULL,
  before_json JSON DEFAULT NULL,
  after_json  JSON DEFAULT NULL,
  hash        CHAR(64) DEFAULT NULL,
  created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_actor (actor_id),
  KEY idx_target (target_type, target_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志表：关键操作留痕';

CREATE TABLE IF NOT EXISTS notify (
  id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id  BIGINT UNSIGNED NOT NULL,
  demand_id  BIGINT UNSIGNED DEFAULT NULL COMMENT '关联需求，便于按需求聚合通知',
  title      VARCHAR(128) NOT NULL,
  content    VARCHAR(512) DEFAULT NULL,
  is_read    TINYINT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_tenant (tenant_id),
  KEY idx_tenant_demand (tenant_id, demand_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内信表：系统通知推送';

-- ===================== 初始化超级管理员 =====================
-- 密码为 BCrypt 加密后的 "admin123"（需后端生成，此处占位由代码初始化）

-- 清空业务数据后由后端 Seeder 重建仿真演示数据（utf8mb4）
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE stage_progress_log;
TRUNCATE TABLE survey;
TRUNCATE TABLE inspection;
TRUNCATE TABLE work_stage;
TRUNCATE TABLE contract;
TRUNCATE TABLE `order`;
TRUNCATE TABLE solution;
TRUNCATE TABLE quotation;
TRUNCATE TABLE fund_flow;
TRUNCATE TABLE credit_event;
TRUNCATE TABLE notify;
TRUNCATE TABLE audit_log;
TRUNCATE TABLE attachment;
TRUNCATE TABLE process;
TRUNCATE TABLE demand;
TRUNCATE TABLE device;
TRUNCATE TABLE account;
TRUNCATE TABLE role_permission;
TRUNCATE TABLE sys_user;
TRUNCATE TABLE enterprise;

SET FOREIGN_KEY_CHECKS = 1;

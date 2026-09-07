/**
 * 展示格式化工具：状态中文标签 / 时间 / 金额等。
 * 状态字典与后端前端(client-web/src/utils/labels.js)保持一致。
 * 说明：ArkTS 不支持 index signature，故使用 Map<string,string>。
 */

/** 由扁平数组构造字典：['KEY','值','KEY2','值2',...] */
function mapOf(kv: string[]): Map<string, string> {
  const m: Map<string, string> = new Map<string, string>();
  for (let i = 0; i + 1 < kv.length; i += 2) {
    m.set(kv[i], kv[i + 1]);
  }
  return m;
}

export const ORDER_STATUS: Map<string, string> = mapOf([
  'CREATED', '待签约', 'CONTRACTED', '已签约', 'IN_PRODUCTION', '生产中',
  'DELIVERED', '已交付', 'ACCEPTED', '已验收', 'COMPLETED', '已完成',
  'CANCELLED', '已取消', 'DISPUTE', '争议中'
]);

export const DEMAND_STATUS: Map<string, string> = mapOf([
  'DRAFT', '草稿', 'PENDING_AUDIT', '申请发布', 'PUBLISHED', '意向期',
  'RETURNED', '退回修改', 'FACTORY_THINKING', '工厂思考期', 'BUYER_THINKING', '买家思考期',
  'THINKING', '思考期(旧)', 'REVIEWING', '审核期(旧)', 'LOCKING', '保证金期(旧流程)',
  'SOLUTION_GENERATED', '方案核定期', 'SOLUTION_CONFIRMED', '已确认待派单',
  'SOLUTION_SELECTED', '方案已选定', 'CONTRACTED', '已签约', 'IN_PRODUCTION', '生产中',
  'COMPLETED', '已完成', 'CANCELLED', '已取消', 'FLOW_FAILED', '流拍'
]);

export const QUOTE_STATUS: Map<string, string> = mapOf([
  'INTENTION', '已报名', 'LOCKED', '已填报方案', 'WIN', '中标',
  'LOSE', '未中标', 'INVALID', '已取消'
]);

export const INTENTION_STATUS: Map<string, string> = mapOf([
  'NONE', '未冻结', 'PENDING_PAY', '待支付意向金', 'FROZEN', '已冻结',
  'COVERED', '同单已缴', 'RELEASED', '已退还', 'FORFEITED', '已罚没'
]);

export const STAGE_STATUS: Map<string, string> = mapOf([
  'PENDING', '待启动', 'IN_PRODUCTION', '生产中', 'PENDING_INSPECTION', '待质检',
  'INSPECTING', '质检中', 'PASS', '合格', 'FAIL', '不合格', 'COMPLETED', '已完成'
]);

export const ESCROW_STATUS: Map<string, string> = mapOf([
  'NONE', '未托管', 'PENDING_PAY', '待支付宝支付', 'HELD', '已托管', 'SETTLED', '已结算'
]);

export const CONTRACT_STATUS: Map<string, string> = mapOf([
  'DRAFT', '待上传/签署', 'PENDING_REVIEW', '待平台确认', 'SIGNED', '已确认'
]);

const ROLE_MAP: Map<string, string> = mapOf([
  'BUYER', '需求方（买家）', 'FACTORY', '工厂', 'OPERATOR', '平台运营',
  'SUPER_ADMIN', '超级管理员', 'INSPECTION', '质检机构'
]);

/** 取字典中文标签，未知值原样返回 */
export function label(map: Map<string, string>, key: string): string {
  if (key === undefined || key === null || key === '') {
    return '-';
  }
  const v: string | undefined = map.get(key);
  return v === undefined ? key : v;
}

/** 空值兜底 */
export function str(s: string | undefined): string {
  return s === undefined || s === null || s.length === 0 ? '-' : s;
}

/** 时间字符串格式化（兼容 LocalDateTime "yyyy-MM-dd HH:mm:ss" 或带 T 的 ISO） */
export function fmtTime(s: string | undefined): string {
  if (s === undefined || s === null || s.length === 0) {
    return '-';
  }
  let t: string = s.replace('T', ' ');
  if (t.length >= 19) {
    t = t.substring(0, 19);
  }
  return t;
}

/** 金额格式化 */
export function fmtMoney(v: number | undefined): string {
  if (v === undefined || v === null || Number.isNaN(v)) {
    return '-';
  }
  return '¥' + v.toFixed(2);
}

/** 整数格式化 */
export function fmtInt(v: number | undefined): string {
  if (v === undefined || v === null || Number.isNaN(v)) {
    return '-';
  }
  return String(v);
}

/** 状态色（用于状态小圆点/文本） */
export function statusColor(status: string): string {
  if (status === undefined || status === null || status === '') {
    return '#909399';
  }
  const done: string[] = ['COMPLETED', 'WIN', 'SIGNED', 'PASS', 'SETTLED', 'ACCEPTED', 'DELIVERED'];
  const danger: string[] = ['CANCELLED', 'LOSE', 'FAIL', 'INVALID', 'DISPUTE', 'FORFEITED', 'FLOW_FAILED'];
  const warn: string[] = ['PENDING', 'PENDING_PAY', 'PENDING_INSPECTION', 'INSPECTING', 'PENDING_REVIEW',
    'FACTORY_THINKING', 'BUYER_THINKING', 'LOCKING', 'INTENTION'];
  if (done.indexOf(status) >= 0) {
    return '#07C160';
  }
  if (danger.indexOf(status) >= 0) {
    return '#E84026';
  }
  if (warn.indexOf(status) >= 0) {
    return '#F5A623';
  }
  return '#0A59F7';
}

/** 角色中文名 */
export function roleName(role: string): string {
  return label(ROLE_MAP, role);
}
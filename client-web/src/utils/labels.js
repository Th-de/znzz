export const DEMAND_STATUS = {
  DRAFT: '草稿',
  PENDING_AUDIT: '申请发布',
  PUBLISHED: '意向期',
  RETURNED: '退回修改',
  FACTORY_THINKING: '工厂思考期',
  BUYER_THINKING: '企业思考期',
  THINKING: '思考期(旧)',
  REVIEWING: '审核期(旧)',
  LOCKING: '保证金期(旧流程)',
  SOLUTION_GENERATED: '方案期',
  SOLUTION_CONFIRMED: '已确认待签合同',
  SOLUTION_SELECTED: '合同签署中',
  CONTRACTED: '已签约',
  IN_PRODUCTION: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FLOW_FAILED: '流拍',
}

export const QUOTE_STATUS = {
  INTENTION: '已报名',
  LOCKED: '已填报方案',
  WIN: '中标',
  LOSE: '未中标',
  INVALID: '已取消',
}

export const DEPOSIT_STATUS = {
  NONE: '未冻结',
  PENDING_PAY: '待支付',
  FROZEN: '已冻结',
  COVERED: '同单已计',
  RELEASED: '已退还',
  FORFEITED: '已罚没',
}

export const INTENTION_STATUS = {
  NONE: '未冻结',
  PENDING_PAY: '待支付意向金',
  FROZEN: '已冻结',
  COVERED: '同单已缴',
  RELEASED: '已退还',
  FORFEITED: '已罚没',
}

export const ORDER_STATUS = {
  CREATED: '待签约',
  CONTRACTED: '已签约',
  IN_PRODUCTION: '生产中',
  DELIVERED: '已交付',
  ACCEPTED: '已验收',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  DISPUTE: '争议中',
}

export const FUND_TYPE = {
  INTENTION: '意向金',
  DEPOSIT: '保证金',
  BUYER_DEPOSIT: '买家保证金',
  PAYMENT: '放款',
  REFUND: '退款',
  PENALTY: '罚没',
  COMMISSION: '佣金',
  INSPECT_FEE: '质检费',
  ESCROW: '托管',
  IMPOUND: '平台暂存',
}

export const ESCROW_STATUS = {
  NONE: '未托管',
  PENDING_PAY: '待支付宝支付',
  HELD: '已托管',
  SETTLED: '已结算',
}

export const STAGE_STATUS = {
  WAITING_OPEN: '待开启',
  PENDING_SIGN: '待签约',
  PENDING: '待启动',
  IN_PRODUCTION: '进行中',
  PENDING_INSPECT_PAY: '待付质检费',
  PENDING_INSPECTION: '待质检',
  PENDING_REVIEW: '待审核',
  INSPECTING: '质检中',
  PASS: '可收款',
  FAIL: '待买家处理',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
}

export const FUND_DIR = {
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  IN: '收入',
  OUT: '支出',
}

export function label(map, key) {
  return map[key] || key || '-'
}

export const DEVICE_STATUS = {
  GOOD: '良好',
  FAULT: '故障',
  IDLE: '良好',
  IN_USE: '良好',
  MAINTENANCE: '故障',
}

export function deviceStatusType(s) {
  return (s === 'FAULT' || s === 'MAINTENANCE') ? 'danger' : 'success'
}

export function fmtTime(v) {
  if (v == null || v === '') return '-'
  const s = String(v).replace('T', ' ')
  return s.length >= 19 ? s.slice(0, 19) : s
}

export function formatInspectMode(raw) {
  const s = String(raw || '').toUpperCase()
  if (s.includes('FULL')) return '全检'
  if (s.includes('AQL')) return 'AQL 抽样'
  return raw ? String(raw) : '-'
}

export function formatDeliveryPeriod(x) {
  if (x == null || x === '') return ''
  if (typeof x === 'string') return x
  let share = ''
  if (x.percent != null && Number(x.percent) > 0) {
    share = Number(x.percent) + '%'
  } else if (String(x.text || '').includes('%')) {
    share = x.text
  } else if (x.qty != null && Number(x.qty) > 0 && Number(x.qty) <= 100) {
    share = Number(x.qty) + '%'
  } else {
    share = x.text || ''
  }
  const start = String(x.startAt || '').slice(0, 10)
  const end = String(x.endAt || '').slice(0, 10)
  const range = [start, end].filter(Boolean).join(' ~ ')
  return [share, range].filter(Boolean).join('  ')
}

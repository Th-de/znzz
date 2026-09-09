export const DEMAND_STATUS = {
  DRAFT: '草稿',
  PENDING_AUDIT: '申请发布',
  PUBLISHED: '意向期',
  RETURNED: '退回修改',
  FACTORY_THINKING: '工厂思考期',
  BUYER_THINKING: '买家思考期',
  THINKING: '思考期(旧)',
  REVIEWING: '审核期(旧)',
  LOCKING: '保证金期(旧流程)',
  SOLUTION_GENERATED: '方案核定期',
  SOLUTION_CONFIRMED: '已确认待签合同',
  SOLUTION_SELECTED: '合同签署中',
  CONTRACTED: '已签约',
  IN_PRODUCTION: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  CLOSED: '已关闭',
  FLOW_FAILED: '流单',
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

export const FUND_DIR = {
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  IN: '收入',
  OUT: '支出',
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
  PENDING_INSPECT_PAY: '待交质检费',
  PENDING_INSPECTION: '质检中',
  PENDING_REVIEW: '质检中',
  INSPECTING: '质检中',
  PASS: '结算中',
  FAIL: '待买家处理',
  CLOSED: '已关闭',
  COMPLETED: '已完成',
}

export function stageProgressLabel(p) {
  if (!p) return '-'
  if (p.status === 'IN_PRODUCTION' && Number(p.reworkCount) > 0) return '返工生产中'
  if (p.status === 'PENDING_INSPECT_PAY') return '待交质检费'
  if (p.status === 'PENDING_INSPECTION' || p.status === 'PENDING_REVIEW' || p.status === 'INSPECTING') return '质检中'
  if (p.status === 'PASS' && (p.escrowStatus === 'NONE' || p.escrowStatus === 'PENDING_PAY')) return '结算中'
  if (p.status === 'PASS' && p.escrowStatus === 'HELD') return '已托管'
  if (p.status === 'PASS' && p.escrowStatus === 'SETTLED') return '已完成'
  return STAGE_STATUS[p.status] || p.status || '-'
}

export function nestReworkPeriods(list) {
  const rows = Array.isArray(list) ? [...list] : []
  const byId = new Map(rows.map((p) => [p.id, p]))
  const childrenByParent = new Map()
  const roots = []
  for (const p of rows) {
    if (p.parentStageId && byId.has(p.parentStageId)) {
      const arr = childrenByParent.get(p.parentStageId) || []
      arr.push(p)
      childrenByParent.set(p.parentStageId, arr)
    } else {
      roots.push(p)
    }
  }
  roots.sort((a, b) => (a.periodNo || 0) - (b.periodNo || 0) || (a.id || 0) - (b.id || 0))
  const out = []
  for (const r of roots) {
    out.push(r)
    const kids = (childrenByParent.get(r.id) || []).sort((a, b) => (a.id || 0) - (b.id || 0))
    const base = r.periodLabel || (r.periodNo ? ('第' + r.periodNo + '期') : '本期')
    for (const c of kids) {
      out.push({ ...c, periodLabel: String(c.periodLabel || '').includes('返工') ? c.periodLabel : (base + ' 返工') })
    }
  }
  return out
}

export function canShowInspectReport(p) {
  if (!['PASS', 'FAIL', 'CLOSED', 'COMPLETED'].includes(p?.status)) return false
  if (p.hasInspection === false) return false
  return true
}

export function factoryWorkSpan(children) {
  let start = null
  let end = null
  for (const p of children || []) {
    const s = p.periodStart
    const e = p.periodEnd || p.promisedDate
    if (s && (start == null || String(s) < String(start))) start = s
    if (e && (end == null || String(e) > String(end))) end = e
  }
  return { start, end }
}

export function label(map, key) {
  return map[key] || key || '-'
}

const STATUS_TONE = {
  DRAFT: 'slate',
  PENDING_AUDIT: 'amber',
  PUBLISHED: 'sky',
  RETURNED: 'orange',
  FACTORY_THINKING: 'indigo',
  BUYER_THINKING: 'violet',
  THINKING: 'blue',
  REVIEWING: 'cyan',
  LOCKING: 'gold',
  SOLUTION_GENERATED: 'teal',
  SOLUTION_CONFIRMED: 'mint',
  SOLUTION_SELECTED: 'purple',
  CONTRACTED: 'navy',
  IN_PRODUCTION: 'blue',
  COMPLETED: 'green',
  CANCELLED: 'stone',
  FLOW_FAILED: 'rose',
  LOSE: 'red',
  WAITING_OPEN: 'slate',
  PENDING_SIGN: 'peach',
  PENDING: 'sand',
  PENDING_INSPECT_PAY: 'tangerine',
  PENDING_INSPECTION: 'aqua',
  PENDING_REVIEW: 'aqua',
  INSPECTING: 'aqua',
  PASS: 'emerald',
  FAIL: 'red',
  CLOSED: 'stone',
  NONE: 'slate',
  PENDING_PAY: 'amber',
  HELD: 'cobalt',
  SETTLED: 'green',
  CREATED: 'peach',
  DELIVERED: 'teal',
  ACCEPTED: 'mint',
  DISPUTE: 'rose',
  GOOD: 'green',
  FAULT: 'red',
  IDLE: 'green',
  IN_USE: 'green',
  MAINTENANCE: 'red',
  PENDING_UPLOAD: 'sand',
  SIGNED: 'green',
  DISABLED: 'red',
}

export function statusTone(code) {
  return STATUS_TONE[code] || 'slate'
}

export function stageProgressTone(p) {
  if (!p) return 'slate'
  if (p.status === 'IN_PRODUCTION' && Number(p.reworkCount) > 0) return 'orange'
  if (p.status === 'PENDING_INSPECT_PAY') return 'tangerine'
  if (p.status === 'PENDING_INSPECTION' || p.status === 'PENDING_REVIEW' || p.status === 'INSPECTING') return 'aqua'
  if (p.status === 'PASS' && (p.escrowStatus === 'NONE' || p.escrowStatus === 'PENDING_PAY')) return 'emerald'
  if (p.status === 'PASS' && p.escrowStatus === 'HELD') return 'cobalt'
  if (p.status === 'PASS' && p.escrowStatus === 'SETTLED') return 'green'
  return statusTone(p.status)
}

export function formatInspectMode(raw) {
  const s = String(raw || '').toUpperCase()
  if (s.includes('FULL')) return '全数检验'
  if (s.includes('AQL')) return '抽样检验（AQL）'
  return raw ? String(raw) : '-'
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

export function fmtDateLine(v) {
  const s = fmtTime(v)
  if (s === '-') return '-'
  return s.length >= 10 ? s.slice(0, 10) : s
}

export function fmtTimeLine(v) {
  const s = fmtTime(v)
  if (s === '-' || s.length < 16) return ''
  return s.slice(11, 19)
}

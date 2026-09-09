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
  CLOSED: '已关闭',
  FLOW_FAILED: '流单',
  LOSE: '已落选',
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
  PENDING_INSPECT_PAY: '待交质检费',
  PENDING_INSPECTION: '质检中',
  PENDING_REVIEW: '质检中',
  INSPECTING: '质检中',
  PASS: '结算中',
  FAIL: '待买家处理',
  CLOSED: '已关闭',
  CANCELLED: '已取消',
  COMPLETED: '已完成',
}

export function stageProgressLabel(p) {
  if (!p) return '-'
  if (!p.contractSigned && (p.status === 'PENDING' || p.status === 'WAITING_OPEN')) return '待签约'
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

export function canPayInspectFee(p, asFactory) {
  if (p?.status !== 'PENDING_INSPECT_PAY') return false
  return asFactory ? p.inspectFeePayer === 'FACTORY' : p.inspectFeePayer !== 'FACTORY'
}

export function canPayStageLabor(p) {
  return ['PASS', 'COMPLETED'].includes(p?.status) && (p.escrowStatus === 'NONE' || p.escrowStatus === 'PENDING_PAY')
}

export const FUND_DIR = {
  FREEZE: '冻结',
  UNFREEZE: '解冻',
  IN: '收入',
  OUT: '支出',
}

export const CREDIT_TYPE = {
  STAGE_PASS: '工单质检通过',
  QUALITY_PASS: '质检合格',
  QUALITY_FAIL: '质检不合格',
  QUALITY_QTY_FAIL: '数量不足',
  QUALITY_STD_FAIL: '质量不达标',
  PUNCTUAL_ON: '按时交付',
  PUNCTUAL_LATE: '逾期交付',
  HONESTY_FACTORY_EXIT: '工厂思考期退出',
  HONESTY_OVERDUE: '逾期未交付',
  HONESTY_INSPECT_FAIL: '质检失信',
  HONESTY_CLEAN_ORDER: '整单无失信完工',
  BUYER_THINKING_CANCEL: '买家思考期取消',
  PAY_ON_TIME: '按时付款',
  PAY_LATE: '逾期付款',
  INTENTION_NO_LOCK: '报名未锁价',
  ORDER_COMPLETE: '订单完成',
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
  WIN: 'green',
  INTENTION: 'sky',
  LOCKED: 'indigo',
  INVALID: 'stone',
  FORFEITED: 'red',
  RELEASED: 'mint',
  COVERED: 'cyan',
  FROZEN: 'cobalt',
  DEDUCTED: 'navy',
  DISABLED: 'red',
}

export function statusTone(code) {
  return STATUS_TONE[code] || 'slate'
}

export function stageProgressTone(p) {
  if (!p) return 'slate'
  if (!p.contractSigned && (p.status === 'PENDING' || p.status === 'WAITING_OPEN')) return 'peach'
  if (p.status === 'IN_PRODUCTION' && Number(p.reworkCount) > 0) return 'orange'
  if (p.status === 'PENDING_INSPECT_PAY') return 'tangerine'
  if (p.status === 'PENDING_INSPECTION' || p.status === 'PENDING_REVIEW' || p.status === 'INSPECTING') return 'aqua'
  if (p.status === 'PASS' && (p.escrowStatus === 'NONE' || p.escrowStatus === 'PENDING_PAY')) return 'emerald'
  if (p.status === 'PASS' && p.escrowStatus === 'HELD') return 'cobalt'
  if (p.status === 'PASS' && p.escrowStatus === 'SETTLED') return 'green'
  return statusTone(p.status)
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

export function formatInspectMode(raw) {
  const s = String(raw || '').toUpperCase()
  if (s.includes('FULL')) return '全数检验'
  if (s.includes('AQL')) return '抽样检验（AQL）'
  return raw ? String(raw) : '-'
}

const INSPECT_DECIDE_COPY = {
  B: {
    situation: '交货数量未达到约定数量，质量检验合格。',
    settlement: '若选择让步接收：按已交数量结算工费，另从工厂保证金划转本阶段约定工费的 5%，作为数量不足的补偿。',
    options: '也可以要求工厂补交短缺数量。本情况不能关闭本阶段。',
  },
  C1: {
    situation: '交货数量已达标，但质量轻微不合格：抽样检验中一般缺陷刚好达到拒收标准，或全数检验的实际良率低于约定最低良率、且差额不超过 5 个百分点。',
    settlement: '若选择让步接收：本阶段工费按全额进入托管。抽样检验时，另从工厂保证金划转本阶段工费的 5%；全数检验时，划转金额为本阶段工费 ×（最低良率 − 实际良率）。',
    options: '也可以要求工厂返工。',
  },
  C2: {
    situation: '交货数量已达标，但质量明显不合格，已超出轻微不合格范围。',
    settlement: '本情况不能让步接收。请要求工厂返工，或关闭本阶段。',
    options: '',
  },
  D: {
    situation: '交货数量未达到约定数量，且质量检验不合格。',
    settlement: '本情况不能让步接收。可要求工厂返工一次；返工后仍不合格，则只能关闭本阶段。',
    options: '',
  },
  E: {
    situation: '存在关键尺寸超出图纸公差。',
    settlement: '本情况不能让步接收。可要求工厂返工，或关闭本阶段。返工后仍不合格，只能关闭。',
    options: '',
  },
}

export function inspectDecideCopy(stage) {
  const code = String(stage?.branchCode || '').toUpperCase()
  const fallback = {
    situation: '质检未通过，请根据下方可选操作处理。',
    settlement: '让步接收仅适用于「数量不足但质量合格」或「数量达标且质量轻微不合格」。',
    options: '',
  }
  const cannotClose = code === 'B' || stage?.canClose === false
  return {
    ...(INSPECT_DECIDE_COPY[code] || fallback),
    closeNote: cannotClose
      ? '数量不足但质量合格时不能关闭本阶段。请选择让步接收，或要求工厂补交短缺数量。返工期限按天填写，至少 1 天，不设上限。'
      : '选择「关闭本阶段」将取消该厂后续未完成工期，并从工厂履约保证金划转「本期及后续未完成期工费合计」的 5% 补偿买家。保证金不足时无法关闭。每个工单全流程仅允许返工一次，期限按天填写，不设上限，与原分期截止日期无关。',
  }
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

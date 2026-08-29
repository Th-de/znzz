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
  SOLUTION_CONFIRMED: '已确认待派单',
  SOLUTION_SELECTED: '方案已选定',
  CONTRACTED: '已签约',
  IN_PRODUCTION: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FLOW_FAILED: '流拍',
}

export const FUND_TYPE = {
  INTENTION: '意向金',
  DEPOSIT: '保证金',
  BUYER_DEPOSIT: '买家保证金',
  PAYMENT: '放款',
  REFUND: '退款',
  PENALTY: '罚没',
  COMMISSION: '佣金',
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
  PENDING: '待启动',
  IN_PRODUCTION: '生产中',
  PENDING_INSPECTION: '待质检',
  INSPECTING: '质检中',
  PASS: '合格',
  FAIL: '不合格',
  COMPLETED: '已完成',
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

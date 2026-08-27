export const DEMAND_STATUS = {
  DRAFT: '草稿',
  PENDING_AUDIT: '申请发布',
  PUBLISHED: '意向期',
  RETURNED: '退回修改',
  THINKING: '思考期',
  REVIEWING: '审核期',
  LOCKING: '保证金期',
  SOLUTION_GENERATED: '方案已生成',
  SOLUTION_CONFIRMED: '已确认待派单',
  SOLUTION_SELECTED: '方案已选定',
  CONTRACTED: '已签约',
  IN_PRODUCTION: '生产中',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
  FLOW_FAILED: '流拍',
}

export const QUOTE_STATUS = {
  INTENTION: '已报名',
  LOCKED: '已锁定报价',
  WIN: '中标',
  LOSE: '未中标',
  INVALID: '已取消',
}

export const INTENTION_STATUS = {
  NONE: '未冻结',
  PENDING_PAY: '待支付意向金',
  FROZEN: '已冻结',
  RELEASED: '已退还',
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
  PAYMENT: '放款',
  REFUND: '退款',
  PENALTY: '罚没',
  COMMISSION: '佣金',
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
  PENDING: '待启动',
  IN_PRODUCTION: '生产中',
  PENDING_INSPECTION: '待质检',
  INSPECTING: '质检中',
  PASS: '合格',
  FAIL: '不合格',
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

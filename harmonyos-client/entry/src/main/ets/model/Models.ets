/**
 * 与后端接口对应的数据模型（字段与 Java 实体/DTO 对齐，均按可选声明以兼容后端 null）。
 * 金额/时间为展示友好类型：金额 number、时间 string(yyyy-MM-dd HH:mm:ss)。
 */

/** 后端统一响应包裹 R<T> */
export interface ApiResult<T> {
  code: number;
  message: string;
  data?: T;
}

/** POST /api/auth/login 请求 */
export interface LoginReq {
  phone: string;
  password: string;
}

/** 登录响应 data */
export interface LoginData {
  token: string;
  role: string;
  tenantId: number;
  name?: string;
}

/** GET /api/order/all 列表项（OrderListView） */
export interface OrderItem {
  id: number;
  demandId?: number;
  title?: string;
  productName?: string;
  factoryNames?: string;
  totalAmount?: number;
  commissionAmount?: number;
  status?: string;
  createdAt?: string;
}

/** 订单详情中按厂承包项（ComboItem） */
export interface ComboItem {
  factoryId?: number;
  factoryName?: string;
  processNo?: number;
  processName?: string;
  quantity?: number;
  price?: number;
  days?: number;
}

/** GET /api/order/{id} 订单详情（OrderDetailView） */
export interface OrderDetail {
  id: number;
  demandId?: number;
  title?: string;
  productName?: string;
  quantity?: number;
  deadlineHard?: string;
  status?: string;
  totalAmount?: number;
  commissionAmount?: number;
  createdAt?: string;
  combo?: ComboItem[];
  flowSteps?: string[];
  flowActive?: number;
}

/** GET /api/order/{orderId}/contracts 合同 */
export interface Contract {
  id?: number;
  orderId?: number;
  tenantId?: number;
  version?: number;
  status?: string;
  attachmentId?: number;
  signedAt?: string;
  createdAt?: string;
  factoryName?: string;
  processNames?: string;
  fileName?: string;
}

/** GET /api/order/{orderId}/stages 工单/阶段（WorkStage） */
export interface WorkStage {
  id?: number;
  orderId?: number;
  tenantId?: number;
  processNo?: number;
  processName?: string;
  quantity?: number;
  promisedDays?: number;
  promisedDate?: string;
  amount?: number;
  escrowStatus?: string;
  actualProgress?: number;
  status?: string;
  contractSigned?: boolean;
  surveyed?: boolean;
  factoryName?: string;
  createdAt?: string;
}

/** GET /api/demand/for-factory 需求（Demand 实体） */
export interface Demand {
  id: number;
  tenantId?: number;
  title?: string;
  productName?: string;
  category?: string;
  quantity?: number;
  material?: string;
  tolerance?: string;
  surfaceTreatment?: string;
  aql?: string;
  certification?: string;
  minYield?: number;
  minCreditScore?: number;
  deadlineHard?: string;
  deadlineFlexible?: string;
  deliveryAddress?: string;
  packaging?: string;
  multiProcess?: number;
  intentionDays?: number;
  remark?: string;
  inspectMode?: string;
  generalTolerance?: string;
  partRevision?: string;
  deliveryTimes?: number;
  deliveryPlanJson?: string;
  estimatedTotal?: number;
  status?: string;
  applied?: boolean;
  myQuoteStatus?: string;
  intentionEndAt?: string;
  factoryThinkingEndAt?: string;
  buyerThinkingEndAt?: string;
  publishedAt?: string;
  createdAt?: string;
}

/** GET /api/demand/{id}/processes 工序（Process） */
export interface ProcessItem {
  id?: number;
  demandId?: number;
  processNo?: number;
  processName?: string;
  quantity?: number;
  requirement?: string;
}

/** 附件 */
export interface Attachment {
  id?: number;
  fileName?: string;
  filePath?: string;
}

/** GET /api/demand/{id} 需求详情（DemandDetailView） */
export interface DemandDetail {
  demand?: Demand;
  processes?: ProcessItem[];
  attachments?: Attachment[];
}

/** 报名/报价（Quotation） */
export interface Quotation {
  id: number;
  tenantId?: number;
  demandId?: number;
  processNo?: number;
  unitPrice?: number;
  price?: number;
  yieldRate?: number;
  promisedDays?: number;
  minQty?: number;
  maxQty?: number;
  planText?: string;
  deliveryPlanJson?: string;
  intentionStatus?: string;
  depositStatus?: string;
  status?: string;
  demandTitle?: string;
  createdAt?: string;
  updatedAt?: string;
}

/** POST /api/bidding/intention 请求（多工序报名） */
export interface IntentionItemReq {
  processNo: number;
  minQty: number;
  maxQty: number;
}

export interface IntentionReq {
  demandId: number;
  items: IntentionItemReq[];
}

/** 报名结果 data */
export interface IntentionResult {
  quotationId?: number;
  frozen?: boolean;
  payUrl?: string;
}

/** POST /api/bidding/commit 填报方案请求 */
export interface CommitItemReq {
  processNo: number;
  unitPrice: number;
  yieldRate: number;
  promisedDays: number;
}

export interface CommitReq {
  demandId: number;
  planText: string;
  deliveryPlan: string[];
  items: CommitItemReq[];
}
/** GET /api/common/notifies 站内通知（平台公告/业务消息共用） */
export interface NotifyItem {
  id?: number;
  title?: string;
  content?: string;
  isRead?: number;
  createdAt?: string;
  link?: string;
  action?: string;
}

/** GET /api/common/todos 业务待办消息 */
export interface TodoItem {
  type?: string;
  title?: string;
  link?: string;
  count?: number;
}

/** GET /api/device/mine 本厂设备 */
export interface DeviceItem {
  id?: number;
  tenantId?: number;
  name?: string;
  model?: string;
  precisionText?: string;
  parts?: string;
  materials?: string;
  processNames?: string;
  dailyCapacity?: number;
  status?: string;
  createdAt?: string;
}

/** GET /api/enterprise/mine 企业信息/账户（买家与工厂共用返回结构） */
export interface CompanyProfile {
  id?: number;
  type?: string;
  name?: string;
  creditCode?: string;
  creditScore?: number;
  authStatus?: string;
  contactName?: string;
  address?: string;
  balance?: number;
  frozen?: number;
  introduction?: string;
}
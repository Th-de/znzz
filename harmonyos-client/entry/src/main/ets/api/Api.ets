import { HttpUtil } from '../common/HttpUtil';
import {
  CompanyProfile, Contract, Demand, DeviceItem, LoginData, LoginReq,
  NotifyItem, OrderDetail, OrderItem, TodoItem, WorkStage
} from '../model/Models';

/**
 * 真实后端接口封装（纯信息查阅，只读）。
 * 路径前缀 /api 与后端 @RequestMapping 对应，鉴权依赖 HttpUtil 自动附带 Bearer token。
 */
export class Api {
  /** POST /api/auth/login 登录 */
  static login(phone: string, password: string): Promise<LoginData> {
    const req: LoginReq = { phone: phone, password: password };
    return HttpUtil.postData<LoginData>('/api/auth/login', req);
  }

  /** GET /api/common/notifies 站内通知（本租户，公告/业务消息同源） */
  static notifyList(): Promise<NotifyItem[]> {
    return HttpUtil.get<NotifyItem[]>('/api/common/notifies');
  }

  /** GET /api/common/todos 业务待办消息 */
  static todoList(): Promise<TodoItem[]> {
    return HttpUtil.get<TodoItem[]>('/api/common/todos');
  }

  /** GET /api/enterprise/mine 企业信息 + 账户资金（买家/工厂通用） */
  static companyProfile(): Promise<CompanyProfile> {
    return HttpUtil.get<CompanyProfile>('/api/enterprise/mine');
  }

  /** GET /api/demand/mine 我发布的需求（买家） */
  static demandsMine(): Promise<Demand[]> {
    return HttpUtil.get<Demand[]>('/api/demand/mine');
  }

  /** GET /api/order/all 我的订单（买家=本人发布；工厂=本厂承接，后端已按租户过滤） */
  static listOrders(): Promise<OrderItem[]> {
    return HttpUtil.get<OrderItem[]>('/api/order/all');
  }

  /** GET /api/order/{id} 订单详情（含实施流程 flowSteps/flowActive） */
  static orderDetail(id: number): Promise<OrderDetail> {
    return HttpUtil.get<OrderDetail>('/api/order/' + id);
  }

  /** GET /api/order/{orderId}/contracts 订单合同列表（买家查看全部厂合同） */
  static orderContracts(orderId: number): Promise<Contract[]> {
    return HttpUtil.get<Contract[]>('/api/order/' + orderId + '/contracts');
  }

  /** GET /api/order/{orderId}/contract 本厂合同（工厂只看自己的那一份） */
  static orderContractMine(orderId: number): Promise<Contract> {
    return HttpUtil.get<Contract>('/api/order/' + orderId + '/contract');
  }

  /** GET /api/order/{orderId}/stages 工单与阶段款 */
  static orderStages(orderId: number): Promise<WorkStage[]> {
    return HttpUtil.get<WorkStage[]>('/api/order/' + orderId + '/stages');
  }

  /** GET /api/device/mine 本厂设备列表（仅工厂角色可用） */
  static deviceList(): Promise<DeviceItem[]> {
    return HttpUtil.get<DeviceItem[]>('/api/device/mine');
  }
}